package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录会话 / Token 吊销表。
 *
 * <p>JWT 仍是无状态签名令牌，但每次请求会校验服务端会话。退出、账号锁定、权限变更时
 * 只需吊销会话即可让已签发 Token 立即失效，解决纯 JWT 无法主动登出的安全漏洞。</p>
 */
@Data
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "access_jti", nullable = false, length = 64)
    private String accessJti;

    @Column(name = "refresh_jti", nullable = false, length = 64)
    private String refreshJti;

    @Column(name = "access_expires_at", nullable = false)
    private LocalDateTime accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "client_fingerprint", nullable = false, length = 64)
    private String clientFingerprint;

    @Column(name = "ip", length = 50)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified", insertable = false, updatable = false)
    private LocalDateTime gmtModified;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}