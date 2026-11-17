package com.example.campusrepair.repository;

import com.example.campusrepair.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AI 用量统计数据访问仓库
 *
 * @Description 仅写 入库统计；后续做"AI 用量"展示时再扩展查询
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
}
