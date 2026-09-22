package com.example.campusrepair.repository;

import com.example.campusrepair.entity.RepairOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 报修工单数据访问仓库
 *
 * @Description 报修工单 JPA 数据访问层
 * @Author 开璐瑶
 * @Date 2026-09-22
 * @Version 1.0
 */
@Repository
public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {

    List<RepairOrder> findByStudentId(Long studentId);

    List<RepairOrder> findByStatus(String status);
}