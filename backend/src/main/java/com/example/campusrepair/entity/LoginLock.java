package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/** 账号登录锁记录；每个用户每种锁定类型保留一行，便于并发更新和审计。 */
@Data
@Entity
@Table(name = "login_lock", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_lock_type", columnNames = {"user_id", "lock_type"}))
public class LoginLock {

    public static final String TYPE_PASSWORD = "PASSWORD";
    public static final String STATUS_FAILING = "FAILING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RELEASED = "RELEASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lock_type", nullable = false, length = 30)
    private String lockType = TYPE_PASSWORD;

    /** FAILING=失败累计中，ACTIVE=已锁定，RELEASED=已解锁。 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_FAILING;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "unlock_at")
    private LocalDateTime unlockAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "release_reason", length = 200)
    private String releaseReason;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified", insertable = false, updatable = false)
    private LocalDateTime gmtModified;
}