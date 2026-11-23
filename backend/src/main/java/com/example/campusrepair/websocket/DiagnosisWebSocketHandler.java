package com.example.campusrepair.websocket;

import com.example.campusrepair.service.KnowledgeService;
import com.example.campusrepair.service.impl.DiagnosisEngine;
import com.example.campusrepair.service.impl.WebSocketEventSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * /ws/diagnosis 消息处理器
 *
 * @Description 上行消息：
 *   {"type":"ask","question":"...","conversationId":"..."}
 *   {"type":"feedback","sessionId":123,"solved":false}
 * 下行消息：meta / delta / done / error / feedback_ack，统一包成
 *   {"type":...,"data":{...}}
 * Handler 只做消息路由，诊断业务全部交给 DiagnosisEngine。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisWebSocketHandler extends TextWebSocketHandler {

    private final DiagnosisEngine diagnosisEngine;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WS 诊断连接建立 session={} userId={}", session.getId(), userId(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketEventSink sink = new WebSocketEventSink(session);
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sink.emit("error", Map.of("message", "消息格式错误"));
            return;
        }

        String type = node.path("type").asText("");
        Long userId = userId(session);
        if (userId == null) {
            sink.emit("error", Map.of("message", "登录已失效，请重新登录"));
            return;
        }

        try {
            switch (type) {
                case "ask" -> {
                    String question = node.path("question").asText("");
                    String conversationId = node.path("conversationId").asText(null);
                    diagnosisEngine.submit(userId, question, conversationId, sink);
                }
                case "feedback" -> handleFeedback(sink, node, userId);
                default -> sink.emit("error", Map.of("message", "不支持的消息类型：" + type));
            }
        } catch (Exception e) {
            log.warn("WS 消息处理失败 type={}: {}", type, e.getMessage());
            sink.emit("error", Map.of("message", "服务暂时不可用，请稍后再试"));
        }
    }

    private void handleFeedback(WebSocketEventSink sink, JsonNode node, Long userId) {
        Long sessionId = node.hasNonNull("sessionId") ? node.get("sessionId").asLong() : null;
        boolean solved = node.path("solved").asBoolean(false);
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("solved", solved);
        if (sessionId == null) {
            data.put("ok", false);
            data.put("message", "会话 ID 缺失");
            sink.emit("feedback_ack", data);
            return;
        }
        try {
            knowledgeService.markSolved(sessionId, userId, solved);
            data.put("ok", true);
        } catch (Exception e) {
            log.warn("WS 反馈失败 sessionId={}: {}", sessionId, e.getMessage());
            data.put("ok", false);
            data.put("message", "反馈失败，请重试");
        }
        sink.emit("feedback_ack", data);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 诊断连接传输异常 session={}: {}", session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
            // 连接已断，忽略
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS 诊断连接关闭 session={} status={}", session.getId(), status);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get("userId");
        return value instanceof Long id ? id : null;
    }
}
