package com.example.campusrepair.controller;

import com.example.campusrepair.dto.RepairParseResult;
import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.AiRepairParseService;
import com.example.campusrepair.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 报修工单 REST 接口
 *
 * @Description 报修工单的增删改查 + AI 智能解析接口
 * @Author 开璐瑶
 * @Date 2026-09-22
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class RepairOrderController {

    private final RepairOrderService repairOrderService;
    private final AiRepairParseService aiRepairParseService;

    @PostMapping
    @RequirePermission("order:create")
    public RepairOrder create(@RequestBody RepairOrder order) {
        return repairOrderService.createOrder(order);
    }

    /**
     * AI 智能解析：文字 + 拍照 → 类别/楼栋/房间/紧急度/置信度
     * 正则优先、LLM 兜底，失败自动降级不阻塞提交
     */
    @PostMapping("/parse")
    @RequirePermission("order:create")
    public RepairParseResult parse(@RequestParam(value = "text", required = false) String text,
                                   @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return aiRepairParseService.parse(text, images);
    }

    @PutMapping
    @RequirePermission("order:update")
    public RepairOrder update(@RequestBody RepairOrder order) {
        return repairOrderService.updateOrder(order);
    }

    @GetMapping("/{id}")
    @RequirePermission("order:read")
    public RepairOrder getById(@PathVariable Long id) {
        return repairOrderService.findById(id);
    }

    @GetMapping("/student/{studentId}")
    @RequirePermission("order:read")
    public List<RepairOrder> getByStudent(@PathVariable Long studentId) {
        return repairOrderService.findByStudentId(studentId);
    }

    @GetMapping("/status/{status}")
    @RequirePermission("order:read")
    public List<RepairOrder> getByStatus(@PathVariable String status) {
        return repairOrderService.findByStatus(status);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("order:delete")
    public void delete(@PathVariable Long id) {
        repairOrderService.deleteById(id);
    }
}