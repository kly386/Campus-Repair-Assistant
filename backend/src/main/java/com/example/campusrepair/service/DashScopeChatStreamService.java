package com.example.campusrepair.service;

import java.util.function.Consumer;

/**
 * DashScope 大模型流式对话（OpenAI 兼容 /chat/completions?stream=true）
 *
 * @Description 给自助诊断提供"逐字输出"能力，模拟真人客服实时回复。
 *              与 EmbeddingService 共用同一个 DASHSCOPE_API_KEY；
 *              调用失败时由上层降级为 Top-1 条目原文，不让主流程挂。
 * @Author 闵娟
 * @Date 2026-11-21
 * @Version 1.0
 */
public interface DashScopeChatStreamService {

    /**
     * 流式对话：每解析到一个 token 就回调 onToken。
     *
     * @param systemPrompt 角色与防幻觉约束
     * @param userPrompt   学生问题 + 检索到的知识条目
     * @param onToken      逐字回调（不可为 null）
     * @throws Exception key 未配置 / 网络失败 / 返回格式异常
     */
    void streamChat(String systemPrompt, String userPrompt, Consumer<String> onToken) throws Exception;
}
