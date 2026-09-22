package com.example.campusrepair.service;

import com.example.campusrepair.entity.RepairOrder;

import java.util.List;

/**
 * 报修工单服务接口
 *
 * @Description 报修工单的业务接口定义
 * @Author 开璐瑶
 * @Date 2026-09-22
 * @Version 1.0
 */
public interface RepairOrderService {

    /**
     * 创建报修工单并初始化状态与时间
     *
     * @param order 报修工单
     * @return 已保存的工单
     */
    RepairOrder createOrder(RepairOrder order);

    /**
     * 更新报修工单（刷新 gmt_modified）
     *
     * @param order 报修工单
     * @return 已保存的工单
     */
    RepairOrder updateOrder(RepairOrder order);

    /**
     * 按主键查询工单
     *
     * @param id 工单ID
     * @return 工单，不存在返回 null
     */
    RepairOrder findById(Long id);

    /**
     * 按学生查询其报修工单
     *
     * @param studentId 学生ID
     * @return 工单列表
     */
    List<RepairOrder> findByStudentId(Long studentId);

    /**
     * 按状态查询工单
     *
     * @param status 工单状态
     * @return 工单列表
     */
    List<RepairOrder> findByStatus(String status);

    /**
     * 删除工单
     *
     * @param id 工单ID
     */
    void deleteById(Long id);
}