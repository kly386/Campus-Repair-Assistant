package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.dto.MenuNode;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前用户信息与动态权限接口。
 *
 * <p>前端登录后调用 /me 获取角色、权限和菜单树，再据此动态挂载路由和按钮。</p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Long userId = AuthContext.userId();
        User user = userRepository.findByIdAndIsDeleted(userId, 0)
                .orElseThrow(() -> AuthException.unauthorized("用户不存在或已删除"));
        PermissionService.AccessProfile profile = permissionService.loadProfile(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("role", user.getRole());
        data.put("roles", profile.roles());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("status", user.getStatus());
        data.put("permissions", profile.permissions());
        data.put("menus", profile.menus());
        return ApiResponse.success(data);
    }

    @GetMapping("/permissions")
    public ApiResponse<Set<String>> permissions() {
        return ApiResponse.success(permissionService.permissionCodes(AuthContext.userId()));
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuNode>> menus() {
        return ApiResponse.success(permissionService.loadProfile(AuthContext.userId()).menus());
    }
}