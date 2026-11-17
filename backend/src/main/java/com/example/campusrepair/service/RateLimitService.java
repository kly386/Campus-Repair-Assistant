package com.example.campusrepair.service;

import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 接口限流与验证码发送冷却。 */
@Service
public class RateLimitService {

    public static final int LOGIN_PER_MINUTE = 10;
    public static final int CAPTCHA_PER_MINUTE = 30;
    public static final int CODE_SEND_PER_IP_PER_MINUTE = 5;
    public static final long CODE_SEND_COOLDOWN_SECONDS = 60;

    private record Window(long startSecond, AtomicLong counter) {
    }

    private record Cooldown(long untilMillis) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Map<String, Cooldown> cooldowns = new ConcurrentHashMap<>();

    public void checkLogin(String ip) {
        checkFixedWindow("login:ip:" + ip, LOGIN_PER_MINUTE, 60,
                ErrorCode.TOO_MANY_REQUESTS, null);
    }

    public void checkCaptcha(String ip) {
        checkFixedWindow("captcha:ip:" + ip, CAPTCHA_PER_MINUTE, 60,
                ErrorCode.TOO_MANY_REQUESTS, null);
    }

    /** 同一手机号 60 秒一次，同时限制单 IP 每分钟发送次数。 */
    public void checkSmsSend(String phone, String ip) {
        checkCooldown("sms:phone:" + phone, ErrorCode.SEND_COOLDOWN, ErrorCode.CHANNEL_SMS);
        checkFixedWindow("sms:ip:" + ip, CODE_SEND_PER_IP_PER_MINUTE, 60,
                ErrorCode.SEND_IP_LIMITED, ErrorCode.CHANNEL_SMS);
    }

    /** 邮箱验证码采用与短信一致的冷却和 IP 频率控制。 */
    public void checkEmailSend(String email, String ip) {
        checkCooldown("email:target:" + email.toLowerCase(),
                ErrorCode.SEND_COOLDOWN, ErrorCode.CHANNEL_EMAIL);
        checkFixedWindow("email:ip:" + ip, CODE_SEND_PER_IP_PER_MINUTE, 60,
                ErrorCode.SEND_IP_LIMITED, ErrorCode.CHANNEL_EMAIL);
    }

    public void checkCodeSend(String type, String target, String ip) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        switch (normalizedType) {
            case "PHONE", "SMS" -> checkSmsSend(target, ip);
            case "EMAIL" -> checkEmailSend(target, ip);
            default -> throw AuthException.badRequest("不支持的验证码类型: " + type,
                    ErrorCode.CODE_TYPE_UNSUPPORTED, ErrorCode.CHANNEL_AUTH);
        }
    }

    private void checkCooldown(String key, String errorCode, String channel) {
        long retryAfter = acquireCooldown(key, CODE_SEND_COOLDOWN_SECONDS);
        if (retryAfter > 0) {
            throw AuthException.tooManyRequests(
                    "验证码发送过于频繁，请 " + retryAfter + " 秒后再试",
                    retryAfter, errorCode, channel);
        }
    }

    private void checkFixedWindow(String key, int limit, long windowSeconds,
                                  String errorCode, String channel) {
        long retryAfter = acquire(key, limit, windowSeconds);
        if (retryAfter > 0) {
            throw AuthException.tooManyRequests("请求过于频繁，请稍后再试",
                    retryAfter, errorCode, channel);
        }
    }

    public long acquire(String key, int limit, long windowSeconds) {
        long nowSecond = System.currentTimeMillis() / 1000;
        long windowStart = nowSecond - (nowSecond % windowSeconds);
        Window window = windows.compute(key, (k, old) ->
                old == null || old.startSecond() != windowStart
                        ? new Window(windowStart, new AtomicLong()) : old);
        long count = window.counter().incrementAndGet();
        return count <= limit ? 0 : Math.max(1, windowStart + windowSeconds - nowSecond);
    }

    public long acquireCooldown(String key, long cooldownSeconds) {
        long now = System.currentTimeMillis();
        long[] retry = {0};
        cooldowns.compute(key, (k, old) -> {
            if (old != null && old.untilMillis() > now) {
                retry[0] = Math.max(1, (old.untilMillis() - now + 999) / 1000);
                return old;
            }
            retry[0] = 0;
            return new Cooldown(now + cooldownSeconds * 1000);
        });
        return retry[0];
    }
}