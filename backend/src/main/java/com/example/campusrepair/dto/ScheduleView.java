package com.example.campusrepair.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班列表视图：在 worker_schedule 基础上补充师傅姓名与擅长类别，供管理端直接渲染。
 */
@Data
public class ScheduleView {

    private Long id;
    private Long workerId;
    private String workerName;
    private String specialties;
    private LocalDate workDate;
    private String timeSlot;
    private String area;
    private String status;
    private Integer maxOrders;
    private Integer curOrders;
    private LocalDateTime gmtCreate;
}
