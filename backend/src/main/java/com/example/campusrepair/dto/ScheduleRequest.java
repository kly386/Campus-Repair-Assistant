package com.example.campusrepair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 排班新增/修改请求体。
 */
@Data
public class ScheduleRequest {

    @NotNull(message = "请选择师傅")
    private Long workerId;

    @NotNull(message = "请选择排班日期")
    private LocalDate workDate;

    private String timeSlot;

    private String area;

    private String status;

    @NotNull(message = "请填写最大接单数")
    @Min(value = 1, message = "最大接单数至少为 1")
    private Integer maxOrders;

    private Integer curOrders = 0;
}
