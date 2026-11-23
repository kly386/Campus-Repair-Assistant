package com.example.campusrepair.service.impl;

import com.example.campusrepair.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DashScope 文本向量实现（OpenAI 兼容端点 /embeddings）
 *
 * @Description 调用阿里云百炼 text-embedding-v4。任何失败（未配 key、
 *              网络、限流）都只记 WARN 并返回 empty，由上层降级为 LIKE
 *              检索——与 MinIO 优雅降级同一设计哲学：外部依赖坏了不让主流程挂。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.0
 */
@Slf4j
@Service
public class DashScopeEmbeddingService implements EmbeddingService {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeEmbeddingService(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.embedding.model:text-embedding-v4}") String model,
            @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.create();
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (apiKey.isEmpty() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            String body = restClient.post()
                    .uri(baseUrl + "/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", text))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                log.warn("DashScope 向量返回格式异常：{}", body);
                return Optional.empty();
            }
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            return Optional.of(vector);
        } catch (Exception e) {
            log.warn("DashScope 向量化失败，本次降级为关键词检索：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 批量入库用（后续扩展），当前单条即可 */
    @SuppressWarnings("unused")
    private Optional<List<float[]>> embedBatch(List<String> texts) {
        return Optional.empty();
    }
}
