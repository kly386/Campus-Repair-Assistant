package com.example.campusrepair.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 实时流式自助诊断（SSE 降级通道）
 *
 * @Description 对外产品只有 WebSocket 实时诊断；本接口保留 SSE 作为内部降级。
 *              业务逻辑统一在 DiagnosisEngine，这里只负责创建 SseEventSink。
 * @Author 闵娟
 * @Date 2026-11-21
 * @Version 1.1
 */
public interface KnowledgeStreamService {

    /** 发起一次 SSE 流式问答，立即返回 SseEmitter；conversationId 用于多轮上下文 */
    SseEmitter streamAsk(Long userId, String question, String conversationId);

    /** 热门问题 Top-5（来自 diagnosis_session 频次；无数据时返回演示兜底） */
    List<Map<String, Object>> hotQuestions();

    /** 自助诊断统计：总提问数 / 已解决数 / 解决率 / 未命中数 */
    Map<String, Object> stats();
}
