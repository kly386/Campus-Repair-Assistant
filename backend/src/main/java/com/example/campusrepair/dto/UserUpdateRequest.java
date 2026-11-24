package com.example.campusrepair.dto;

import lombok.Data;

/**
 * 管理员修改用户基本信息。密码与角色不在这里改，分别走注册/角色分配接口。
 */
@Data
public class UserUpdateRequest {

    private String realName;
    private String phone;
    private String email;
    private String building;
    private String room;
    private String status;
}
