package com.example.campusrepair.service;

/**
 * 自助诊断事件出口（传输层抽象）
 *
 * @Description DiagnosisEngine 只认这个接口，不关心事件最终走 SSE 还是 WebSocket。
 *              SseEventSink / WebSocketEventSink 只负责把事件发出去，不做任何业务判断。
 *              事件名统一：meta / delta / done / error / feedback_ack。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
public interface DiagnosisEventSink {

    /** 推送一个事件；data 为可 JSON 序列化的对象（通常是 Map） */
    void emit(String event, Object data);

    /** 本次问答事件流结束；WebSocket 通道无需特殊处理，SSE 需要 complete emitter */
    void complete();
}
