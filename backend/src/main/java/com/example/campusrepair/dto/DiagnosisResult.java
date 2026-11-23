package com.example.campusrepair.dto;

import lombok.Data;

import java.util.List;

/**
 * 自助诊断问答结果
 *
 * @Description 一次 RAG 提问的返回：命中的知识条目（按相似度/相关性排序）、
 *              组织好的答案文本、会话 ID，以及报修接力需要的类别与紧急等级。
 *              similarity 为空表示本次走的是 LIKE 降级检索。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.2
 */
@Data
public class DiagnosisResult {

    /** 本次问答会话 ID（反馈"是否解决"时回传） */
    private Long sessionId;

    /** 多轮对话 ID（前端生成，同一会话的多轮提问共用一个） */
    private String conversationId;

    /** 答案文本：向量模式为 Top-1 条目答案；LLM 生成模式为模型组织文本（扩展点） */
    private String answer;

    /** 使用的检索方式：VECTOR=向量语义检索 / LIKE=关键词降级 */
    private String searchMode;

    /** Top-1 命中条目的故障类别 ID，报修接力时带给 Repair.vue */
    private Long categoryId;

    /** 诊断结论紧急等级：HIGH / NORMAL，由关键词规则推导，不让模型生成 */
    private String emergencyLevel;

    /** 命中的知识条目（最多 3 条，按相关性排序） */
    private List<Match> matches;

    @Data
    public static class Match {
        private Long id;
        private String title;
        private String question;
        private String answer;
        private Long helpful;
        /** 命中条目的故障类别 ID */
        private Long categoryId;
        /** 相似度 0~1（LIKE 降级时为 null） */
        private Double similarity;
    }
}
