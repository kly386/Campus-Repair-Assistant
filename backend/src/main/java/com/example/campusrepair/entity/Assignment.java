package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单记录实体
 *
 * @Description 调度 Agent 每次派单尝试的审计记录，对应 assignment 表；
 *              is_success=0 表示候选但未中选（如并发抢单失败），用于回溯决策过程
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "assignment")
@Data
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "assign_score", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal assignScore;

    @Column(name = "assign_reason")
    private String assignReason;

    /** 是否派单成功: 1成功/0失败（如并发抢单落败仅留痕） */
    @Column(name = "is_success", nullable = false, columnDefinition = "TINYINT(1) UNSIGNED")
    private Boolean isSuccess = true;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;
}
