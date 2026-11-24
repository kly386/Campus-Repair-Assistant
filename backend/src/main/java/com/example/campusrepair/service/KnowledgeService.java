package com.example.campusrepair.service;

import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.dto.KnowledgeSaveRequest;
import com.example.campusrepair.entity.KnowledgeHistory;
import com.example.campusrepair.entity.KnowledgeBase;

import java.util.List;

/**
 * 知识库业务接口（knowledge_base 为唯一数据源）
 *
 * @Description 管理员端 CRUD/审核/回滚 + 学生端浏览/搜索/helpful + 自助诊断问答落库。
 *              向量检索由 KnowledgeVectorCache 负责，本接口只负责编排与落库。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.1
 */
public interface KnowledgeService {

    /* ---------- 管理员端（权限码 knowledge:manage） ---------- */

    KnowledgeBase create(KnowledgeSaveRequest request);

    KnowledgeBase update(Long id, KnowledgeSaveRequest request);

    KnowledgeBase review(Long id, String reviewStatus);

    KnowledgeBase rollback(Long historyId);

    void delete(Long id);

    List<KnowledgeBase> adminList(String reviewStatus, Long categoryId);

    List<KnowledgeHistory> history(Long knowledgeId);

    /* ---------- 学生端（权限码 menu:diagnosis） ---------- */

    List<KnowledgeBase> browse(Long categoryId);

    List<KnowledgeBase> search(String keyword);

    long helpful(Long id);

    /* ---------- 自助诊断（RAG 问答） ---------- */

    /** 学生提问：内存向量 Top3（异常降级 LIKE）→ 落 diagnosis_session */
    DiagnosisResult ask(Long userId, String question, String conversationId);

    /** 学生反馈"是否解决"：is_solved 落库；解决时给命中条目 helpful +1 */
    void markSolved(Long sessionId, Long userId, boolean solved);
}
