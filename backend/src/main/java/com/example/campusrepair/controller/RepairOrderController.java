package com.example.campusrepair.controller;

import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class RepairOrderController {

    private final RepairOrderService repairOrderService;

    @PostMapping
    public RepairOrder create(@RequestBody RepairOrder order) {
        return repairOrderService.createOrder(order);
    }

    @PutMapping
    public RepairOrder update(@RequestBody RepairOrder order) {
        return repairOrderService.updateOrder(order);
    }

    @GetMapping("/{id}")
    public RepairOrder getById(@PathVariable Long id) {
        return repairOrderService.findById(id);
    }

    @GetMapping("/student/{studentId}")
    public List<RepairOrder> getByStudent(@PathVariable Long studentId) {
        return repairOrderService.findByStudentId(studentId);
    }

    @GetMapping("/status/{status}")
    public List<RepairOrder> getByStatus(@PathVariable String status) {
        return repairOrderService.findByStatus(status);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repairOrderService.deleteById(id);
    }
}