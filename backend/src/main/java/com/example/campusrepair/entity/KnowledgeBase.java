package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 维修知识库实体
 *
 * @Description RAG 自助诊断的知识来源。管理员手动录入（MANUAL）或从已完成
 *              工单沉淀（ORDER，阶段三接入）；审核通过（APPROVED）后对学生可见。
 *              embedding 向量列（BLOB）阶段二接入向量化检索时再映射，本实体暂不处理。
 *              对应 knowledge_base 表。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "knowledge_base")
@Data
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 标题（可空，检索与展示的补充信息） */
    @Column(name = "title", length = 100)
    private String title;

    /** 关键词（必填，用于检索与同类条目去重） */
    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    /** 问题（必填，故障现象描述） */
    @Column(name = "question", nullable = false, length = 255)
    private String question;

    /** 答案（必填，自助解决方案） */
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    /** 故障类别 ID（逻辑关联 repair_category.id） */
    @Column(name = "category_id")
    private Long categoryId;

    /** 来源：MANUAL=手册录入 / ORDER=工单沉淀 */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /** 来源工单 ID（source=ORDER 时记录，逻辑关联 repair_order.id） */
    @Column(name = "source_order_id")
    private Long sourceOrderId;

    /** 被评价有帮助次数（原子累加，见 Repository#incrementHelpful） */
    @Column(name = "helpful", nullable = false)
    private Integer helpful;

    /** 审核状态：PENDING/APPROVED/REJECTED/OFFLINE，学生端仅可见 APPROVED */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    /** 向量化结果（DashScope text-embedding-v4，float[] 序列化为字节；未向量化为空） */
    @Column(name = "embedding", columnDefinition = "BLOB")
    private byte[] embedding;

    /** 软删标记：0=正常 / 1=已删除（物理不删，历史可追溯） */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;
}
