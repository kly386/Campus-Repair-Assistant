package com.example.campusrepair.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证、授权与验证码发送异常。
 *
 * <p>除 HTTP 状态外，异常还携带稳定业务错误码、发送渠道和是否可重试，
 * 全局异常处理器会把这些字段合并进统一响应的 data 中，前端据此展示用户可理解的提示。</p>
 */
@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final int code;
    private final Object data;
    private final String errorCode;
    private final String channel;
    private final boolean retryable;

    public AuthException(HttpStatus status, int code, String message) {
        this(status, code, message, null, null, null, false);
    }

    public AuthException(HttpStatus status, int code, String message, Object data) {
        this(status, code, message, null, null, data, false);
    }

    public AuthException(HttpStatus status, int code, String message, String errorCode,
                         String channel, Object data, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
        this.errorCode = errorCode;
        this.channel = channel;
        this.retryable = retryable;
    }

    public static AuthException of(HttpStatus status, String message, String errorCode,
                                   String channel, boolean retryable, Object data) {
        return new AuthException(status, status.value(), message, errorCode, channel, data, retryable);
    }

    public static AuthException badRequest(String message) {
        return badRequest(message, ErrorCode.VALIDATION_ERROR, null);
    }

    public static AuthException badRequest(String message, String errorCode, String channel) {
        return of(HttpStatus.BAD_REQUEST, message, errorCode, channel, false, null);
    }

    public static AuthException unauthorized(String message) {
        return unauthorized(message, ErrorCode.INVALID_CREDENTIALS, ErrorCode.CHANNEL_AUTH);
    }

    public static AuthException unauthorized(String message, String errorCode, String channel) {
        return of(HttpStatus.UNAUTHORIZED, message, errorCode, channel, false, null);
    }

    public static AuthException forbidden(String message) {
        return forbidden(message, ErrorCode.ACCESS_DENIED, ErrorCode.CHANNEL_AUTH);
    }

    public static AuthException forbidden(String message, String errorCode, String channel) {
        return of(HttpStatus.FORBIDDEN, message, errorCode, channel, false, null);
    }

    public static AuthException tooManyRequests(String message, long retryAfterSeconds) {
        return tooManyRequests(message, retryAfterSeconds, ErrorCode.TOO_MANY_REQUESTS, null);
    }

    public static AuthException tooManyRequests(String message, long retryAfterSeconds,
                                                String errorCode, String channel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retryAfterSeconds", Math.max(1, retryAfterSeconds));
        return of(HttpStatus.TOO_MANY_REQUESTS, message, errorCode, channel, true, data);
    }

    public static AuthException serviceUnavailable(String message, String errorCode, String channel) {
        return of(HttpStatus.SERVICE_UNAVAILABLE, message, errorCode, channel, true, null);
    }

    public static AuthException accountLocked(long remainingMinutes) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lockRemainingMinutes", remainingMinutes);
        return of(HttpStatus.LOCKED, "账号已锁定，请 " + remainingMinutes + " 分钟后再试",
                ErrorCode.ACCOUNT_LOCKED, ErrorCode.CHANNEL_AUTH, true, data);
    }
}