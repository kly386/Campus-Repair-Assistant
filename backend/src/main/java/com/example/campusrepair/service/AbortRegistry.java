package com.example.campusrepair.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行中任务的中止注册表
 *
 * @Description ConcurrentHashMap 维护 userId + conversationId 到 AbortHandle 的映射。
 *              用户点停止时按 conversationId 精确中止；WebSocket 连接关闭时
 *              按 userId 一次性中止该用户全部任务，避免浏览器断开后继续烧 token。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.0
 */
@Component
public class AbortRegistry {

    private final ConcurrentHashMap<String, AbortHandle> active = new ConcurrentHashMap<>();

    /** 开始一次诊断时注册句柄（同一会话重复提问会覆盖旧句柄） */
    public AbortHandle register(Long userId, String conversationId) {
        AbortHandle handle = new AbortHandle();
        active.put(key(userId, conversationId), handle);
        return handle;
    }

    /** 用户点"停止生成"：只中止当前会话这一轮 */
    public void abort(Long userId, String conversationId) {
        AbortHandle handle = active.get(key(userId, conversationId));
        if (handle != null) {
            handle.abort();
        }
    }

    /** WebSocket 断开：中止该用户所有正在运行的任务 */
    public void abortAllByUser(Long userId) {
        if (userId == null) {
            return;
        }
        String prefix = userId + ":";
        active.forEach((key, handle) -> {
            if (key.startsWith(prefix)) {
                handle.abort();
            }
        });
    }

    /** 任务结束（含正常/异常/中止）后注销句柄 */
    public void remove(Long userId, String conversationId) {
        active.remove(key(userId, conversationId));
    }

    private String key(Long userId, String conversationId) {
        return userId + ":" + (conversationId == null ? "" : conversationId);
    }
}
