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

/** 用户与角色关联（标准 RBAC 多对多关系表）。 */
@Data
@Entity
@Table(name = "user_role", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_role", columnNames = {"user_id", "role_id"}))
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;
}