package com.example.campusrepair.config;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.campusrepair.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录态拦截器（鉴权核心）
 *
 * @Description 业务接口必须携带有效 Authorization: Bearer <JWT>。
 *              校验通过后把 userId/role 放进请求与 AuthContext，供业务代码获取当前用户，
 *              也是后续 RBAC @RequirePermission 的前置环节。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // OPTIONS 预检直接放行（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, 401, "未登录或会话已过期");
            return false;
        }
        Claims claims = jwtUtil.parse(header.substring(7));
        if (claims == null) {
            reject(response, 401, "登录已失效，请重新登录");
            return false;
        }
        // 供 Controller 直接读取
        request.setAttribute("userId", Long.valueOf(claims.getSubject()));
        request.setAttribute("role", (String) claims.get("role"));
        AuthContext.set(Long.valueOf(claims.getSubject()), (String) claims.get("role"));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear(); // 防 ThreadLocal 内存泄漏
    }

    private void reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
