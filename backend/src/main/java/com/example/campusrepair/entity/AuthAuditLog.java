package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 鉴权审计日志：登录锁定/解锁、Token 吊销、角色与权限变更。 */
@Data
@Entity
@Table(name = "auth_audit_log")
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "ip", length = 50)
    private String ip;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;
}