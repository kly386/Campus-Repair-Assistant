package com.example.campusrepair.service.impl;

import com.example.campusrepair.service.DiagnosisEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 传输实现（降级通道）
 *
 * @Description 只做事件转发，不参与任何诊断业务判断。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
@Slf4j
public class SseEventSink implements DiagnosisEventSink {

    private final SseEmitter emitter;

    public SseEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void emit(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.warn("SSE 事件发送失败（客户端可能已断开）event={}: {}", event, e.getMessage());
        }
    }

    @Override
    public void complete() {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete 失败: {}", e.getMessage());
        }
    }
}
