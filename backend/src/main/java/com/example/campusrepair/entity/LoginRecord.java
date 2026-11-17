package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 登录审计记录：成功、失败、锁定原因、IP、终端指纹均可追溯。 */
@Data
@Entity
@Table(name = "login_record")
public class LoginRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** PASSWORD / SMS / EMAIL / WECHAT / REFRESH。 */
    @Column(name = "login_type", nullable = false, length = 20)
    private String loginType;

    @Column(name = "identifier", length = 100)
    private String identifier;

    @Column(name = "ip", length = 50)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "client_fingerprint", length = 64)
    private String clientFingerprint;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;
}