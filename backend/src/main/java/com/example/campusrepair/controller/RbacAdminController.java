package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.entity.Permission;
import com.example.campusrepair.entity.Role;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.AccountLockService;
import com.example.campusrepair.service.RbacAdminService;
import com.example.campusrepair.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * RBAC 与账号锁定管理接口。
 *
 * <p>角色/权限变更和锁定操作均写审计日志，并立即使目标用户 Token 失效。
 * 所有接口都带 {@link RequirePermission}，不能只靠前端隐藏菜单。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RbacAdminController {

    private final RbacAdminService rbacAdminService;
    private final AccountLockService accountLockService;

    @GetMapping("/roles")
    @RequirePermission("system:role:read")
    public ApiResponse<List<Role>> roles() {
        return ApiResponse.success(rbacAdminService.roles());
    }

    @GetMapping("/permissions")
    @RequirePermission("system:permission:read")
    public ApiResponse<List<Permission>> permissions() {
        return ApiResponse.success(rbacAdminService.permissions());
    }

    @PutMapping("/users/{userId}/roles")
    @RequirePermission("system:user:role")
    public ApiResponse<Void> assignRoles(@PathVariable Long userId,
                                         @Valid @RequestBody UserRoleRequest req,
                                         HttpServletRequest request) {
        rbacAdminService.assignUserRoles(userId, req.getRoleIds(), AuthContext.userId(),
                RequestUtils.clientIp(request));
        return ApiResponse.success("角色已更新，目标用户需重新登录", null);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @RequirePermission("system:role:permission")
    public ApiResponse<Void> assignPermissions(@PathVariable Long roleId,
                                               @Valid @RequestBody RolePermissionRequest req,
                                               HttpServletRequest request) {
        rbacAdminService.assignRolePermissions(roleId, req.getPermissionIds(), AuthContext.userId(),
                RequestUtils.clientIp(request));
        return ApiResponse.success("权限已更新，受影响用户需重新登录", null);
    }

    @PostMapping("/users/{userId}/lock")
    @RequirePermission("system:user:lock")
    public ApiResponse<Void> lock(@PathVariable Long userId,
                                  @Valid @RequestBody LockRequest req,
                                  HttpServletRequest request) {
        accountLockService.lockUser(userId, req.getMinutes(), AuthContext.userId(),
                RequestUtils.clientIp(request));
        return ApiResponse.success("账号已锁定", null);
    }

    @PostMapping("/users/{userId}/unlock")
    @RequirePermission("system:user:unlock")
    public ApiResponse<Void> unlock(@PathVariable Long userId, HttpServletRequest request) {
        accountLockService.unlockUser(userId, AuthContext.userId(), RequestUtils.clientIp(request));
        return ApiResponse.success("账号已解锁", null);
    }

    @Data
    public static class UserRoleRequest {
        private Set<Long> roleIds;
    }

    @Data
    public static class RolePermissionRequest {
        private Set<Long> permissionIds;
    }

    @Data
    public static class LockRequest {
        @Min(value = 1, message = "锁定时间至少 1 分钟")
        @Max(value = 1440, message = "锁定时间最多 1440 分钟")
        private long minutes = 10;
    }
}