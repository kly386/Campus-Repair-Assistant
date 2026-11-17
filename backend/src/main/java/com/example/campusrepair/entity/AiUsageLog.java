package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用用量统计实体
 *
 * @Description 每一次模型调用（含 mock）落一行，用于答辩展示"密钥用量"：
 *              谁（caller）用了多少 token、耗时多少、失败率如何。
 *              对应 ai_usage_log 表（docs/sql/ai-usage.sql）。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "ai_usage_log")
@Data
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 调用方标识：repair_parse=报修解析 / rag_chat=RAG问答 等 */
    @Column(name = "caller", nullable = false, length = 50)
    private String caller;

    /** 模型名 */
    @Column(name = "model", nullable = false, length = 64)
    private String model;

    /** 是否本地模拟调用 */
    @Column(name = "is_mock", columnDefinition = "TINYINT UNSIGNED")
    private Boolean isMock = false;

    /** 调用是否成功 */
    @Column(name = "success", columnDefinition = "TINYINT UNSIGNED")
    private Boolean success = true;

    /** 提示词 tokens */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    /** 补全 tokens */
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /** 耗时毫秒 */
    @Column(name = "cost_ms")
    private Integer costMs;

    /** 失败原因（成功为空） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;
}
