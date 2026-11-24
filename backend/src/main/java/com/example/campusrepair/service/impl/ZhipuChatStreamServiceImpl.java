package com.example.campusrepair.service.impl;

import com.example.campusrepair.service.AbortException;
import com.example.campusrepair.service.ZhipuChatStreamService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 智谱流式对话实现（OpenAI 兼容 /chat/completions，Java 原生 HttpClient 读 SSE）
 *
 * @Description stream=true 逐行读 "data: {...}"，从 choices[0].delta.content 取增量。
 *              读每一行前与回调前都检查 aborted：命中即抛 AbortException，
 *              try-with-resources 关闭 InputStream 从而断开上游 SSE、立即停止扣费。
 *              仅对限流（HTTP 429 / code 1305）做 2 次退避重试；其它失败直接向上抛，
 *              由引擎降级为知识库原文。已吐过 token 的流不重试，避免重复内容。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.1
 */
@Slf4j
@Service
public class ZhipuChatStreamServiceImpl implements ZhipuChatStreamService {

    /** 限流最多重试 2 次（共 3 次请求） */
    private static final int MAX_RETRIES = 2;
    /** 退避间隔：1s、2s */
    private static final long[] RETRY_DELAYS_MS = {1000L, 2000L};

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ZhipuChatStreamServiceImpl(
            @Value("${zhipu.api-key:}") String apiKey,
            @Value("${zhipu.chat.model:glm-4-flashx-250414}") String model,
            @Value("${zhipu.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public void streamChat(String systemPrompt, String userPrompt,
                           Consumer<String> onToken, BooleanSupplier aborted) throws Exception {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("智谱 API key 未配置");
        }
        // glm-4-flashx-250414 无思考模式，不传 thinking 参数；
        // 解析侧仍兼容 reasoning_content，方便日后切回带思考的模型。
        String body = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt))));

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            // 本轮是否已向外吐过 token：吐过就不再重试，避免重复内容
            boolean[] emitted = {false};
            Consumer<String> guarded = token -> {
                emitted[0] = true;
                onToken.accept(token);
            };
            try {
                streamOnce(request, guarded, aborted);
                return;
            } catch (AbortException e) {
                throw e; // 用户主动中止，绝不重试
            } catch (RateLimitException e) {
                if (emitted[0] || attempt == MAX_RETRIES) {
                    throw e;
                }
                long delay = RETRY_DELAYS_MS[attempt];
                log.warn("智谱对话限流，{}ms 后重试（{}/{}）：{}", delay, attempt + 1, MAX_RETRIES, e.getMessage());
                backoffAndCheckAbort(delay, aborted);
            }
        }
    }

    /** 单次 SSE 请求：非 200 时区分限流与其它错误；读流途中检查 abort */
    private void streamOnce(HttpRequest request, Consumer<String> onToken,
                            BooleanSupplier aborted) throws Exception {
        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String err;
            try (InputStream errStream = response.body()) {
                err = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            String snippet = err.length() > 500 ? err.substring(0, 500) : err;
            if (response.statusCode() == 429 || err.contains("1305")) {
                throw new RateLimitException("智谱对话 HTTP " + response.statusCode() + "：" + snippet);
            }
            throw new IllegalStateException("智谱对话 HTTP " + response.statusCode() + "：" + snippet);
        }

        try (InputStream input = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 上游还有更多 token 要吐之前先检查，越早断越省费用
                if (aborted.getAsBoolean()) {
                    throw new AbortException();
                }
                if (!line.startsWith("data:")) {
                    continue; // 忽略 SSE 注释行（: ping）与空行
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode deltaNode = root.path("choices").path(0).path("delta");
                    String delta = deltaNode.path("content").asText("");
                    if (delta.isEmpty()) {
                        // 兼容带思考模式的模型；普通模型该字段为空
                        delta = deltaNode.path("reasoning_content").asText("");
                    }
                    if (!delta.isEmpty()) {
                        if (aborted.getAsBoolean()) {
                            throw new AbortException();
                        }
                        onToken.accept(delta);
                    }
                } catch (java.io.IOException e) {
                    // 单行格式异常（心跳/错误帧）跳过，不让整条流挂掉
                    log.warn("智谱流式响应跳过异常帧: {}", payload.substring(0, Math.min(80, payload.length())));
                }
            }
        }
    }

    /** 分段睡眠，期间持续检查中止标记：用户点停止后立即放弃重试 */
    private void backoffAndCheckAbort(long millis, BooleanSupplier aborted) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (aborted.getAsBoolean()) {
                throw new AbortException();
            }
            Thread.sleep(Math.min(100L, deadline - System.currentTimeMillis()));
        }
    }

    /** 仅限流可重试；其它异常直接降级 */
    private static class RateLimitException extends Exception {
        RateLimitException(String message) {
            super(message);
        }
    }
}
