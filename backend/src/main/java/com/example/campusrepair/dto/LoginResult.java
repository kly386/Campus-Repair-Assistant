package com.example.campusrepair.dto;

import java.util.List;
import java.util.Set;

/**
 * 登录成功响应。
 *
 * <p>{@code token} 与 {@code accessToken} 相同，保留 token 字段兼容现有前端；
 * 新前端应优先使用 accessToken + refreshToken，并按 permissions 动态挂载路由和按钮。</p>
 */
public record LoginResult(
        String token,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        String realName,
        String role,
        List<String> roles,
        Set<String> permissions,
        List<MenuNode> menus
) {
}