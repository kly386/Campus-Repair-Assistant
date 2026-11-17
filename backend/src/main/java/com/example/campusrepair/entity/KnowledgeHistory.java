package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库历史快照实体
 *
 * @Description 每次编辑/回滚知识条目前，把当时的标题、问题、答案、审核状态
 *              整行拷贝进本表，形成版本链；回滚 = 取历史快照写回主表。
 *              注意：只存内容快照，不建与 KnowledgeBase 的 JPA 关联映射，
 *              避免 cascade 误删历史（历史必须只增不改）。
 *              对应 knowledge_history 表。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "knowledge_history")
@Data
public class KnowledgeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联知识 ID（逻辑关联 knowledge_base.id，不用外键/级联） */
    @Column(name = "knowledge_id", nullable = false)
    private Long knowledgeId;

    /** 编辑前的标题 */
    @Column(name = "title", length = 100)
    private String title;

    /** 编辑前的问题（故障现象） */
    @Column(name = "question", nullable = false, length = 255)
    private String question;

    /** 编辑前的答案（自助解决方案） */
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** 编辑前的审核状态 */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    /** 操作人 ID（逻辑关联 user.id，取自 AuthContext 当前登录人） */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;
}
