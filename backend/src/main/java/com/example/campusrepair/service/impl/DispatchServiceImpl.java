package com.example.campusrepair.service.impl;

import com.example.campusrepair.entity.Assignment;
import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.entity.WorkerSchedule;
import com.example.campusrepair.repository.AssignmentRepository;
import com.example.campusrepair.repository.RepairOrderRepository;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.repository.WorkerScheduleRepository;
import com.example.campusrepair.repository.WorkerSpecialtyRepository;
import com.example.campusrepair.service.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调度 Agent 服务实现
 *
 * @Description 自动派单评分模型（专长 40 + 负载均衡 30 + 区域就近 20 + 紧急加权 10），
 *              冲突检测（排班状态/饱和度/同时刻活单三重校验），
 *              原子抢单（数据库行级条件裁决并发），改约（冲突校验 + 负载迁移）
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final RepairOrderRepository repairOrderRepository;
    private final WorkerScheduleRepository workerScheduleRepository;
    private final WorkerSpecialtyRepository workerSpecialtyRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    /** 派单失败即视为终态，不再参与冲突判定的工单状态 */
    private static final List<String> CLOSED_STATUSES = List.of("COMPLETED", "CANCELLED", "REJECTED");

    @Override
    @Transactional
    public Assignment autoAssign(Long orderId) {
        RepairOrder order = repairOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在: " + orderId));
        if (order.getWorkerId() != null || !"SUBMITTED".equals(order.getStatus())) {
            throw new IllegalStateException("工单当前状态不可派单: status=" + order.getStatus()
                    + ", workerId=" + order.getWorkerId());
        }

        List<Candidate> candidates = collectCandidates(order);
        List<Assignment> failedRecords = new ArrayList<>();
        Assignment chosen = null;
        for (Candidate c : candidates) {
            // 逐个尝试：赢家经 claimOrder 原子落库，输家（并发竞争/负载被抢）留痕后继续下一候选
            int claimed = repairOrderRepository.claimOrder(orderId, c.workerId, c.score, c.reason);
            Assignment record = buildRecord(orderId, c, claimed == 1);
            assignmentRepository.save(record);
            if (claimed == 1) {
                chosen = record;
                chosen.setIsSuccess(true);
                // 命中排班负载 +1（失败说明恰好并发饱和，工单已指派但负载记为 0 容忍微小偏差）
                workerScheduleRepository.incrementCurOrders(c.scheduleId());
                break;
            }
            failedRecords.add(record);
        }
        if (chosen == null) {
            // 全部候选竞争失败或无候选：抛出让前端提示改约或手工派单
            throw new IllegalStateException("自动派单失败：候选师傅均不可用或竞争落败，请尝试改约时间或手工派单");
        }
        return chosen;
    }

    @Override
    @Transactional
    public List<BatchResult> batchAutoAssign() {
        List<RepairOrder> pending = repairOrderRepository
                .findByWorkerIdIsNullAndStatusOrderByEmergencyLevelDescIdAsc("SUBMITTED");
        List<BatchResult> results = new ArrayList<>();
        for (RepairOrder order : pending) {
            try {
                Assignment a = autoAssign(order.getId());
                results.add(new BatchResult(order.getId(), true, a.getWorkerId(), a.getAssignReason()));
            } catch (Exception e) {
                results.add(new BatchResult(order.getId(), false, null, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public RepairOrder reschedule(Long orderId, LocalDateTime newTime) {
        if (newTime == null || newTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("改约时间必须晚于当前时间");
        }
        RepairOrder order = repairOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在: " + orderId));
        if (CLOSED_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("工单已" + order.getStatus() + "，不可改约");
        }
        if (order.getWorkerId() != null) {
            // 已有师傅：检测新时段 ±1 小时窗口内师傅是否已有活单（维修约 1 小时，重叠即冲突）
            List<RepairOrder> conflicts = repairOrderRepository.findWorkerConflicts(
                    order.getWorkerId(), newTime.minusHours(1), newTime.plusHours(1), CLOSED_STATUSES);
            if (!conflicts.isEmpty()) {
                throw new IllegalArgumentException("改约冲突：该师傅在目标时段前后 1 小时内已有 " + conflicts.size()
                        + " 张活单（最近：" + conflicts.get(0).getOrderNo() + "），请改约其他时间");
            }
            // 迁移排班负载：旧时段 -1，新时段 +1
            Slot oldSlot = toSlot(order.getAppointmentTime());
            Slot newSlot = toSlot(newTime);
            if (oldSlot != null) {
                workerScheduleRepository.findByWorkerIdAndWorkDateAndTimeSlot(order.getWorkerId(), oldSlot.date(), oldSlot.name())
                        .ifPresent(s -> workerScheduleRepository.decrementCurOrders(s.getId()));
            }
            if (newSlot != null) {
                WorkerSchedule target = workerScheduleRepository
                        .findByWorkerIdAndWorkDateAndTimeSlot(order.getWorkerId(), newSlot.date(), newSlot.name())
                        .orElseThrow(() -> new IllegalArgumentException("师傅在目标时段无排班，无法改约"));
                if (target.getCurOrders() >= target.getMaxOrders()) {
                    throw new IllegalArgumentException("师傅目标时段已饱和，无法改约");
                }
                workerScheduleRepository.incrementCurOrders(target.getId());
            }
        }
        order.setAppointmentTime(newTime);
        order.setGmtModified(LocalDateTime.now());
        return repairOrderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assignment> findAssignments(Long orderId) {
        return assignmentRepository.findByOrderIdOrderByGmtCreateDesc(orderId);
    }

    // ------------------------------------------------------------------
    // 评分与候选集
    // ------------------------------------------------------------------

    /** 候选师傅：专长匹配 → 排班可用 → 冲突校验 → 评分排序 */
    private List<Candidate> collectCandidates(RepairOrder order) {
        Slot slot = toSlot(order.getAppointmentTime());
        if (slot == null) {
            throw new IllegalArgumentException("工单缺少有效预约时间，无法自动派单");
        }
        List<Long> skilled = workerSpecialtyRepository.findWorkerIdByCategoryId(order.getCategoryId());
        List<Candidate> candidates = new ArrayList<>();
        for (WorkerSchedule ws : workerScheduleRepository.findByWorkDate(slot.date())) {
            if (!slot.name().equals(ws.getTimeSlot()) || skilled.contains(ws.getWorkerId()) == false) {
                continue;
            }
            if (!"FREE".equals(ws.getStatus()) || ws.getCurOrders() >= ws.getMaxOrders()) {
                continue; // 冲突：休息/繁忙/已饱和
            }
            // 冲突：该师傅在预约时刻 ±1 小时窗口内已有活单
            List<RepairOrder> conflicts = repairOrderRepository.findWorkerConflicts(
                    ws.getWorkerId(), order.getAppointmentTime().minusHours(1),
                    order.getAppointmentTime().plusHours(1), CLOSED_STATUSES);
            if (!conflicts.isEmpty()) {
                continue;
            }
            candidates.add(score(order, ws));
        }
        candidates.sort((a, b) -> b.score.compareTo(a.score));
        return candidates;
    }

    /**
     * 评分模型（满分 100）：
     * 专长匹配 40（在候选集内必有）+ 负载均衡 30（剩余容量比例）+
     * 区域就近 20（排班区域覆盖工单楼栋）+ 紧急加权 10（HIGH 全额，MEDIUM 半额）
     */
    private Candidate score(RepairOrder order, WorkerSchedule ws) {
        BigDecimal score = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();

        score = score.add(BigDecimal.valueOf(40));
        reasons.add("专长匹配+40");

        double freeRatio = (ws.getMaxOrders() - ws.getCurOrders()) / (double) Math.max(ws.getMaxOrders(), 1);
        int loadScore = (int) Math.round(freeRatio * 30);
        score = score.add(BigDecimal.valueOf(loadScore));
        reasons.add("负载均衡+" + loadScore);

        int areaScore = 10;
        if (ws.getArea() == null || ws.getArea().isBlank()) {
            areaScore = 15; // 全区师傅次优
        } else if (ws.getArea().contains(order.getBuilding())) {
            areaScore = 20; // 排班区域直接覆盖工单楼栋最优
        }
        score = score.add(BigDecimal.valueOf(areaScore));
        reasons.add("区域就近+" + areaScore);

        int emergencyScore = "HIGH".equals(order.getEmergencyLevel()) ? 10
                : "MEDIUM".equals(order.getEmergencyLevel()) ? 5 : 0;
        if (emergencyScore > 0) {
            // 紧急工单偏向负载更低的师傅，尽快消化
            emergencyScore = (int) Math.round(emergencyScore * freeRatio);
            score = score.add(BigDecimal.valueOf(emergencyScore));
            reasons.add("紧急加权+" + emergencyScore);
        }

        return new Candidate(ws.getWorkerId(), ws.getId(), score.setScale(2, RoundingMode.HALF_UP),
                "调度Agent: " + String.join(", ", reasons) + "（排班#" + ws.getId() + " " + ws.getWorkDate() + " " + ws.getTimeSlot() + "）");
    }

    private Assignment buildRecord(Long orderId, Candidate c, boolean success) {
        Assignment a = new Assignment();
        a.setOrderId(orderId);
        a.setWorkerId(c.workerId);
        a.setAssignScore(c.score);
        a.setAssignReason(c.reason);
        a.setIsSuccess(success);
        LocalDateTime now = LocalDateTime.now();
        a.setGmtCreate(now);
        a.setGmtModified(now);
        return a;
    }

    /** 预约时间 → 排班日期 + 时段 */
    private Slot toSlot(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        LocalTime t = time.toLocalTime();
        String name = t.isBefore(LocalTime.NOON) ? "MORNING"
                : t.isBefore(LocalTime.of(18, 0)) ? "AFTERNOON" : "EVENING";
        return new Slot(time.toLocalDate(), name);
    }

    private record Slot(LocalDate date, String name) {
    }

    private record Candidate(Long workerId, Long scheduleId, BigDecimal score, String reason) {
    }
}
