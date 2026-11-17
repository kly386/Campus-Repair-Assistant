package com.example.campusrepair.service;

import com.example.campusrepair.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsCodeSenderTest {

    @Test
    void shouldClassifyQuotaExhausted() {
        SmsCodeSender.ProviderFailure failure = SmsCodeSender.classifySpugFailure("短信额度不足，请充值");
        assertEquals(ErrorCode.SMS_QUOTA_EXHAUSTED, failure.errorCode());
        assertEquals(503, failure.status().value());
        assertFalse(failure.retryable());
    }

    @Test
    void shouldClassifyIpWhitelist() {
        SmsCodeSender.ProviderFailure failure = SmsCodeSender.classifySpugFailure("当前 IP 不在白名单");
        assertEquals(ErrorCode.SMS_IP_NOT_ALLOWED, failure.errorCode());
    }

    @Test
    void shouldClassifyProviderRateLimit() {
        SmsCodeSender.ProviderFailure failure = SmsCodeSender.classifySpugFailure("发送频率过高，请稍后重试");
        assertEquals(ErrorCode.SMS_PROVIDER_RATE_LIMITED, failure.errorCode());
        assertEquals(429, failure.status().value());
        assertTrue(failure.retryable());
    }

    @Test
    void shouldUseGenericErrorForUnknownProviderMessage() {
        SmsCodeSender.ProviderFailure failure = SmsCodeSender.classifySpugFailure("unknown provider error");
        assertEquals(ErrorCode.SMS_SEND_FAILED, failure.errorCode());
    }
}