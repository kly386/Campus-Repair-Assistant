package com.example.campusrepair.service;

import com.example.campusrepair.entity.RepairOrder;

import java.util.List;

public interface RepairOrderService {

    RepairOrder createOrder(RepairOrder order);

    RepairOrder updateOrder(RepairOrder order);

    RepairOrder findById(Long id);

    List<RepairOrder> findByStudentId(Long studentId);

    List<RepairOrder> findByStatus(String status);

    void deleteById(Long id);
}