package com.example.campusrepair.security;

import java.util.Set;

/** 当前请求的认证主体，由拦截器在每次请求时实时加载。 */
public record LoginPrincipal(
        Long userId,
        String username,
        String sessionId,
        Set<String> roles,
        Set<String> permissions
) {
    public boolean hasPermission(String permission) {
        return permissions.contains("*:*") || permissions.contains(permission);
    }
}