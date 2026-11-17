package com.example.campusrepair.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性验证码与扫码会话存储。
 *
 * <p>图形验证码、短信验证码、邮箱验证码均为一次性凭证；验证码最多尝试 5 次，
 * 防止在有效期内无限猜码。生产多节点建议替换为 Redis，以实现共享状态和原子过期。</p>
 */
@Service
public class AuthCodeService {

    private static final long CAPTCHA_TTL_MILLIS = 5 * 60_000L;
    private static final long CODE_TTL_MILLIS = 5 * 60_000L;
    private static final long WX_TTL_MILLIS = 3 * 60_000L;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String WX_WAITING = "WAITING";
    public static final String WX_SCANNED = "SCANNED";
    public static final String WX_CONFIRMED = "CONFIRMED";
    public static final String WX_EXPIRED = "EXPIRED";
    public static final String WX_USED = "USED";

    private record Captcha(String value, long expireAt) {
        boolean valid() {
            return System.currentTimeMillis() <= expireAt;
        }
    }

    private record Code(String value, long expireAt, int attempts) {
        boolean valid() {
            return System.currentTimeMillis() <= expireAt;
        }
    }

    private record WxSession(String status, long expireAt, String openid) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private final Map<String, Captcha> captchas = new ConcurrentHashMap<>();
    private final Map<String, Code> smsCodes = new ConcurrentHashMap<>();
    private final Map<String, Code> emailCodes = new ConcurrentHashMap<>();
    private final Map<String, WxSession> wxSessions = new ConcurrentHashMap<>();

    public void saveCaptcha(String captchaId, String code) {
        captchas.put(captchaId, new Captcha(code, System.currentTimeMillis() + CAPTCHA_TTL_MILLIS));
    }

    /** 图形验证码无论正确与否都立即失效，防止重放和反复猜码。 */
    public boolean verifyCaptcha(String captchaId, String input) {
        if (captchaId == null || captchaId.isBlank() || input == null) {
            return false;
        }
        Captcha captcha = captchas.remove(captchaId);
        return captcha != null && captcha.valid() && captcha.value().equalsIgnoreCase(input.trim());
    }

    public String generateSmsCode(String phone) {
        String code = randomCode();
        smsCodes.put(phone, new Code(code, System.currentTimeMillis() + CODE_TTL_MILLIS, 0));
        return code;
    }

    public boolean verifySmsCode(String phone, String input) {
        return verifyCode(smsCodes, phone, input);
    }

    public String generateEmailCode(String email) {
        String code = randomCode();
        emailCodes.put(email.toLowerCase(), new Code(code, System.currentTimeMillis() + CODE_TTL_MILLIS, 0));
        return code;
    }

    public boolean verifyEmailCode(String email, String input) {
        return verifyCode(emailCodes, email.toLowerCase(), input);
    }

    private boolean verifyCode(Map<String, Code> store, String key, String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        boolean[] matched = {false};
        store.computeIfPresent(key, (k, code) -> {
            if (!code.valid() || code.attempts() >= MAX_CODE_ATTEMPTS) {
                return null;
            }
            if (code.value().equals(input.trim())) {
                matched[0] = true;
                return null;
            }
            int attempts = code.attempts() + 1;
            return attempts >= MAX_CODE_ATTEMPTS
                    ? null
                    : new Code(code.value(), code.expireAt(), attempts);
        });
        return matched[0];
    }

    private String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    // ---------------- 微信扫码会话 ----------------

    public void createWxSession(String uuid) {
        wxSessions.put(uuid, new WxSession(WX_WAITING, System.currentTimeMillis() + WX_TTL_MILLIS, null));
    }

    public String peekWxState(String uuid) {
        WxSession session = wxSessions.get(uuid);
        if (session == null || session.expired()) {
            return WX_EXPIRED;
        }
        return session.status();
    }

    public long wxRemainingSeconds(String uuid) {
        WxSession session = wxSessions.get(uuid);
        if (session == null) {
            return 0;
        }
        return Math.max(0, (session.expireAt() - System.currentTimeMillis() + 999) / 1000);
    }

    public void markWxScanned(String uuid) {
        updateWxStatus(uuid, WX_WAITING, WX_SCANNED);
    }

    public void markWxConfirmed(String uuid, String openid) {
        wxSessions.computeIfPresent(uuid, (k, session) -> {
            if (session.expired() || (!WX_WAITING.equals(session.status()) && !WX_SCANNED.equals(session.status()))) {
                return session;
            }
            return new WxSession(WX_CONFIRMED, session.expireAt(), openid);
        });
    }

    private void updateWxStatus(String uuid, String expected, String next) {
        wxSessions.computeIfPresent(uuid, (k, session) -> {
            if (session.expired() || !expected.equals(session.status())) {
                return session;
            }
            return new WxSession(next, session.expireAt(), session.openid());
        });
    }

    /** 原子消费已确认的扫码会话，保证一次确认只签发一次 Token。 */
    public String consumeWxConfirmed(String uuid) {
        String[] openid = {null};
        wxSessions.computeIfPresent(uuid, (k, session) -> {
            if (!session.expired() && WX_CONFIRMED.equals(session.status())) {
                openid[0] = session.openid();
                return new WxSession(WX_USED, session.expireAt(), session.openid());
            }
            return session;
        });
        return openid[0];
    }

    public void clearWxSession(String uuid) {
        wxSessions.remove(uuid);
    }
}
