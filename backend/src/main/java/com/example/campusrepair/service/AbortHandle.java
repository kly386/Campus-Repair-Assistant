package com.example.campusrepair.service;

/**
 * 单次问答的中止句柄
 *
 * @Description volatile 布尔标记：诊断线程池里每收到一块 token 读一次，
 *              WS 线程收到 abort 消息或连接关闭时写一次，可见性由 volatile 保证。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.0
 */
public final class AbortHandle {

    private volatile boolean aborted = false;

    /** 标记中止（幂等） */
    public void abort() {
        aborted = true;
    }

    /** 流式回调每次输出前检查 */
    public boolean isAborted() {
        return aborted;
    }
}
