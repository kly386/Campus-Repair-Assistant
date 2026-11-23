package com.example.campusrepair.service.impl;

import com.example.campusrepair.service.DashScopeChatStreamService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
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
import java.util.function.Consumer;

/**
 * DashScope 流式对话实现（OpenAI 兼容端点，Java 原生 HttpClient 读 SSE）
 *
 * @Description 发送 stream=true 的 chat/completions 请求，逐行读取
 *              "data: {...}" 事件，从 choices[0].delta.content 取出增量
 *              文本回调给上层。任何失败都向上抛，由上层降级为 Top-1 条目
 *              原文——外部依赖坏了不让问答主流程挂（与 Embedding 同款设计）。
 * @Author 闵娟
 * @Date 2026-11-21
 * @Version 1.0
 */
@Slf4j
@Service
public class DashScopeChatStreamServiceImpl implements DashScopeChatStreamService {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public DashScopeChatStreamServiceImpl(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.chat.model:qwen-plus}") String model,
            @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public void streamChat(String systemPrompt, String userPrompt, Consumer<String> onToken) throws Exception {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("DashScope API key 未配置");
        }
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

        // ofInputStream 在响应头到达时即返回，后续 body 边到边读——SSE 的关键
        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("DashScope 对话 HTTP " + response.statusCode() + "：" + err);
        }

        try (InputStream input = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue; // 忽略 SSE 注释行（: ping）与空行
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(payload);
                    String delta = root.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        onToken.accept(delta);
                    }
                } catch (IOException e) {
                    // 单行格式异常（如心跳/错误帧）跳过，不让整条流挂掉
                    log.warn("DashScope 流式响应跳过异常帧: {}", payload.substring(0, Math.min(80, payload.length())));
                }
            }
        }
    }
}
