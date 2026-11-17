package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自助诊断会话实体
 *
 * @Description RAG 问答记录：学生每次提问落一行，保存问题、命中的知识条目、
 *              检索相似度与是否自助解决。is_solved 反馈会同时给命中条目的
 *              helpful 计数 +1，形成"问答反哺知识库"的数据闭环。
 *              对应 diagnosis_session 表。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "diagnosis_session")
@Data
public class DiagnosisSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提问学生 ID（逻辑关联 user.id） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 学生的问题 */
    @Column(name = "question", nullable = false, length = 500)
    private String question;

    /** RAG 返回的答案（Top-1 命中条目的解决方案摘要） */
    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    /** 识别到的故障类别 ID（Top-1 命中条目的类别） */
    @Column(name = "category_id")
    private Long categoryId;

    /** 命中的知识条目 ID（Top-1，逻辑关联 knowledge_base.id） */
    @Column(name = "matched_kb_id")
    private Long matchedKbId;

    /** 检索相似度 0~1（向量检索时有值；LIKE 降级时为空） */
    @Column(name = "similarity", precision = 4, scale = 3)
    private BigDecimal similarity;

    /** 是否自助解决：1=是 0=否/未反馈 */
    @Column(name = "is_solved", nullable = false)
    private Boolean isSolved;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;
}
