package com.example.campusrepair.controller;

import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.entity.RolePermission;
import com.example.campusrepair.repository.RolePermissionRepository;
import com.example.campusrepair.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色已有权限查询，供管理端权限配置弹窗回填勾选状态。
 *
 * <p>只新增 GET /api/admin/roles/{roleId}/permissions，不改动 RbacAdminController
 * 已有的 PUT 同路径接口。</p>
 *
 * @Author 开璐瑶
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RolePermissionQueryController {

    private final RolePermissionRepository rolePermissionRepository;

    @GetMapping("/{roleId}/permissions")
    @RequirePermission("system:permission:read")
    public ApiResponse<List<Long>> rolePermissions(@PathVariable Long roleId) {
        List<Long> ids = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermission::getPermissionId)
                .toList();
        return ApiResponse.success(ids);
    }
}
