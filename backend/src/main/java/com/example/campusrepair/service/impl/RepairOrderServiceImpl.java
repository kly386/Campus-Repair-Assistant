package com.example.campusrepair.service.impl;

import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.repository.RepairOrderRepository;
import com.example.campusrepair.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl implements RepairOrderService {

    private final RepairOrderRepository repairOrderRepository;

    @Override
    public RepairOrder createOrder(RepairOrder order) {
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        if (order.getStatus() == null) {
            order.setStatus("PENDING");
        }
        return repairOrderRepository.save(order);
    }

    @Override
    public RepairOrder updateOrder(RepairOrder order) {
        order.setUpdateTime(LocalDateTime.now());
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