package com.example.campusrepair.websocket;

import com.example.campusrepair.service.TokenSessionService;
import com.example.campusrepair.util.JwtUtil;
import com.example.campusrepair.util.RequestUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * WebSocket 握手鉴权
 *
 * @Description 浏览器 new WebSocket() 不能设置 Authorization 头，也不能用 URL query
 *              传 token（会进 Nginx access log），所以走 Sec-WebSocket-Protocol：
 *              campus-diag.&lt;base64url(access_token)&gt;.&lt;base64url(client_fp)&gt;
 *              校验链路与 HTTP 的 AuthInterceptor 完全一致：JwtUtil.parse →
 *              TokenSessionService.validateAccess(claims, sha256(指纹))。
 *              握手成功必须回显子协议，否则浏览器会直接关闭连接。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisHandshakeInterceptor implements HandshakeInterceptor {

    private static final String PROTOCOL_PREFIX = "campus-diag.";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_SESSION_ID = "sessionId";

    private final JwtUtil jwtUtil;
    private final TokenSessionService tokenSessionService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocols == null || protocols.isBlank()) {
            reject(response, HttpStatus.UNAUTHORIZED, "缺少 WebSocket 子协议");
            return false;
        }
        String protocol = java.util.Arrays.stream(protocols.split(","))
                .map(String::trim)
                .filter(p -> p.startsWith(PROTOCOL_PREFIX))
                .findFirst()
                .orElse(null);
        if (protocol == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "WebSocket 子协议不合法");
            return false;
        }

        try {
            String[] parts = protocol.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("子协议格式错误");
            }
            String accessToken = decode(parts[1]);
            String clientFingerprint = decode(parts[2]);
            Claims claims = jwtUtil.parse(accessToken);
            if (claims == null) {
                throw new IllegalArgumentException("登录已失效");
            }
            // 登录时 RequestUtils.clientInfo() 存的是 sha256(上报指纹)，这里必须用同样的算法
            TokenSessionService.CurrentSession session =
                    tokenSessionService.validateAccess(claims, RequestUtils.sha256(clientFingerprint));

            attributes.put(ATTR_USER_ID, session.userId());
            attributes.put(ATTR_SESSION_ID, session.sessionId());
            // 子协议必须回显，否则浏览器按 RFC6455 直接断开
            response.getHeaders().set("Sec-WebSocket-Protocol", protocol);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket 握手鉴权失败: {}", e.getMessage());
            reject(response, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理；握手失败的原因已在 beforeHandshake 里写入响应头
    }

    private String decode(String value) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void reject(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().set("X-WS-Error", message);
    }
}
