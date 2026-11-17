package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.entity.RepairOrder;
import com.example.campusrepair.repository.RepairOrderRepository;
import com.example.campusrepair.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 报修工单服务实现
 *
 * @Description 报修工单业务逻辑实现：状态/时间初始化、工单号与幂等键生成、
 *              紧急度规范化、报修人取当前登录用户
 * @Author 开璐瑶
 * @Date 2026-09-22
 * @Version 1.0
 */
@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl implements RepairOrderService {

    /** 合法紧急度集合（与 repair_order.emergency_level 注释约定一致） */
    private static final Set<String> EMERGENCY_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private static final Random ORDER_RANDOM = new Random();

    private final RepairOrderRepository repairOrderRepository;

    @Override
    public RepairOrder createOrder(RepairOrder order) {
        LocalDateTime now = LocalDateTime.now();
        order.setGmtCreate(now);
        order.setGmtModified(now);
        if (order.getStatus() == null) {
            order.setStatus("SUBMITTED");
        }
        // 报修人：以当前登录用户为准，防止前端伪造/漏传
        if (order.getStudentId() == null) {
            order.setStudentId(AuthContext.userId());
        }
        // 工单号：RO + 时间戳 + 3位随机，冲突概率可忽略
        if (!StringUtils.hasText(order.getOrderNo())) {
            order.setOrderNo("RO" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%03d", ORDER_RANDOM.nextInt(1000)));
        }
        // 幂等键：前端未传则生成（保证唯一索引不炸，防重由前端生成时承担）
        if (!StringUtils.hasText(order.getIdempotencyKey())) {
            order.setIdempotencyKey("auto-" + java.util.UUID.randomUUID());
        }
        // 紧急度规范化为大写枚举（前端可能传小写）
        order.setEmergencyLevel(normalizeEmergency(order.getEmergencyLevel()));
        // 乐观锁版本号：新单固定 0
        if (order.getVersion() == null) {
            order.setVersion(0);
        }
        return repairOrderRepository.save(order);
    }

    private String normalizeEmergency(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "LOW";
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return EMERGENCY_LEVELS.contains(upper) ? upper : "LOW";
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
    @Slave
    public List<RepairOrder> findByStatus(String status) {
        return repairOrderRepository.findByStatus(status);
    }

    @Override
    public void deleteById(Long id) {
        repairOrderRepository.deleteById(id);
    }
}