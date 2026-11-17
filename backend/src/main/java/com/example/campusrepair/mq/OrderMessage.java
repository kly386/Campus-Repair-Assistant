package com.example.campusrepair.mq;

/**
 * 工单领域消息（不可变）
 *
 * @Description 消息队列中的最小消息单元：type 表示业务事件，orderId 为业务主键。
 *              现有事件：ORDER_SUBMITTED（工单提交，触发后台自动派单）
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
public record OrderMessage(String type, Long orderId) {

    /** 工单提交事件：消费者收到后调用调度 Agent 自动派单 */
    public static final String ORDER_SUBMITTED = "ORDER_SUBMITTED";
}
