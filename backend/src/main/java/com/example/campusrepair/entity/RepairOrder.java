package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "repair_order")
@Data
public class RepairOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ai_parsed_info", columnDefinition = "TEXT")
    private String aiParsedInfo;

    @Column(name = "ai_confidence", columnDefinition = "DECIMAL(4,3)")
    private BigDecimal aiConfidence;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "emergency_level")
    private String emergencyLevel;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "building")
    private String building;

    @Column(name = "room")
    private String room;

    @Column(name = "appointment_time")
    private LocalDateTime appointmentTime;

    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "assign_score")
    private BigDecimal assignScore;

    @Column(name = "assign_reason")
    private String assignReason;

    @Column(name = "status")
    private String status;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "version")
    private Integer version;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified")
    private LocalDateTime gmtModified;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;
}