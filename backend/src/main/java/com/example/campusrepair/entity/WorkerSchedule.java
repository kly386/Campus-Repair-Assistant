package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 师傅排班实体
 *
 * @Description 师傅按日期+时段的排班与负载，对应 worker_schedule 表；
 *              cur_orders 达到 max_orders 即视为该时段饱和，调度 Agent 跳过
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Entity
@Table(name = "worker_schedule")
@Data
public class WorkerSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 时间段: MORNING/AFTERNOON/EVENING */
    @Column(name = "time_slot", nullable = false)
    private String timeSlot;

    /** 负责区域（楼栋范围，逗号分隔，如 "1栋,2栋,3栋"），空表示全区 */
    @Column(name = "area")
    private String area;

    /** 状态: FREE/BUSY/REST */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "max_orders", nullable = false)
    private Integer maxOrders;

    @Column(name = "cur_orders", nullable = false)
    private Integer curOrders = 0;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;
}
