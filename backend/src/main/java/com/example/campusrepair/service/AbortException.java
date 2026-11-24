package com.example.campusrepair.service;

/**
 * 用户主动中止生成的信号
 *
 * @Description 在流式 token 回调里抛出，穿透上游 SSE 读取循环并关闭连接，
 *              从而立即停止消耗大模型 token。必须与普通异常区分：
 *              中止发 aborted 事件、不回填答案、不计入"未命中"统计。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.0
 */
public class AbortException extends RuntimeException {

    public AbortException() {
        super("用户主动中止生成");
    }
}
