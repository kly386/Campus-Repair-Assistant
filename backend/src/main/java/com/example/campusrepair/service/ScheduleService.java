package com.example.campusrepair.service;

import com.example.campusrepair.dto.ScheduleRequest;
import com.example.campusrepair.dto.ScheduleView;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 排班管理（CRUD）。只写排班数据，调度 Agent 仍只读排班。
 */
public interface ScheduleService {

    List<ScheduleView> list(Long workerId, LocalDate start, LocalDate end, String status);

    ScheduleView get(Long id);

    ScheduleView create(ScheduleRequest req);

    ScheduleView update(Long id, ScheduleRequest req);

    void delete(Long id);

    /** 可选师傅下拉：id / name / specialties */
    List<Map<String, Object>> workers();
}
