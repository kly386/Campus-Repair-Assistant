package com.example.campusrepair.controller;

import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据大屏 REST 接口
 *
 * @Description 运营数据大屏的统计聚合接口，登录后即可访问
 * @Author wmy
 * @Date 2026-11-17
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(dashboardService.overview());
    }
}
