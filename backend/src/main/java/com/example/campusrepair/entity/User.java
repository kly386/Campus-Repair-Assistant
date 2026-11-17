package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体。
 *
 * <p>保留 {@code role} 字段用于兼容旧业务查询，但真正的授权关系由
 * {@code user_role -> role_permission -> permission} 动态计算。新登录用户会同时写入
 * 旧角色字段和标准 RBAC 关联表，后续可平滑移除旧字段。</p>
 */
@Data
@Entity
@Table(name = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 密码；兼容历史明文，登录成功后自动升级。 */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    /** 兼容字段：STUDENT / WORKER / ADMIN。 */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "wx_openid", length = 64)
    private String wxOpenid;

    /** 账号状态：ACTIVE / LOCKED / DISABLED。 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    /** 全局令牌版本，可用于一次性吊销某用户全部 Token。 */
    @Column(name = "token_version", nullable = false)
    private Long tokenVersion = 0L;

    @Column(name = "building", length = 50)
    private String building;

    @Column(name = "room", length = 50)
    private String room;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified", insertable = false, updatable = false)
    private LocalDateTime gmtModified;
}