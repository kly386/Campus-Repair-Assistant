package com.example.campusrepair.controller;

import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 全局异常处理，统一输出业务错误码、渠道和是否可重试。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuth(AuthException e) {
        Map<String, Object> data = buildErrorData(e.getData(), e.getErrorCode(),
                e.getChannel(), e.isRetryable());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getCode(), e.getMessage(), data));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message,
                buildErrorData(null, ErrorCode.VALIDATION_ERROR, null, false)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage(),
                buildErrorData(null, ErrorCode.VALIDATION_ERROR, null, false)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage(),
                buildErrorData(null, ErrorCode.VALIDATION_ERROR, null, false)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.error(e.getStatusCode().value(),
                        e.getReason() == null ? "请求处理失败" : e.getReason(),
                        buildErrorData(null, ErrorCode.REQUEST_FAILED, null, false)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求体格式不正确",
                buildErrorData(null, ErrorCode.VALIDATION_ERROR, null, false)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器开小差了，请稍后再试",
                        buildErrorData(null, ErrorCode.INTERNAL_ERROR, null, true)));
    }

    private Map<String, Object> buildErrorData(Object rawData, String errorCode,
                                                String channel, boolean retryable) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (rawData instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) data.put(String.valueOf(key), value);
            });
        } else if (rawData != null) {
            data.put("detail", rawData);
        }
        if (errorCode != null && !errorCode.isBlank()) data.put("errorCode", errorCode);
        if (channel != null && !channel.isBlank()) data.put("channel", channel);
        data.put("retryable", retryable);
        return data;
    }
}