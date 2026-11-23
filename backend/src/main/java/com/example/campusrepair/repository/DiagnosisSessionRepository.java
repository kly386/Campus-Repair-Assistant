package com.example.campusrepair.repository;

import com.example.campusrepair.entity.DiagnosisSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 自助诊断会话数据访问
 *
 * @Description 基础 CRUD 之外追加统计查询：热门问题（按 question 分组计数）、
 *              自助解决率、未命中数——把采集的数据真正消费起来。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface DiagnosisSessionRepository extends JpaRepository<DiagnosisSession, Long> {

    /** 已自助解决数（用于解决率分子） */
    long countByIsSolvedTrue();

    /** 已反馈数（is_solved 非空，用于解决率分母；未反馈不算"没解决"） */
    long countByIsSolvedIsNotNull();

    /** 同一对话、同一用户最近 3 轮 Q/A，按时间倒序；多轮上下文注入用 */
    List<DiagnosisSession> findTop3ByConversationIdAndUserIdOrderByGmtCreateDesc(
            String conversationId, Long userId);

    /** 未命中知识条目数（matched_kb_id 为空，用于转人工占比） */
    long countByMatchedKbIdIsNull();

    /** 热门问题 Top-N：按问题文本分组计数降序 */
    @Query("select s.question, count(s) from DiagnosisSession s group by s.question order by count(s) desc")
    List<Object[]> findHotQuestions(Pageable pageable);
}
