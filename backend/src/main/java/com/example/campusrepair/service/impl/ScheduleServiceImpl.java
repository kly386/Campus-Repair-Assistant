package com.example.campusrepair.service.impl;

import com.example.campusrepair.dto.ScheduleRequest;
import com.example.campusrepair.dto.ScheduleView;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.entity.WorkerSchedule;
import com.example.campusrepair.entity.WorkerSpecialty;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.repository.WorkerScheduleRepository;
import com.example.campusrepair.repository.WorkerSpecialtyRepository;
import com.example.campusrepair.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排班管理实现。
 *
 * <p>排班是调度 Agent 的候选数据源，写入时校验：师傅账号身份、日期、时段/状态枚举、
 * 同一师傅同一天同一时段唯一。已有接单的排班不允许删除，避免派单记录悬空。</p>
 *
 * @Author 开璐瑶
 */
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private static final Set<String> SLOTS = Set.of("MORNING", "AFTERNOON", "EVENING");
    private static final Set<String> STATUSES = Set.of("FREE", "BUSY", "REST");

    /** 故障类别展示名，与 init.sql / 前端 Repair.vue 的类别顺序保持一致 */
    private static final Map<Long, String> CATEGORY_NAMES = Map.of(
            1L, "水电维修",
            2L, "网络故障",
            3L, "门窗家具",
            4L, "宿舍电器",
            5L, "报修咨询");

    private final WorkerScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final WorkerSpecialtyRepository specialtyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleView> list(Long workerId, LocalDate start, LocalDate end, String status) {
        Map<Long, String> workerNames = workerNames();
        Map<Long, List<String>> specialties = specialties();

        Comparator<WorkerSchedule> order = Comparator
                .comparing(WorkerSchedule::getWorkDate)
                .thenComparing(w -> slotOrder(w.getTimeSlot()))
                .thenComparing(WorkerSchedule::getWorkerId, Comparator.nullsLast(Long::compareTo));

        return scheduleRepository.findAll().stream()
                .filter(w -> workerId == null || workerId.equals(w.getWorkerId()))
                .filter(w -> start == null || !w.getWorkDate().isBefore(start))
                .filter(w -> end == null || !w.getWorkDate().isAfter(end))
                .filter(w -> status == null || status.isBlank() || status.equalsIgnoreCase(w.getStatus()))
                .sorted(order)
                .map(w -> toView(w, workerNames, specialties))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleView get(Long id) {
        WorkerSchedule w = findById(id);
        return toView(w, workerNames(), specialties());
    }

    @Override
    @Transactional
    public ScheduleView create(ScheduleRequest req) {
        validate(req);
        scheduleRepository.findByWorkerIdAndWorkDateAndTimeSlot(
                req.getWorkerId(), req.getWorkDate(), req.getTimeSlot())
                .ifPresent(x -> {
                    throw new IllegalArgumentException("该师傅在此日期时段已有排班，请勿重复添加");
                });

        WorkerSchedule w = new WorkerSchedule();
        apply(w, req);
        LocalDateTime now = LocalDateTime.now();
        w.setGmtCreate(now);
        w.setGmtModified(now);
        w = scheduleRepository.save(w);
        return get(w.getId());
    }

    @Override
    @Transactional
    public ScheduleView update(Long id, ScheduleRequest req) {
        validate(req);
        WorkerSchedule w = findById(id);
        scheduleRepository.findByWorkerIdAndWorkDateAndTimeSlot(
                req.getWorkerId(), req.getWorkDate(), req.getTimeSlot())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("该师傅在此日期时段已有排班");
                    }
                });

        apply(w, req);
        w.setGmtModified(LocalDateTime.now());
        scheduleRepository.save(w);
        return get(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        WorkerSchedule w = findById(id);
        if (w.getCurOrders() != null && w.getCurOrders() > 0) {
            throw new IllegalArgumentException(
                    "该排班已接 " + w.getCurOrders() + " 单，不能删除；请先改约或调整负载");
        }
        scheduleRepository.delete(w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> workers() {
        Map<Long, List<String>> specialties = specialties();
        return userRepository.findAll().stream()
                .filter(u -> "WORKER".equalsIgnoreCase(u.getRole()))
                .filter(u -> u.getIsDeleted() == null || u.getIsDeleted() == 0)
                .sorted(Comparator.comparing(User::getId))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getRealName());
                    m.put("specialties", String.join("、",
                            specialties.getOrDefault(u.getId(), List.of())));
                    return m;
                })
                .toList();
    }

    // ------------------------------------------------------------------

    private WorkerSchedule findById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("排班不存在: " + id));
    }

    private void validate(ScheduleRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getWorkerId() == null) {
            throw new IllegalArgumentException("请选择师傅");
        }
        if (req.getWorkDate() == null) {
            throw new IllegalArgumentException("请选择排班日期");
        }
        if (req.getTimeSlot() == null || !SLOTS.contains(req.getTimeSlot().toUpperCase())) {
            throw new IllegalArgumentException("时间段只能是 MORNING / AFTERNOON / EVENING");
        }
        if (req.getStatus() == null || !STATUSES.contains(req.getStatus().toUpperCase())) {
            throw new IllegalArgumentException("状态只能是 FREE / BUSY / REST");
        }
        if (req.getMaxOrders() == null || req.getMaxOrders() < 1) {
            throw new IllegalArgumentException("最大接单数至少为 1");
        }
        User worker = userRepository.findById(req.getWorkerId())
                .orElseThrow(() -> new IllegalArgumentException("师傅账号不存在"));
        if (!"WORKER".equalsIgnoreCase(worker.getRole())) {
            throw new IllegalArgumentException("所选账号不是维修师傅");
        }
        req.setTimeSlot(req.getTimeSlot().toUpperCase());
        req.setStatus(req.getStatus().toUpperCase());
    }

    private void apply(WorkerSchedule w, ScheduleRequest req) {
        w.setWorkerId(req.getWorkerId());
        w.setWorkDate(req.getWorkDate());
        w.setTimeSlot(req.getTimeSlot().toUpperCase());
        w.setArea(req.getArea());
        w.setStatus(req.getStatus().toUpperCase());
        w.setMaxOrders(req.getMaxOrders());
        w.setCurOrders(req.getCurOrders() == null ? 0 : req.getCurOrders());
    }

    private ScheduleView toView(WorkerSchedule w, Map<Long, String> names,
                                Map<Long, List<String>> specialties) {
        ScheduleView v = new ScheduleView();
        v.setId(w.getId());
        v.setWorkerId(w.getWorkerId());
        v.setWorkerName(names.getOrDefault(w.getWorkerId(), "师傅#" + w.getWorkerId()));
        v.setSpecialties(String.join("、", specialties.getOrDefault(w.getWorkerId(), List.of())));
        v.setWorkDate(w.getWorkDate());
        v.setTimeSlot(w.getTimeSlot());
        v.setArea(w.getArea());
        v.setStatus(w.getStatus());
        v.setMaxOrders(w.getMaxOrders());
        v.setCurOrders(w.getCurOrders());
        v.setGmtCreate(w.getGmtCreate());
        return v;
    }

    private Map<Long, String> workerNames() {
        Map<Long, String> names = new HashMap<>();
        for (User u : userRepository.findAll()) {
            names.put(u.getId(), u.getRealName());
        }
        return names;
    }

    private Map<Long, List<String>> specialties() {
        Map<Long, List<String>> result = new HashMap<>();
        for (WorkerSpecialty ws : specialtyRepository.findAll()) {
            String name = CATEGORY_NAMES.get(ws.getCategoryId());
            if (name != null) {
                result.computeIfAbsent(ws.getWorkerId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    private int slotOrder(String slot) {
        if (slot == null) return 9;
        return switch (slot.toUpperCase()) {
            case "MORNING" -> 0;
            case "AFTERNOON" -> 1;
            case "EVENING" -> 2;
            default -> 9;
        };
    }
}
