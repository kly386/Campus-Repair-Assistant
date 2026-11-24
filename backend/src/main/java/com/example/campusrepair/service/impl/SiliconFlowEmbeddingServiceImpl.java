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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 硅基流动 Embedding 实现（OpenAI 兼容 /embeddings，BAAI/bge-m3，1024 维，免费）
 *
 * @Description key 未配置或任何调用失败都返回 empty，由上层降级为 MySQL keywords LIKE，
 *              外部依赖坏了不让主流程挂。bge-m3 不支持 dimensions 参数，请求体不带该字段。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.0
 */
@Slf4j
@Service
public class SiliconFlowEmbeddingServiceImpl implements EmbeddingService {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SiliconFlowEmbeddingServiceImpl(
            @Value("${siliconflow.api-key:}") String apiKey,
            @Value("${siliconflow.embedding.model:BAAI/bge-m3}") String model,
            @Value("${siliconflow.base-url:https://api.siliconflow.cn/v1}") String baseUrl) {
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
            JsonNode data = call(List.of(text));
            return parse(data.path(0));
        } catch (Exception e) {
            log.warn("硅基流动向量化失败，本次降级为关键词检索：{}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Optional<float[]>> embedBatch(List<String> texts) {
        if (apiKey.isEmpty() || texts == null || texts.isEmpty()) {
            return texts == null ? List.of() : texts.stream().map(t -> Optional.<float[]>empty()).toList();
        }
        try {
            JsonNode data = call(texts);
            List<Optional<float[]>> result = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                result.add(i < data.size() ? parse(data.path(i)) : Optional.empty());
            }
            return result;
        } catch (Exception e) {
            log.warn("硅基流动批量向量化失败，本次全部降级为关键词检索：{}", e.getMessage());
            return texts.stream().map(t -> Optional.<float[]>empty()).toList();
        }
    }

    /** 调 /embeddings 并返回 data 数组节点；bge-m3 不支持 dimensions，只传 model + input */
    private JsonNode call(List<String> texts) throws Exception {
        String body = restClient.post()
                .uri(baseUrl + "/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "input", texts))
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("硅基流动向量返回格式异常：" + body);
        }
        return data;
    }

    /** 从 data[i] 里取 embedding 数组转 float[] */
    private Optional<float[]> parse(JsonNode item) {
        JsonNode vectorNode = item.path("embedding");
        if (!vectorNode.isArray() || vectorNode.isEmpty()) {
            return Optional.empty();
        }
        float[] vector = new float[vectorNode.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) vectorNode.get(i).asDouble();
        }
        return Optional.of(vector);
    }
}
