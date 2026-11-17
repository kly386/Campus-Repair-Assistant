package com.example.campusrepair.config;

import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.security.LoginPrincipal;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.AccountLockService;
import com.example.campusrepair.service.PermissionService;
import com.example.campusrepair.service.TokenSessionService;
import com.example.campusrepair.util.JwtUtil;
import com.example.campusrepair.util.RequestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 统一身份认证与接口级 RBAC 拦截器。
 *
 * <p>每个受保护请求依次执行：JWT 签名校验 -> 服务端会话/jti 校验 -> 客户端指纹校验
 * -> 账号锁定/禁用校验 -> 实时加载角色权限 -> {@code @RequirePermission} 校验。
 * 因此前端隐藏按钮不是安全边界，直接调用接口同样会被后端拦截。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenSessionService tokenSessionService;
    private final AccountLockService accountLockService;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        try {
            Claims claims = parseBearer(request);
            TokenSessionService.CurrentSession session = tokenSessionService.validateAccess(
                    claims, RequestUtils.clientInfo(request).fingerprint());
            User user = userRepository.findByIdAndIsDeleted(session.userId(), 0)
                    .orElseThrow(() -> AuthException.unauthorized("用户不存在或已删除"));
            accountLockService.assertLoginAllowed(user);

            PermissionService.AccessProfile profile = permissionService.loadProfile(user.getId());
            LoginPrincipal principal = new LoginPrincipal(user.getId(), user.getUsername(),
                    session.sessionId(), profile.roles(), profile.permissions());
            AuthContext.set(principal);
            checkPermission(handler, principal);
            return true;
        } catch (AuthException e) {
            writeError(response, e);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private Claims parseBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw AuthException.unauthorized("未登录或会话已过期");
        }
        Claims claims = jwtUtil.parse(header.substring(7).trim());
        if (claims == null) {
            throw AuthException.unauthorized("登录已失效，请重新登录");
        }
        return claims;
    }

    private void checkPermission(Object handler, LoginPrincipal principal) {
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        RequirePermission permission = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequirePermission.class);
        if (permission == null) {
            permission = AnnotatedElementUtils.findMergedAnnotation(
                    method.getBeanType(), RequirePermission.class);
        }
        if (permission != null && !principal.hasPermission(permission.value())) {
            throw AuthException.forbidden("无权访问该接口：" + permission.value());
        }
    }

    private void writeError(HttpServletResponse response, AuthException e) throws Exception {
        response.setStatus(e.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(e.getCode(), e.getMessage(), e.getData()));
    }
}