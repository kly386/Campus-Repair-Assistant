package com.example.campusrepair.service.impl;

import com.example.campusrepair.service.DiagnosisEventSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 传输实现（主通道）
 *
 * @Description 用 ConcurrentWebSocketSessionDecorator 包一层，防止诊断线程池和
 *              feedback 处理线程同时 sendMessage 抛 "The remote endpoint was in
 *              [TEXT_PARTIAL_WRITING]" 并发异常。只做事件转发，不写业务判断。
 *              下行消息统一格式：{"type":"meta|delta|done|error|feedback_ack","data":{...}}
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
@Slf4j
public class WebSocketEventSink implements DiagnosisEventSink {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SEND_TIME_LIMIT_MS = 30_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final ConcurrentWebSocketSessionDecorator session;

    public WebSocketEventSink(WebSocketSession session) {
        this.session = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
    }

    @Override
    public void emit(String event, Object data) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", event);
            envelope.put("data", data == null ? Map.of() : data);
            session.sendMessage(new TextMessage(MAPPER.writeValueAsBytes(envelope)));
        } catch (Exception e) {
            log.warn("WS 事件发送失败（客户端可能已断开）event={}: {}", event, e.getMessage());
        }
    }

    @Override
    public void complete() {
        // WebSocket 由前端根据 done 事件自行判断结束，服务端不主动关闭连接
    }
}
