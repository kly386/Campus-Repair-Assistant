package com.example.campusrepair.config;

import com.example.campusrepair.websocket.DiagnosisHandshakeInterceptor;
import com.example.campusrepair.websocket.DiagnosisWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 *
 * @Description /ws/diagnosis 走项目 JWT 握手鉴权，不新增任何 API key。
 *              跨域白名单复用 app.cors.allowed-origins，与 HTTP 接口保持一致。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DiagnosisWebSocketHandler diagnosisWebSocketHandler;
    private final DiagnosisHandshakeInterceptor diagnosisHandshakeInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(diagnosisWebSocketHandler, "/ws/diagnosis")
                .addInterceptors(diagnosisHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
