package com.example.campusrepair.controller;

import com.example.campusrepair.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（鉴权相关）
 *
 * @Description 统一将鉴权/反爬类异常转为明确响应码：429 限流，400 参数/验证码错误，500 兜底。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthController.RateLimitException.class)
    public ApiResponse<Void> handleRateLimit(AuthController.RateLimitException e) {
        return ApiResponse.error(429, "请求过于频繁，请稍后再试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.error(500, "服务器开小差了，请稍后再试");
    }
}
