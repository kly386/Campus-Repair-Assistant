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

/** 角色与权限关联（角色授权变更后必须吊销该角色下用户的 Token）。 */
@Data
@Entity
@Table(name = "role_permission", uniqueConstraints = @UniqueConstraint(
        name = "uk_role_permission", columnNames = {"role_id", "permission_id"}))
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;
}