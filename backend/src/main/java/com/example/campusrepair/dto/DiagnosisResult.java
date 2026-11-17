package com.example.campusrepair.dto;

import lombok.Data;

import java.util.List;

/**
 * 自助诊断问答结果
 *
 * @Description 一次 RAG 提问的返回：命中的知识条目（按相似度/相关性排序）、
 *              组织好的答案文本与会话 ID。similarity 为空表示本次走的是
 *              LIKE 降级检索（向量服务不可用或条目未向量化）。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Data
public class DiagnosisResult {

    /** 本次问答会话 ID（反馈"是否解决"时回传） */
    private Long sessionId;

    /** 答案文本：向量模式为 Top-1 条目答案；LLM 生成模式为模型组织文本（扩展点） */
    private String answer;

    /** 使用的检索方式：VECTOR=向量语义检索 / LIKE=关键词降级 */
    private String searchMode;

    /** 命中的知识条目（最多 3 条，按相关性排序） */
    private List<Match> matches;

    @Data
    public static class Match {
        private Long id;
        private String title;
        private String question;
        private String answer;
        private Long helpful;
        /** 相似度 0~1（LIKE 降级时为 null） */
        private Double similarity;
    }
}
