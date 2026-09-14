package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "repair_order")
@Data
public class RepairOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "building")
    private String building;

    @Column(name = "room")
    private String room;

    @Column(name = "emergency_level")
    private String emergencyLevel;

    @Column(name = "status")
    private String status;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}