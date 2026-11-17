package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.AiProperties;
import com.example.campusrepair.entity.AiUsageLog;
import com.example.campusrepair.repository.AiUsageLogRepository;
import com.example.campusrepair.service.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OpenAI 兼容协议 AI 客户端实现
 *
 * @Description 真实模式 POST {base-url}/chat/completions（多模态：文字 + 图片 data URI）；
 *              mock 模式返回确定性本地结果（按关键词粗分类），联调/演示零成本不外呼。
 *              无论 mock/真实/失败，均写一行 ai_usage_log 供"密钥用量"统计。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
@Slf4j
@Service
public class OpenAiCompatibleAiClient implements AiClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AiUsageLogRepository usageLogRepository;

    public OpenAiCompatibleAiClient(AiProperties properties,
                                    ObjectMapper objectMapper,
                                    RestTemplateBuilder restTemplateBuilder,
                                    AiUsageLogRepository usageLogRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.usageLogRepository = usageLogRepository;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (!properties.realReady()) {
            return mockChat(request);
        }
        long start = System.currentTimeMillis();
        try {
            ChatResponse resp = realChat(request, start);
            logUsage(request.caller(), properties.getModel(), true, resp.promptTokens(),
                    resp.completionTokens(), resp.costMillis(), null, false);
            return resp;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("AI 调用失败 caller={} model={}", request.caller(), properties.getModel(), e);
            logUsage(request.caller(), properties.getModel(), false, null, null, cost,
                    shorten(e.getMessage()), false);
            throw new IllegalStateException("AI 调用失败: " + shorten(e.getMessage()), e);
        }
    }

    /** 真实调用：OpenAI 兼容 chat/completions，多模态内容以 parts 数组下发 */
    private ChatResponse realChat(ChatRequest request, long start) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", 0.2);
        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", request.systemPrompt());
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode parts = user.putArray("content");
        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", request.userText());
        if (request.imageDataUris() != null) {
            for (String dataUri : request.imageDataUris()) {
                ObjectNode imagePart = parts.addObject();
                imagePart.put("type", "image_url");
                imagePart.putObject("image_url").put("url", dataUri);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        String raw = restTemplate.postForObject(
                properties.getBaseUrl() + "/chat/completions", entity, String.class);
        JsonNode json = objectMapper.readTree(raw);
        String content = json.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("模型返回为空: " + shorten(raw));
        }
        JsonNode usage = json.path("usage");
        return new ChatResponse(content,
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                System.currentTimeMillis() - start,
                false);
    }

    /**
     * 本地模拟：不外呼。按关键词粗分类，楼栋/房间返回 null——
     * 由上层"正则优先、LLM 兜底"策略用本地正则补齐，保证全链路可演示。
     */
    private ChatResponse mockChat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String text = request.userText() == null ? "" : request.userText();
        // 用户提示词形如 "故障描述：xxx\n候选类别：..."，取出纯描述部分
        String desc = text;
        int descStart = text.indexOf("故障描述：");
        int descEnd = text.indexOf("\n候选类别：");
        if (descStart >= 0) {
            desc = descEnd > descStart ? text.substring(descStart + 5, descEnd) : text.substring(descStart + 5);
        }
        String category;
        if (text.contains("漏水") || text.contains("水龙头") || text.contains("马桶")
                || text.contains("热水器") || text.contains("空调") || text.contains("插座")
                || text.contains("灯") || text.contains("电")) {
            category = "水电维修";
        } else if (text.contains("网") || text.contains("wifi") || text.contains("WiFi")
                || text.contains("路由") || text.contains("断网")) {
            category = "网络故障";
        } else if (text.contains("门") || text.contains("窗") || text.contains("桌")
                || text.contains("床") || text.contains("柜") || text.contains("椅")) {
            category = "门窗家具";
        } else {
            category = "报修咨询";
        }
        // 紧急度模拟：影响生活的关键词 → HIGH，其余 MEDIUM
        String emergency = "MEDIUM";
        if (text.contains("漏水") || text.contains("停电") || text.contains("断电")
                || text.contains("断网") || text.contains("不出水") || text.contains("不出热水")
                || text.contains("必须") || text.contains("马上") || text.contains("立即")) {
            emergency = "HIGH";
        }
        // JSON 转义后拼接，标题截 20 字
        String title = desc.length() > 20 ? desc.substring(0, 20) : desc;
        title = title.replace("\\", "\\\\").replace("\"", "\\\"");
        String content = "{\"title\":\"" + title + "(模拟解析)\",\"category\":\"" + category
                + "\",\"emergency_level\":\"" + emergency + "\",\"building\":null,\"room\":null,\"confidence\":0.42}";
        ChatResponse resp = new ChatResponse(content, 0, 0,
                System.currentTimeMillis() - start, true);
        logUsage(request.caller(), properties.getModel() + "(mock)", true, 0, 0,
                resp.costMillis(), null, true);
        return resp;
    }

    /** 用量落库：失败不影响主流程（捕获后仅记日志） */
    private void logUsage(String caller, String model, boolean success,
                          Integer promptTokens, Integer completionTokens,
                          long costMillis, String error, boolean mock) {
        try {
            AiUsageLog row = new AiUsageLog();
            row.setCaller(caller == null ? "unknown" : caller);
            row.setModel(model);
            row.setIsMock(mock);
            row.setSuccess(success);
            row.setPromptTokens(promptTokens);
            row.setCompletionTokens(completionTokens);
            row.setCostMs((int) Math.min(costMillis, Integer.MAX_VALUE));
            row.setErrorMessage(error);
            row.setGmtCreate(LocalDateTime.now());
            usageLogRepository.save(row);
        } catch (Exception e) {
            log.warn("AI 用量落库失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String shorten(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    /** 图片数量上限透传（供上层裁剪） */
    public List<String> trimImages(List<String> imageDataUris) {
        int max = properties.getMaxImages();
        if (imageDataUris == null || imageDataUris.size() <= max) {
            return imageDataUris;
        }
        return imageDataUris.subList(0, max);
    }
}
