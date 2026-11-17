package com.example.campusrepair.exception;

/** 验证码、认证与外部发送服务的稳定业务错误码。 */
public final class ErrorCode {

    public static final String CHANNEL_AUTH = "AUTH";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_EMAIL = "EMAIL";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String REQUEST_FAILED = "REQUEST_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String CAPTCHA_INVALID = "CAPTCHA_INVALID";
    public static final String CODE_INVALID = "CODE_INVALID";
    public static final String CODE_EXPIRED = "CODE_EXPIRED";
    public static final String CODE_ATTEMPTS_EXCEEDED = "CODE_ATTEMPTS_EXCEEDED";
    public static final String CODE_TYPE_UNSUPPORTED = "CODE_TYPE_UNSUPPORTED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String SEND_COOLDOWN = "SEND_COOLDOWN";
    public static final String SEND_IP_LIMITED = "SEND_IP_LIMITED";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";

    public static final String SMS_TARGET_INVALID = "SMS_TARGET_INVALID";
    public static final String SMS_QUOTA_EXHAUSTED = "SMS_QUOTA_EXHAUSTED";
    public static final String SMS_IP_NOT_ALLOWED = "SMS_IP_NOT_ALLOWED";
    public static final String SMS_PROVIDER_RATE_LIMITED = "SMS_PROVIDER_RATE_LIMITED";
    public static final String SMS_TEMPLATE_CONFIG_ERROR = "SMS_TEMPLATE_CONFIG_ERROR";
    public static final String SMS_PROVIDER_TIMEOUT = "SMS_PROVIDER_TIMEOUT";
    public static final String SMS_PROVIDER_UNAVAILABLE = "SMS_PROVIDER_UNAVAILABLE";
    public static final String SMS_SEND_FAILED = "SMS_SEND_FAILED";

    public static final String EMAIL_TARGET_INVALID = "EMAIL_TARGET_INVALID";
    public static final String EMAIL_SMTP_AUTH_FAILED = "EMAIL_SMTP_AUTH_FAILED";
    public static final String EMAIL_SMTP_CONNECT_FAILED = "EMAIL_SMTP_CONNECT_FAILED";
    public static final String EMAIL_SMTP_TIMEOUT = "EMAIL_SMTP_TIMEOUT";
    public static final String EMAIL_TLS_CONFIG_ERROR = "EMAIL_TLS_CONFIG_ERROR";
    public static final String EMAIL_RECIPIENT_REJECTED = "EMAIL_RECIPIENT_REJECTED";
    public static final String EMAIL_MESSAGE_INVALID = "EMAIL_MESSAGE_INVALID";
    public static final String EMAIL_SEND_FAILED = "EMAIL_SEND_FAILED";

    private ErrorCode() {
    }
}