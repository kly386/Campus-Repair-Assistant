package com.example.campusrepair.service.impl;

import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏统计服务实现
 *
 * @Description 全部聚合查询走 @Slave 只读从库，避免大屏轮询挤占主库写入；
 *              结果统一封装为 Map 便于前端 ECharts 直接消费
 * @Author wmy
 * @Date 2026-11-17
 * @Version 1.0
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Slave
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>(16);
        data.put("summary", querySummary());
        data.put("statusDistribution", queryGroupCount(
                "SELECT status AS name, COUNT(*) AS cnt FROM repair_order WHERE is_deleted = 0 GROUP BY status"));
        data.put("emergencyDistribution", queryGroupCount(
                "SELECT emergency_level AS name, COUNT(*) AS cnt FROM repair_order WHERE is_deleted = 0 GROUP BY emergency_level"));
        data.put("categoryTop", queryGroupCount(
                "SELECT COALESCE(NULLIF(c.name, ''), '未分类') AS name, COUNT(*) AS cnt FROM repair_order o "
                        + "LEFT JOIN repair_category c ON o.category_id = c.id "
                        + "WHERE o.is_deleted = 0 GROUP BY COALESCE(NULLIF(c.name, ''), '未分类') ORDER BY cnt DESC LIMIT 5"));
        data.put("buildingTop", queryGroupCount(
                "SELECT COALESCE(NULLIF(TRIM(building), ''), '未填写') AS name, COUNT(*) AS cnt FROM repair_order WHERE is_deleted = 0 "
                        + "GROUP BY COALESCE(NULLIF(TRIM(building), ''), '未填写') ORDER BY cnt DESC LIMIT 5"));
        data.put("workerTop", queryGroupCount(
                "SELECT COALESCE(NULLIF(u.real_name, ''), CONCAT('师傅#', o.worker_id)) AS name, COUNT(*) AS cnt FROM repair_order o "
                        + "LEFT JOIN user u ON o.worker_id = u.id WHERE o.is_deleted = 0 AND o.worker_id IS NOT NULL "
                        + "GROUP BY o.worker_id, COALESCE(NULLIF(u.real_name, ''), CONCAT('师傅#', o.worker_id)) ORDER BY cnt DESC LIMIT 5"));
        data.put("trend", queryTrend14Days());
        data.put("roles", queryGroupCount(
                "SELECT role AS name, COUNT(*) AS cnt FROM user WHERE is_deleted = 0 GROUP BY role"));
        data.put("files", queryFileSummary());
        return data;
    }

    /**
     * 概览指标卡：总量/今日/本月/完成率/处理中/平均满意度
     *
     * @return 指标 Map
     */
    private Map<String, Object> querySummary() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, "
                        + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed, "
                        + "COALESCE(SUM(CASE WHEN status NOT IN ('COMPLETED', 'CANCELLED') THEN 1 ELSE 0 END), 0) AS inProgress, "
                        + "COALESCE(SUM(CASE WHEN gmt_create >= CURDATE() THEN 1 ELSE 0 END), 0) AS todayNew, "
                        + "COALESCE(SUM(CASE WHEN gmt_create >= DATE_FORMAT(NOW(), '%Y-%m-01') THEN 1 ELSE 0 END), 0) AS monthNew "
                        + "FROM repair_order WHERE is_deleted = 0");
        Map<String, Object> summary = new LinkedHashMap<>(8);
        long total = ((Number) row.get("total")).longValue();
        long completed = ((Number) row.get("completed")).longValue();
        summary.put("total", total);
        summary.put("completed", completed);
        summary.put("inProgress", ((Number) row.get("inProgress")).longValue());
        summary.put("todayNew", ((Number) row.get("todayNew")).longValue());
        summary.put("monthNew", ((Number) row.get("monthNew")).longValue());
        // 完成率 = 已完成 / 总量（总量为 0 时给 0，避免除零）
        summary.put("completedRate", total == 0 ? 0.0 : Math.round(completed * 1000.0 / total) / 10.0);
        // 平均满意度：一单一评，取全部评价均分，保留 2 位
        Double avgScore = jdbcTemplate.queryForObject(
                "SELECT AVG(score) FROM order_evaluation", Double.class);
        summary.put("avgScore", avgScore == null ? 0.0 : Math.round(avgScore * 100.0) / 100.0);
        return summary;
    }

    /**
     * 通用分组计数：name/cnt 两列结果转 List<Map>
     *
     * @param sql 聚合 SQL
     * @return 图表数据列表
     */
    private List<Map<String, Object>> queryGroupCount(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(2);
            Object name = row.get("name");
            item.put("name", name == null ? "未分类" : String.valueOf(name));
            item.put("value", ((Number) row.get("cnt")).longValue());
            list.add(item);
        }
        return list;
    }

    /**
     * 近 14 天工单趋势：SQL 只返回有工单的日期，缺失日期在 Java 侧补 0，保证折线连续
     *
     * @return [{date, value}] 14 天连续序列
     */
    private List<Map<String, Object>> queryTrend14Days() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE(gmt_create) AS d, COUNT(*) AS cnt FROM repair_order "
                        + "WHERE is_deleted = 0 AND gmt_create >= DATE_SUB(CURDATE(), INTERVAL 13 DAY) "
                        + "GROUP BY DATE(gmt_create)");
        Map<String, Long> byDay = new LinkedHashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object d = row.get("d");
            if (d != null) {
                byDay.put(String.valueOf(d), ((Number) row.get("cnt")).longValue());
            }
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<Map<String, Object>> trend = new ArrayList<>(14);
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            String day = today.minusDays(i).format(fmt);
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("date", day);
            item.put("value", byDay.getOrDefault(day, 0L));
            trend.add(item);
        }
        return trend;
    }

    /**
     * 文件存储概览：对象数与总字节数
     *
     * @return 文件指标 Map
     */
    private Map<String, Object> queryFileSummary() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS totalSize FROM file_object");
        Map<String, Object> files = new LinkedHashMap<>(2);
        files.put("count", ((Number) row.get("cnt")).longValue());
        files.put("totalSize", ((Number) row.get("totalSize")).longValue());
        return files;
    }
}
