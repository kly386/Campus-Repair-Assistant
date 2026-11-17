package com.example.campusrepair.service;

import java.util.List;

/**
 * AI 模型客户端（公共封装）
 *
 * @Description 全项目唯一的大模型出口：智能报修的多模态解析、自助诊断的 RAG 问答
 *              都走本接口，便于统一换供应商、统计用量与控制密钥。
 *              采用 OpenAI 兼容协议（/chat/completions），文字与图片（data URI）混合输入。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface AiClient {

    /**
     * 发起一次对话补全（文字 + 可选图片多模态）
     *
     * @param request 系统提示词 + 用户文本 + 图片 data URI 列表
     * @return 模型返回内容与用量信息
     * @throws IllegalStateException 真实调用失败（网络/超时/接口报错）
     */
    ChatResponse chat(ChatRequest request);

    /** 调用入参 */
    record ChatRequest(String systemPrompt, String userText, List<String> imageDataUris, String caller) {
    }

    /** 调用出参：content 为模型文本；tokens/耗时用于用量统计 */
    record ChatResponse(String content, Integer promptTokens, Integer completionTokens,
                        long costMillis, boolean mock) {
    }
}
