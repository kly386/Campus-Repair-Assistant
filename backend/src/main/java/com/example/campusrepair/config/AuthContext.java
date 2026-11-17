package com.example.campusrepair.config;

import com.example.campusrepair.security.LoginPrincipal;

import java.util.Set;

/** 当前登录用户上下文（线程绑定），请求结束时由拦截器清理。 */
public final class AuthContext {

    private static final ThreadLocal<LoginPrincipal> PRINCIPAL = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginPrincipal principal) {
        PRINCIPAL.set(principal);
    }

    public static LoginPrincipal principal() {
        return PRINCIPAL.get();
    }

    public static Long userId() {
        LoginPrincipal principal = PRINCIPAL.get();
        return principal == null ? null : principal.userId();
    }

    public static String username() {
        LoginPrincipal principal = PRINCIPAL.get();
        return principal == null ? null : principal.username();
    }

    public static String sessionId() {
        LoginPrincipal principal = PRINCIPAL.get();
        return principal == null ? null : principal.sessionId();
    }

    public static Set<String> roles() {
        LoginPrincipal principal = PRINCIPAL.get();
        return principal == null ? Set.of() : principal.roles();
    }

    public static Set<String> permissions() {
        LoginPrincipal principal = PRINCIPAL.get();
        return principal == null ? Set.of() : principal.permissions();
    }

    public static boolean hasPermission(String permission) {
        LoginPrincipal principal = PRINCIPAL.get();
        return principal != null && principal.hasPermission(permission);
    }

    public static void clear() {
        PRINCIPAL.remove();
    }
}