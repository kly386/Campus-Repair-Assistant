package com.example.campusrepair.service;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 智谱 GLM-4-Flash 流式对话服务
 *
 * @Description OpenAI 兼容 SSE 接口，逐块回调增量文本。
 *              aborted 由调用方传入：读取上游流时每块都检查，
 *              为 true 立即抛 AbortException 并关闭连接，停止消耗 token。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.0
 */
public interface ZhipuChatStreamService {

    /**
     * @param systemPrompt 系统指令
     * @param userPrompt   用户消息（含知识片段 + 最近 3 轮历史 + 当前问题）
     * @param onToken      增量文本回调
     * @param aborted      中止检查，返回 true 时抛 AbortException
     */
    void streamChat(String systemPrompt, String userPrompt,
                    Consumer<String> onToken, BooleanSupplier aborted) throws Exception;
}
