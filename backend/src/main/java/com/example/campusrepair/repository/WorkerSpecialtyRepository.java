package com.example.campusrepair.repository;

import com.example.campusrepair.entity.WorkerSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 师傅专长数据访问仓库
 *
 * @Description 师傅-故障类别关联的 JPA 数据访问层
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Repository
public interface WorkerSpecialtyRepository extends JpaRepository<WorkerSpecialty, Long> {

    /**
     * 查询掌握某故障类别的全部师傅ID
     *
     * @param categoryId 故障类别ID
     * @return 师傅ID列表
     */
    @Query("SELECT ws.workerId FROM WorkerSpecialty ws WHERE ws.categoryId = :categoryId")
    List<Long> findWorkerIdByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 查询师傅掌握的全部专长
     *
     * @param workerId 师傅ID
     * @return 专长列表
     */
    List<WorkerSpecialty> findByWorkerId(Long workerId);
}
