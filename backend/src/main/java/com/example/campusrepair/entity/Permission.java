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
 * 权限实体。
 *
 * <p>权限不只用于菜单展示：后端接口通过 {@code @RequirePermission} 使用 permission.code
 * 做接口级校验，前端再根据同一份权限集合渲染菜单和按钮。</p>
 */
@Data
@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** MENU / API / BUTTON。 */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "route_path", length = 200)
    private String routePath;

    @Column(name = "component", length = 200)
    private String component;

    @Column(name = "api_path", length = 200)
    private String apiPath;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;

    @Column(name = "gmt_create", insertable = false, updatable = false)
    private LocalDateTime gmtCreate;

    @Column(name = "gmt_modified", insertable = false, updatable = false)
    private LocalDateTime gmtModified;
}