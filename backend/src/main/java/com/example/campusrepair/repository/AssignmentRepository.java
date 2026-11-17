package com.example.campusrepair.repository;

import com.example.campusrepair.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 派单记录数据访问仓库
 *
 * @Description 派单审计记录的 JPA 数据访问层
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /**
     * 查询工单的全部派单记录（按时间倒序，用于回溯决策过程）
     *
     * @param orderId 工单ID
     * @return 派单记录列表
     */
    List<Assignment> findByOrderIdOrderByGmtCreateDesc(Long orderId);

    /**
     * 查询师傅的派单记录
     *
     * @param workerId 师傅ID
     * @return 派单记录列表
     */
    List<Assignment> findByWorkerId(Long workerId);
}
