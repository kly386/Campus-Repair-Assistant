package com.example.campusrepair.repository;

import com.example.campusrepair.entity.KnowledgeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 知识库历史数据访问
 *
 * @Description 历史快照只增不改，仅提供按知识条目倒序查询与按 ID 精确查找
 *              （回滚时取快照）。不提供删除/更新方法——历史必须可追溯。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface KnowledgeHistoryRepository extends JpaRepository<KnowledgeHistory, Long> {

    /** 某知识条目的版本链，最早快照在前 */
    List<KnowledgeHistory> findByKnowledgeIdOrderByIdAsc(Long knowledgeId);
}
