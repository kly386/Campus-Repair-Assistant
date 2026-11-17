package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.entity.WorkerSchedule;
import com.example.campusrepair.repository.RepairOrderRepository;
import com.example.campusrepair.repository.WorkerScheduleRepository;
import com.example.campusrepair.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 师傅端接单工作台 REST 接口
 *
 * @Description 师傅视角：我的工单列表、接单/出发/完成状态流转、我的排班。
 *              所有接口以 AuthContext 当前登录用户为师傅本人，禁止查看他人工单
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/worker/my")
@RequiredArgsConstructor
public class WorkerWorkbenchController {

    private final RepairOrderRepository repairOrderRepository;
    private final WorkerScheduleRepository workerScheduleRepository;

    /** 我的工单（活跃单在前） */
    @GetMapping("/orders")
    @RequirePermission("order:read")
    public List<RepairOrder> myOrders() {
        Long workerId = requireWorkerId();
        List<RepairOrder> orders = repairOrderRepository.findByWorkerId(workerId);
        orders.sort((a, b) -> {
            int pa = activeRank(a.getStatus()), pb = activeRank(b.getStatus());
            return pa != pb ? Integer.compare(pa, pb)
                    : a.getAppointmentTime() == null || b.getAppointmentTime() == null
                    ? 0 : a.getAppointmentTime().compareTo(b.getAppointmentTime());
        });
        return orders;
    }

    /** 接单：ASSIGNED → PENDING_ACCEPT */
    @PostMapping("/orders/{id}/accept")
    @RequirePermission("order:update")
    public RepairOrder accept(@PathVariable Long id) {
        return transition(id, "ASSIGNED", "PENDING_ACCEPT", null);
    }

    /** 出发上门：PENDING_ACCEPT → ON_THE_WAY */
    @PostMapping("/orders/{id}/depart")
    @RequirePermission("order:update")
    public RepairOrder depart(@PathVariable Long id) {
        return transition(id, "PENDING_ACCEPT", "ON_THE_WAY", null);
    }

    /** 开始维修：ON_THE_WAY → PROCESSING */
    @PostMapping("/orders/{id}/start")
    @RequirePermission("order:update")
    public RepairOrder start(@PathVariable Long id) {
        return transition(id, "ON_THE_WAY", "PROCESSING", null);
    }

    /** 完成：PROCESSING → COMPLETED，body: { result } 维修结果说明 */
    @PostMapping("/orders/{id}/complete")
    @RequirePermission("order:update")
    public RepairOrder complete(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return transition(id, "PROCESSING", "COMPLETED", body.get("result"));
    }

    /** 我的排班（今天起 7 天） */
    @GetMapping("/schedule")
    @RequirePermission("order:read")
    public List<WorkerSchedule> mySchedule() {
        return workerScheduleRepository.findByWorkerIdAndWorkDateBetween(
                requireWorkerId(), LocalDate.now(), LocalDate.now().plusDays(7));
    }

    // ------------------------------------------------------------------

    private Long requireWorkerId() {
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录或会话已失效");
        }
        return userId;
    }

    /** 校验归属与前置状态后执行状态流转 */
    private RepairOrder transition(Long id, String expected, String target, String result) {
        Long workerId = requireWorkerId();
        RepairOrder order = repairOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));
        if (!workerId.equals(order.getWorkerId())) {
            throw new IllegalArgumentException("无权操作他人工单");
        }
        if (!expected.equals(order.getStatus())) {
            throw new IllegalArgumentException("工单当前状态为 " + order.getStatus() + "，无法执行该操作");
        }
        order.setStatus(target);
        if (result != null) {
            order.setResult(result);
        }
        if ("COMPLETED".equals(target)) {
            order.setFinishTime(LocalDateTime.now());
        }
        order.setGmtModified(LocalDateTime.now());
        return repairOrderRepository.save(order);
    }

    private int activeRank(String status) {
        return switch (status == null ? "" : status) {
            case "PROCESSING", "ON_THE_WAY" -> 0;
            case "PENDING_ACCEPT" -> 1;
            case "ASSIGNED" -> 2;
            default -> 3; // 已完成/取消等
        };
    }
}
