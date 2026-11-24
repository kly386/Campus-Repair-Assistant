package com.example.campusrepair.service;

import java.util.List;
import java.util.Optional;

/**
 * 文本向量化服务（硅基流动 SiliconFlow，OpenAI 兼容 /embeddings）
 *
 * @Description 把文本转换为语义向量，供 RAG 检索使用。
 *              SILICONFLOW_API_KEY 未配置或调用失败时返回 empty，
 *              调用方据此自动降级为 LIKE 关键词检索——接口永不让启动失败。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.1
 */
public interface EmbeddingService {

    /**
     * 文本转向量。
     *
     * @return 向量；key 未配置 / 网络失败 / 超限时返回 empty（调用方走降级）
     */
    Optional<float[]> embed(String text);

    /**
     * 批量向量化，供启动时全量载入知识库向量缓存。
     *
     * @return 与输入等长的列表，第 i 项对应 texts[i]；单条失败该项为 empty
     */
    List<Optional<float[]>> embedBatch(List<String> texts);
}
