package com.example.campusrepair.controller;

import com.example.campusrepair.entity.Assignment;
import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调度 Agent REST 接口
 *
 * @Description 自动派单、批量派单、改约、派单审计查询
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    /** 单张工单自动派单 */
    @PostMapping("/orders/{orderId}/auto")
    @RequirePermission("dispatch:auto")
    public Assignment autoAssign(@PathVariable Long orderId) {
        return dispatchService.autoAssign(orderId);
    }

    /** 全部待派工单批量自动派单 */
    @PostMapping("/batch")
    @RequirePermission("dispatch:auto")
    public List<DispatchService.BatchResult> batchAssign() {
        return dispatchService.batchAutoAssign();
    }

    /** 改约（学生/管理员），带师傅时段冲突检测 */
    @PutMapping("/orders/{orderId}/reschedule")
    @RequirePermission("dispatch:reschedule")
    public RepairOrder reschedule(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        LocalDateTime newTime = LocalDateTime.parse(body.get("appointmentTime"));
        return dispatchService.reschedule(orderId, newTime);
    }

    /** 工单派单决策审计 */
    @GetMapping("/orders/{orderId}/assignments")
    @RequirePermission("dispatch:read")
    public List<Assignment> assignments(@PathVariable Long orderId) {
        return dispatchService.findAssignments(orderId);
    }
}
