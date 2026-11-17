package com.example.campusrepair.repository;

import com.example.campusrepair.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 知识库数据访问
 *
 * @Description 学生端浏览/检索（仅 APPROVED）、管理员端列表（全状态）、
 *              helpful 原子累加。检索用 LIKE 关键词匹配——这是阶段二
 *              向量化检索的降级保底方案，接口签名保持一致以便无缝切换。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    /** 学生端按类别浏览（categoryId 为空 = 全部），有帮助数排序 */
    @Query("""
            SELECT k FROM KnowledgeBase k
            WHERE k.isDeleted = false AND k.reviewStatus = 'APPROVED'
              AND (:categoryId IS NULL OR k.categoryId = :categoryId)
            ORDER BY k.helpful DESC, k.id DESC
            """)
    List<KnowledgeBase> browseApproved(@Param("categoryId") Long categoryId);

    /** 学生端关键词检索：问题/关键词/答案/标题 LIKE 匹配 */
    @Query("""
            SELECT k FROM KnowledgeBase k
            WHERE k.isDeleted = false AND k.reviewStatus = 'APPROVED'
              AND (k.question LIKE CONCAT('%', :q, '%')
                OR k.keyword  LIKE CONCAT('%', :q, '%')
                OR k.answer   LIKE CONCAT('%', :q, '%')
                OR k.title    LIKE CONCAT('%', :q, '%'))
            ORDER BY k.helpful DESC, k.id DESC
            """)
    List<KnowledgeBase> searchApproved(@Param("q") String q);

    /** 管理员端列表：可按审核状态/类别过滤，最近改动优先 */
    @Query("""
            SELECT k FROM KnowledgeBase k
            WHERE k.isDeleted = false
              AND (:status IS NULL OR k.reviewStatus = :status)
              AND (:categoryId IS NULL OR k.categoryId = :categoryId)
            ORDER BY k.gmtModified DESC, k.id DESC
            """)
    List<KnowledgeBase> adminList(@Param("status") String status,
                                  @Param("categoryId") Long categoryId);

    /** 按主键取未删除的条目（服务层判空后抛业务异常） */
    Optional<KnowledgeBase> findByIdAndIsDeletedFalse(Long id);

    /**
     * 有帮助数原子累加：数据库端 +1，规避"读-改-写"并发丢计数。
     * 仅对可见（APPROVED 且未删除）条目生效；返回受影响行数，
     * 0 = 条目不存在/不可见，由服务层转换为业务异常。
     */
    @Modifying
    @Query("""
            UPDATE KnowledgeBase k SET k.helpful = k.helpful + 1
            WHERE k.id = :id AND k.isDeleted = false AND k.reviewStatus = 'APPROVED'
            """)
    int incrementHelpful(@Param("id") Long id);
}
