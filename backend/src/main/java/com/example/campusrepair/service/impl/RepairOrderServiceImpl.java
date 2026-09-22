package com.example.campusrepair.service.impl;

import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.repository.RepairOrderRepository;
import com.example.campusrepair.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报修工单服务实现
 *
 * @Description 报修工单业务逻辑实现，负责状态、时间的初始化维护
 * @Author 开璐瑶
 * @Date 2026-09-22
 * @Version 1.0
 */
@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl implements RepairOrderService {

    private final RepairOrderRepository repairOrderRepository;

    @Override
    public RepairOrder createOrder(RepairOrder order) {
        order.setGmtCreate(LocalDateTime.now());
        order.setGmtModified(LocalDateTime.now());
        if (order.getStatus() == null) {
            order.setStatus("SUBMITTED");
        }
        return repairOrderRepository.save(order);
    }

    @Override
    public RepairOrder updateOrder(RepairOrder order) {
        order.setGmtModified(LocalDateTime.now());
        return repairOrderRepository.save(order);
    }

    @Override
    public RepairOrder findById(Long id) {
        return repairOrderRepository.findById(id).orElse(null);
    }

    @Override
    public List<RepairOrder> findByStudentId(Long studentId) {
        return repairOrderRepository.findByStudentId(studentId);
    }

    @Override
    public List<RepairOrder> findByStatus(String status) {
        return repairOrderRepository.findByStatus(status);
    }

    @Override
    public void deleteById(Long id) {
        repairOrderRepository.deleteById(id);
    }
}