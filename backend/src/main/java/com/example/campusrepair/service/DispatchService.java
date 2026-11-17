package com.example.campusrepair.service;

import com.example.campusrepair.entity.Assignment;
import com.example.campusrepair.entity.RepairOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调度 Agent 服务接口
 *
 * @Description 自动派单、冲突检测、改约的核心调度能力定义
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface DispatchService {

    /**
     * 对单张工单执行自动派单
     *
     * @param orderId 工单ID
     * @return 派单成功时返回中选记录；候选不足或竞争失败返回 null（调用方需给出提示）
     */
    Assignment autoAssign(Long orderId);

    /**
     * 对所有待派单（SUBMITTED 且无师傅）工单批量自动派单
     *
     * @return 每张工单的派单结果（value=true 表示成功）
     */
    List<BatchResult> batchAutoAssign();

    /**
     * 学生/管理员改约：校验目标时段师傅是否空闲（冲突检测），通过后更新预约时间并迁移排班负载
     *
     * @param orderId  工单ID
     * @param newTime  新预约时间
     * @return 更新后的工单
     */
    RepairOrder reschedule(Long orderId, LocalDateTime newTime);

    /**
     * 查询工单的派单决策记录（审计）
     *
     * @param orderId 工单ID
     * @return 派单记录列表
     */
    List<Assignment> findAssignments(Long orderId);

    /**
     * 批量派单结果
     */
    record BatchResult(Long orderId, boolean success, Long workerId, String message) {
    }
}
