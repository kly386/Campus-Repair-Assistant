package com.example.campusrepair.config;

/**
 * 当前登录用户上下文（线程绑定）
 *
 * @Description 拦截器写入，业务代码随时随地读取当前 userId/role；请求结束由拦截器清理。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Long userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    /** 当前用户ID（未登录场景下为 null，仅限已过拦截器的业务代码使用） */
    public static Long userId() {
        return USER_ID.get();
    }

    /** 当前角色 */
    public static String role() {
        return ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }
}
