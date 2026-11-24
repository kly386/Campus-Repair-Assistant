package com.example.campusrepair.controller;

import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.dto.ScheduleRequest;
import com.example.campusrepair.dto.ScheduleView;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 排班管理 REST 接口（管理端「调度排班」页）。
 *
 * <p>调度 Agent 只读排班，排班的新增/修改/删除全部走这里。</p>
 *
 * @Author 开璐瑶
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 排班列表，支持按师傅/日期区间/状态筛选 */
    @GetMapping
    @RequirePermission("schedule:read")
    public ApiResponse<List<ScheduleView>> list(
            @RequestParam(required = false) Long workerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(scheduleService.list(workerId, start, end, status));
    }

    /** 可选师傅下拉 */
    @GetMapping("/workers")
    @RequirePermission("schedule:read")
    public ApiResponse<List<Map<String, Object>>> workers() {
        return ApiResponse.success(scheduleService.workers());
    }

    @GetMapping("/{id}")
    @RequirePermission("schedule:read")
    public ApiResponse<ScheduleView> get(@PathVariable Long id) {
        return ApiResponse.success(scheduleService.get(id));
    }

    @PostMapping
    @RequirePermission("schedule:create")
    public ApiResponse<ScheduleView> create(@Valid @RequestBody ScheduleRequest req) {
        return ApiResponse.success("排班已新增", scheduleService.create(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("schedule:update")
    public ApiResponse<ScheduleView> update(@PathVariable Long id,
                                            @Valid @RequestBody ScheduleRequest req) {
        return ApiResponse.success("排班已更新", scheduleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("schedule:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ApiResponse.success("排班已删除", null);
    }
}
