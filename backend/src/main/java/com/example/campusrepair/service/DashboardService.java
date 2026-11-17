package com.example.campusrepair.service;

import java.util.Map;

/**
 * 数据大屏统计服务
 *
 * @Description 汇聚工单/评价/用户/文件等运营数据，供前端数据大屏展示
 * @Author wmy
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface DashboardService {

    /**
     * 大屏总览数据（一次返回全部聚合指标与图表数据）
     *
     * @return 聚合结果 Map
     */
    Map<String, Object> overview();
}
