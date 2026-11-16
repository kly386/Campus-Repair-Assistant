package com.example.campusrepair.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性凭证存储（内存实现）
 *
 * @Description 承载三类一次性凭证——图形验证码 / 短信验证码 / 微信扫码登录状态，
 *              均带过期时间与"一次性消费"语义（取出即删，防重放）。
 *              设计为进程内 ConcurrentHashMap；上 Redis 时只需将本类实现替换为 Redis 访问。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Service
public class AuthCodeService {

    /** 过期凭证封装（value + 到期时间戳） */
    private record Expired(String value, long expireAt) {
        boolean valid() {
            return System.currentTimeMillis() <= expireAt;
        }
    }

    private final Map<String, Expired> store = new ConcurrentHashMap<>();

    // ---------------- 固定过期时间常量 ----------------
    private static final long CAPTCHA_TTL   = 5 * 60_000;   // 图形验证码 5 分钟
    private static final long SMS_TTL       = 5 * 60_000;   // 短信验证码 5 分钟
    private static final long WX_STATE_TTL  = 3 * 60_000;   // 微信扫码状态 3 分钟
    private static final long LOCK_TTL      = 10 * 60_000;  // 登录失败锁定 10 分钟

    /** 登录失败阈值：连续失败 5 次锁定（反爬要点之一） */
    public static final int MAX_LOGIN_FAIL = 5;

    /**
     * 存（任一类凭证/锁定都可存，但请走下方具名方法保证 TTL 一致性）
     */
    private void put(String key, String value, long ttl) {
        store.put(key, new Expired(value, System.currentTimeMillis() + ttl));
    }

    /**
     * 取即删：取出后立即失效，验证码只能用一次，防重放
     *
     * @return 有效值；不存在/过期返回 null
     */
    private String consume(String key) {
        Expired e = store.remove(key);
        return (e != null && e.valid()) ? e.value() : null;
    }

    /** 取不删：用于轮询型状态（微信扫码） */
    private String peek(String key) {
        Expired e = store.get(key);
        if (e == null || !e.valid()) {
            return null;
        }
        return e.value();
    }

    // ================= 图形验证码 =================

    /** 保存图形验证码 */
    public void saveCaptcha(String captchaId, String code) {
        put("captcha:" + captchaId, code, CAPTCHA_TTL);
    }

    /** 消费图形验证码（取出即删） */
    public String consumeCaptcha(String captchaId) {
        return consume("captcha:" + captchaId);
    }

    // ================= 短信验证码 =================

    /** 生成并保存 6 位短信验证码，返回验证码明文（模拟短信场景直接回显给前端） */
    public String generateSmsCode(String phone) {
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        put("sms:" + phone, code, SMS_TTL);
        return code;
    }

    /** 消费短信验证码 */
    public String consumeSmsCode(String phone) {
        return consume("sms:" + phone);
    }

    // ================= 微信扫码（模拟） =================

    /** 扫码状态值 */
    public static final String WX_WAITING  = "WAITING";
    public static final String WX_SCANNED  = "SCANNED";
    public static final String WX_CONFIRM  = "CONFIRMED";
    public static final String WX_EXPIRED  = "EXPIRED";

    /** 初始化扫码状态 */
    public void initWxState(String uuid) {
        put("wx:" + uuid, WX_WAITING, WX_STATE_TTL);
    }

    /** 模拟手机端「扫码」动作 */
    public void markWxScanned(String uuid) {
        String cur = peek("wx:" + uuid);
        if (WX_WAITING.equals(cur)) {
            put("wx:" + uuid, WX_SCANNED, WX_STATE_TTL);
        }
    }

    /** 模拟手机端「确认登录」动作 */
    public void markWxConfirmed(String uuid) {
        String cur = peek("wx:" + uuid);
        if (WX_SCANNED.equals(cur)) {
            put("wx:" + uuid, WX_CONFIRM, WX_STATE_TTL);
        }
    }

    /** 查询扫码状态（轮询不消耗） */
    public String peekWxState(String uuid) {
        return peek("wx:" + uuid);
    }

    /** 扫码登录成功后清理状态 */
    public void clearWxState(String uuid) {
        store.remove("wx:" + uuid);
    }

    // ================= 登录失败锁定（反爬） =================

    /** 记录一次登录失败，返回当前累计失败次数 */
    public int recordLoginFail(String loginKey) {
        String cur = peek("lock:" + loginKey);
        int n = cur == null ? 0 : Integer.parseInt(cur);
        put("lock:" + loginKey, String.valueOf(n + 1), LOCK_TTL);
        return n + 1;
    }

    /** 是否已被锁定 */
    public boolean isLoginLocked(String loginKey) {
        String cur = peek("lock:" + loginKey);
        return cur != null && Integer.parseInt(cur) >= MAX_LOGIN_FAIL;
    }

    /** 登录成功后清空失败计数 */
    public void clearLoginFail(String loginKey) {
        store.remove("lock:" + loginKey);
    }
}
