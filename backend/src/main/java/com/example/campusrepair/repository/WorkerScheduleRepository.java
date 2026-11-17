package com.example.campusrepair.repository;

import com.example.campusrepair.entity.WorkerSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 师傅排班数据访问仓库
 *
 * @Description 排班查询与负载计数的 JPA 数据访问层
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Repository
public interface WorkerScheduleRepository extends JpaRepository<WorkerSchedule, Long> {

    /**
     * 查询师傅某天某时段的排班
     *
     * @param workerId 师傅ID
     * @param workDate 排班日期
     * @param timeSlot 时段
     * @return 排班记录
     */
    Optional<WorkerSchedule> findByWorkerIdAndWorkDateAndTimeSlot(Long workerId, LocalDate workDate, String timeSlot);

    /**
     * 查询师傅某天全部排班
     *
     * @param workerId 师傅ID
     * @param workDate 排班日期
     * @return 排班记录列表
     */
    List<WorkerSchedule> findByWorkerIdAndWorkDate(Long workerId, LocalDate workDate);

    /**
     * 查询某天全部排班（调度候选集）
     *
     * @param workDate 排班日期
     * @return 排班记录列表
     */
    List<WorkerSchedule> findByWorkDate(LocalDate workDate);

    /**
     * 派单成功后原子递增已接单数，条件 FREE 且未饱和，防止并发超发
     *
     * @param id 排班ID
     * @return 影响行数：1 成功，0 已被并发占满
     */
    @Modifying
    @Query("UPDATE WorkerSchedule w SET w.curOrders = w.curOrders + 1 " +
           "WHERE w.id = :id AND w.status = 'FREE' AND w.curOrders < w.maxOrders")
    int incrementCurOrders(@Param("id") Long id);

    /**
     * 改约释放排班负载：原子递减（不低于 0）
     *
     * @param id 排班ID
     * @return 影响行数
     */
    @Modifying
    @Query("UPDATE WorkerSchedule w SET w.curOrders = CASE WHEN w.curOrders > 0 THEN w.curOrders - 1 ELSE 0 END " +
           "WHERE w.id = :id")
    int decrementCurOrders(@Param("id") Long id);
}
