package com.example.campusrepair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 用户实体（对应 docs/sql/init.sql 中的 user 表，支持账密 / 手机验证码 / 微信扫码三种登录方式）
 *
 * @Description 用户实体：角色 STUDENT/WORKER/ADMIN，微信登录补充 wx_openid 字段
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Data
@Table(name = "user")
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名（账密登录） */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /** 密码（BCrypt 加密；历史明文数据登录成功后自动升级） */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "real_name", nullable = false)
    private String realName;

    /** 角色: STUDENT / WORKER / ADMIN（RBAC 待扩展为角色-权限表） */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** 手机号（短信验证码登录） */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 微信 openid（扫码登录绑定标识） */
    @Column(name = "wx_openid", length = 64)
    private String wxOpenid;

    /** 是否删除: 0正常 / 1删除 */
    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;
}
