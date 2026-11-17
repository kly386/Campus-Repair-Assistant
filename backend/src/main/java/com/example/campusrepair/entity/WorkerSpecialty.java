package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 师傅专长实体
 *
 * @Description 师傅-故障类别多对多关联，对应 worker_specialty 表；
 *              调度 Agent 按工单类别筛选具备专长的师傅优先派单
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "worker_specialty")
@Data
public class WorkerSpecialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;
}
