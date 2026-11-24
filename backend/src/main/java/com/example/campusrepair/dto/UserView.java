package com.example.campusrepair.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理列表视图：在 user 表基础上补充 RBAC 角色信息，避免前端二次请求。
 */
@Data
public class UserView {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private String phone;
    private String email;
    private String building;
    private String room;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime gmtCreate;
    private List<Long> roleIds = new ArrayList<>();
    private List<String> roleNames = new ArrayList<>();
}
