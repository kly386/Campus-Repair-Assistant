package com.example.campusrepair.service;

import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.dto.KnowledgeSaveRequest;
import com.example.campusrepair.entity.KnowledgeBase;
import com.example.campusrepair.entity.KnowledgeHistory;

import java.util.List;

/**
 * 知识库业务接口（阶段一：CRUD + 审核闭环 + 历史回滚）
 *
 * @Description 后续阶段在本接口上扩展：阶段二追加 RAG 检索问答
 *              （向量化 Top-K + LIKE 降级 + diagnosis_session 落库），
 *              阶段三追加工单知识回流（ai_confidence 置信度治理）。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface KnowledgeService {

    /* ---------- 管理员端（权限码 knowledge:manage） ---------- */

    /** 新增知识条目：初始 PENDING 待审核，来源固定 MANUAL */
    KnowledgeBase create(KnowledgeSaveRequest request);

    /** 编辑知识条目：先把旧内容快照进 history（版本链），再更新主表 */
    KnowledgeBase update(Long id, KnowledgeSaveRequest request);

    /** 审核状态流转：PENDING/APPROVED/REJECTED/OFFLINE 四态 */
    KnowledgeBase review(Long id, String reviewStatus);

    /** 回滚：把指定历史快照写回主表，回滚前的现状先再次快照（版本链不断） */
    KnowledgeBase rollback(Long historyId);

    /** 软删除：只置 is_deleted=1，历史与数据保留 */
    void delete(Long id);

    /** 管理员列表：可按审核状态/故障类别过滤 */
    List<KnowledgeBase> adminList(String reviewStatus, Long categoryId);

    /** 查看某条知识的版本历史 */
    List<KnowledgeHistory> history(Long knowledgeId);

    /* ---------- 学生端（权限码 menu:diagnosis） ---------- */

    /** 按类别浏览已上架知识（categoryId 为空 = 全部），走从库 */
    List<KnowledgeBase> browse(Long categoryId);

    /** 关键词检索（阶段一 LIKE 保底实现；阶段二升级为向量检索同签名），走从库 */
    List<KnowledgeBase> search(String keyword);

    /** "有帮助"计数原子 +1，返回累加后的最新值 */
    long helpful(Long id);

    /* ---------- 自助诊断（RAG 问答） ---------- */

    /** 学生提问：向量检索 Top-K（降级 LIKE）→ 组织答案 → 落 diagnosis_session */
    DiagnosisResult ask(Long userId, String question);

    /** 学生反馈"是否解决"：is_solved 落库；解决时给命中条目 helpful +1 */
    void markSolved(Long sessionId, Long userId, boolean solved);
}
