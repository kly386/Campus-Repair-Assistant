package com.example.campusrepair.repository;

import com.example.campusrepair.entity.RepairOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * 按学生ID查询其报修工单
     *
     * @param studentId 学生ID
     * @return 工单列表
     */
    List<RepairOrder> findByStudentId(Long studentId);

    /**
     * 按状态查询报修工单
     *
     * @param status 工单状态
     * @return 工单列表
     */
    List<RepairOrder> findByStatus(String status);

    /**
     * 原子抢单：仅当工单仍无师傅且为 SUBMITTED 时写入派单结果，
     * 并发派单/手工派单靠数据库行级条件裁决，输家影响行数为 0
     *
     * @param id           工单ID
     * @param workerId     师傅ID
     * @param assignScore  派单评分
     * @param assignReason 派单理由
     * @return 影响行数：1 成功，0 竞争失败
     */
    @Modifying
    @Query("UPDATE RepairOrder o SET o.workerId = :workerId, o.assignScore = :assignScore, " +
           "o.assignReason = :assignReason, o.status = 'ASSIGNED', o.gmtModified = CURRENT_TIMESTAMP " +
           "WHERE o.id = :id AND o.workerId IS NULL AND o.status = 'SUBMITTED'")
    int claimOrder(@Param("id") Long id, @Param("workerId") Long workerId,
                   @Param("assignScore") java.math.BigDecimal assignScore,
                   @Param("assignReason") String assignReason);

    /**
     * 查询师傅在目标时刻 ±1 小时窗口内的活单（冲突检测：维修约 1 小时，重叠即冲突）
     *
     * @param workerId 师傅ID
     * @param start    窗口起点
     * @param end      窗口终点
     * @return 冲突工单列表
     */
    @Query("SELECT o FROM RepairOrder o WHERE o.workerId = :workerId " +
           "AND o.appointmentTime >= :start AND o.appointmentTime < :end " +
           "AND o.status NOT IN :statuses")
    List<RepairOrder> findWorkerConflicts(@Param("workerId") Long workerId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end,
                                          @Param("statuses") List<String> statuses);

    /**
     * 查询师傅名下的工单
     *
     * @param workerId 师傅ID
     * @return 工单列表
     */
    List<RepairOrder> findByWorkerId(Long workerId);

    /**
     * 查询待派单工单：紧急优先、先到先处理
     *
     * @return 工单列表
     */
    List<RepairOrder> findByWorkerIdIsNullAndStatusOrderByEmergencyLevelDescIdAsc(String status);
}