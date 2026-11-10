package com.example.campusrepair.controller;

import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.service.AuthCodeService;
import com.example.campusrepair.service.AuthService;
import com.example.campusrepair.service.RateLimitService;
import com.example.campusrepair.util.CaptchaGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 登录接口（鉴权入口，全部放行勿加拦截）
 *
 * @Description 三种登录方式 + 图形验证码下发。
 *              反爬防线：登录类接口 RateLimit 10 次/分/IP；账密连续失败 5 次锁 10 分钟。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCodeService authCodeService;
    private final RateLimitService rateLimitService;

    // ================= 1. 图形验证码（账密登录用） =================

    /**
     * 下发图形验证码
     *
     * @return captchaId + 图片 data URI
     */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        String code = CaptchaGenerator.randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        authCodeService.saveCaptcha(captchaId, code);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("captchaId", captchaId);
        data.put("image", CaptchaGenerator.toDataUri(code));
        return ApiResponse.success(data);
    }

    /**
     * 账密登录（带图形验证码）
     */
    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@RequestBody PasswordLoginRequest req,
                                                      HttpServletRequest request) {
        String ip = clientIp(request);
        checkRate("login:" + ip);
        String loginKey = req.getUsername() != null ? req.getUsername() : ip;
        if (authCodeService.isLoginLocked(loginKey)) {
            return ApiResponse.error(429, "失败次数过多，请 10 分钟后再试");
        }
        try {
            AuthService.LoginResult result = authService.loginByPassword(
                    req.getUsername(), req.getPassword(), req.getCaptchaId(), req.getCaptchaCode(), loginKey);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            int fails = authCodeService.recordLoginFail(loginKey);
            int remain = AuthCodeService.MAX_LOGIN_FAIL;
            return ApiResponse.error(401, e.getMessage() + "（" + fails + "/" + remain + "）");
        }
    }

    // ================= 2. 手机短信验证码登录 =================

    /**
     * 发送短信验证码（模拟场景验证码将随响应返回，便于演示）
     */
    @PostMapping("/sms/send")
    public ApiResponse<Map<String, String>> sendSms(@RequestBody SmsSendRequest req,
                                                    HttpServletRequest request) {
        checkRate("sms:" + clientIp(request));
        String code = authService.sendSmsCode(req.getPhone());
        Map<String, String> data = new LinkedHashMap<>();
        if (code != null) {
            data.put("mockCode", code); // smsMock=true 才返回，生产模式无此字段
        }
        return ApiResponse.success("验证码已发送", data);
    }

    /**
     * 短信验证码登录（未注册手机号自动开通学生账号）
     */
    @PostMapping("/sms/login")
    public ApiResponse<AuthService.LoginResult> smsLogin(@RequestBody SmsLoginRequest req,
                                                         HttpServletRequest request) {
        checkRate("login:" + clientIp(request));
        return ApiResponse.success(authService.loginBySms(req.getPhone(), req.getCode()));
    }

    // ================= 3. 微信扫码登录（模拟流程） =================

    /**
     * 生成扫码二维码（模拟微信侧）
     *
     * @return uuid（前端拼成二维码轮询用） + 模拟跳转 url
     */
    @PostMapping("/wx/qrcode")
    public ApiResponse<Map<String, String>> wxQrcode() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        authCodeService.initWxState(uuid);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("uuid", uuid);
        data.put("url", "https://open.weixin.qq.com/mock/oauth2?state=" + uuid);
        data.put("qrcodeContent", "MOCK-WX-LOGIN:" + uuid);
        return ApiResponse.success(data);
    }

    /**
     * 轮询扫码状态：WAITING → SCANNED → CONFIRMED(token) / EXPIRED
     */
    @GetMapping("/wx/state")
    public ApiResponse<Map<String, Object>> wxState(@RequestParam String uuid) {
        String state = authCodeService.peekWxState(uuid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", state == null ? "EXPIRED" : state);
        if (AuthCodeService.WX_CONFIRM.equals(state)) {
            AuthService.LoginResult result = authService.finalizeWxLogin(uuid);
            if (result != null) {
                data.put("token", result.token());
                data.put("username", result.username());
                data.put("realName", result.realName());
                data.put("role", result.role());
            }
        }
        return ApiResponse.success(data);
    }

    /**
     * 【答辩演示专用】模拟手机端动作：scan=模拟扫码，confirm=模拟确认登录
     */
    @PostMapping("/wx/mock/scan")
    public ApiResponse<Void> wxMockScan(@RequestParam String uuid) {
        authCodeService.markWxScanned(uuid);
        return ApiResponse.success(null);
    }

    @PostMapping("/wx/mock/confirm")
    public ApiResponse<Void> wxMockConfirm(@RequestParam String uuid) {
        authCodeService.markWxConfirmed(uuid);
        return ApiResponse.success(null);
    }

    // ---------------- 请求体 ----------------

    /** 账密登录请求 */
    @Data
    public static class PasswordLoginRequest {
        private String username;
        private String password;
        private String captchaId;
        private String captchaCode;
    }

    /** 短信发送请求 */
    @Data
    public static class SmsSendRequest {
        private String phone;
    }

    /** 短信登录请求 */
    @Data
    public static class SmsLoginRequest {
        private String phone;
        private String code;
    }

    private void checkRate(String key) {
        if (!rateLimitService.tryAcquire(key, RateLimitService.LIMIT_LOGIN)) {
            throw new RateLimitException();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() :
                request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /** 限流受控异常（统一由异常处理器输出 429） */
    public static class RateLimitException extends RuntimeException {
    }
}
