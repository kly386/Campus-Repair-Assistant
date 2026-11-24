package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.dto.UserUpdateRequest;
import com.example.campusrepair.dto.UserView;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.UserAdminService;
import com.example.campusrepair.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口（管理端「用户权限」页）。
 *
 * <p>角色分配 / 锁定解锁沿用 RbacAdminController 的 /api/admin/users/{id}/... 接口。</p>
 *
 * @Author 开璐瑶
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    /** 用户列表，可按关键字/角色/状态筛选 */
    @GetMapping
    @RequirePermission("system:user:read")
    public ApiResponse<List<UserView>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(userAdminService.users(keyword, role, status));
    }

    /** 修改用户基本信息与状态 */
    @PutMapping("/{id}")
    @RequirePermission("system:user:update")
    public ApiResponse<UserView> update(@PathVariable Long id,
                                        @RequestBody UserUpdateRequest req,
                                        HttpServletRequest request) {
        return ApiResponse.success("用户信息已更新",
                userAdminService.update(id, req, AuthContext.userId(), RequestUtils.clientIp(request)));
    }
}
