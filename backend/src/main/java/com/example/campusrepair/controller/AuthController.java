package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.dto.ClientInfo;
import com.example.campusrepair.dto.LoginResult;
import com.example.campusrepair.service.AuthCodeService;
import com.example.campusrepair.service.AuthService;
import com.example.campusrepair.service.RateLimitService;
import com.example.campusrepair.service.WechatLoginService;
import com.example.campusrepair.util.CaptchaGenerator;
import com.example.campusrepair.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
 * 统一登录入口。
 *
 * <p>支持账号密码、Spug 短信、邮箱验证码、微信扫码四种方式。密码登录强制图形验证码；
 * 所有登录入口按 IP 限流，短信/邮箱发送再按目标账号 60 秒冷却。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCodeService authCodeService;
    private final RateLimitService rateLimitService;
    private final WechatLoginService wechatLoginService;

    // ---------------- 图形验证码 ----------------

    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha(HttpServletRequest request) {
        rateLimitService.checkCaptcha(RequestUtils.clientIp(request));
        String code = CaptchaGenerator.randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        authCodeService.saveCaptcha(captchaId, code);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("captchaId", captchaId);
        data.put("image", CaptchaGenerator.toDataUri(code));
        return ApiResponse.success(data);
    }

    // ---------------- 统一验证码发送 ----------------

    /**
     * 前端通过 PHONE / EMAIL 选择验证码发送渠道。
     */
    @PostMapping("/code/send")
    public ApiResponse<Map<String, String>> sendCode(@Valid @RequestBody CodeSendRequest req,
                                                      HttpServletRequest request) {
        String ip = RequestUtils.clientIp(request);
        rateLimitService.checkCodeSend(req.getType(), req.getTarget(), ip);
        AuthService.CodeSendResult result = authService.sendVerificationCode(req.getType(), req.getTarget());
        Map<String, String> data = new LinkedHashMap<>();
        if (result.mockCode() != null) {
            data.put("mockCode", result.mockCode());
        }
        return ApiResponse.success("验证码已发送", data);
    }

    // ---------------- 账号密码登录 ----------------

    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody PasswordLoginRequest req,
                                          HttpServletRequest request) {
        ClientInfo client = RequestUtils.clientInfo(request);
        rateLimitService.checkLogin(client.ip());
        return ApiResponse.success(authService.loginByPassword(
                req.getUsername(), req.getPassword(), req.getCaptchaId(), req.getCaptchaCode(), client));
    }

    // ---------------- 短信登录 ----------------

    @PostMapping("/sms/send")
    public ApiResponse<Map<String, String>> sendSms(@Valid @RequestBody SmsSendRequest req,
                                                     HttpServletRequest request) {
        String ip = RequestUtils.clientIp(request);
        rateLimitService.checkSmsSend(req.getPhone(), ip);
        AuthService.CodeSendResult result = authService.sendSmsCode(req.getPhone());
        Map<String, String> data = new LinkedHashMap<>();
        if (result.mockCode() != null) {
            data.put("mockCode", result.mockCode());
        }
        return ApiResponse.success("验证码已发送", data);
    }

    @PostMapping("/sms/login")
    public ApiResponse<LoginResult> smsLogin(@Valid @RequestBody SmsLoginRequest req,
                                             HttpServletRequest request) {
        ClientInfo client = RequestUtils.clientInfo(request);
        rateLimitService.checkLogin(client.ip());
        return ApiResponse.success(authService.loginBySms(req.getPhone(), req.getCode(), client));
    }

    // ---------------- 邮箱登录 ----------------

    @PostMapping("/email/send")
    public ApiResponse<Map<String, String>> sendEmail(@Valid @RequestBody EmailSendRequest req,
                                                       HttpServletRequest request) {
        String ip = RequestUtils.clientIp(request);
        rateLimitService.checkEmailSend(req.getEmail(), ip);
        AuthService.CodeSendResult result = authService.sendEmailCode(req.getEmail());
        Map<String, String> data = new LinkedHashMap<>();
        if (result.mockCode() != null) {
            data.put("mockCode", result.mockCode());
        }
        return ApiResponse.success("验证码已发送", data);
    }

    @PostMapping("/email/login")
    public ApiResponse<LoginResult> emailLogin(@Valid @RequestBody EmailLoginRequest req,
                                               HttpServletRequest request) {
        ClientInfo client = RequestUtils.clientInfo(request);
        rateLimitService.checkLogin(client.ip());
        return ApiResponse.success(authService.loginByEmail(req.getEmail(), req.getCode(), client));
    }

    // ---------------- 微信扫码 ----------------

    @PostMapping("/wx/qrcode")
    public ApiResponse<Map<String, Object>> wxQrcode(HttpServletRequest request) {
        rateLimitService.checkCaptcha(RequestUtils.clientIp(request));
        return ApiResponse.success(wechatLoginService.createQrSession());
    }

    /**
     * 轮询扫码状态。确认后直接返回 token 字段，兼容旧前端；
     * 同时返回完整登录信息（permissions、menus、refreshToken）。
     */
    @GetMapping("/wx/state")
    public ApiResponse<Map<String, Object>> wxState(@RequestParam String uuid,
                                                     HttpServletRequest request) {
        Map<String, Object> data = wechatLoginService.poll(uuid);
        if (AuthCodeService.WX_CONFIRMED.equals(data.get("status"))) {
            LoginResult result = authService.finalizeWxLogin(uuid, RequestUtils.clientInfo(request));
            data.put("status", AuthCodeService.WX_CONFIRMED);
            data.put("token", result.token());
            data.put("accessToken", result.accessToken());
            data.put("refreshToken", result.refreshToken());
            data.put("tokenType", result.tokenType());
            data.put("expiresIn", result.expiresIn());
            data.put("userId", result.userId());
            data.put("username", result.username());
            data.put("realName", result.realName());
            data.put("role", result.role());
            data.put("roles", result.roles());
            data.put("permissions", result.permissions());
            data.put("menus", result.menus());
        }
        return ApiResponse.success(data);
    }

    /** 微信开放平台授权回调：code 换取 openid 后唤醒前端轮询。 */
    @GetMapping("/wx/callback")
    public ApiResponse<Void> wxCallback(@RequestParam String code, @RequestParam String state) {
        wechatLoginService.handleCallback(state, code);
        return ApiResponse.success("微信授权成功，请返回登录页", null);
    }

    /** 答辩演示：模拟手机端扫码。 */
    @PostMapping("/wx/mock/scan")
    public ApiResponse<Void> wxMockScan(@RequestParam String uuid) {
        wechatLoginService.markScanned(uuid);
        return ApiResponse.success(null);
    }

    /** 答辩演示：模拟手机端确认登录。 */
    @PostMapping("/wx/mock/confirm")
    public ApiResponse<Void> wxMockConfirm(@RequestParam String uuid) {
        wechatLoginService.markConfirmed(uuid);
        return ApiResponse.success(null);
    }

    // ---------------- 刷新与退出 ----------------

    @PostMapping("/refresh")
    public ApiResponse<LoginResult> refresh(@Valid @RequestBody RefreshRequest req,
                                             HttpServletRequest request) {
        return ApiResponse.success(authService.refresh(
                req.getRefreshToken(), RequestUtils.clientInfo(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(AuthContext.sessionId(), AuthContext.userId(),
                RequestUtils.clientInfo(request));
        return ApiResponse.success("已退出登录", null);
    }

    // ---------------- 请求体 ----------------

    @Data
    public static class CodeSendRequest {
        @NotBlank(message = "验证码类型不能为空")
        private String type;
        @NotBlank(message = "接收目标不能为空")
        private String target;
    }

    @Data
    public static class PasswordLoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        @NotBlank(message = "captchaId 不能为空")
        private String captchaId;
        @NotBlank(message = "图形验证码不能为空")
        private String captchaCode;
    }

    @Data
    public static class SmsSendRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
    }

    @Data
    public static class SmsLoginRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank(message = "验证码不能为空")
        private String code;
    }

    @Data
    public static class EmailSendRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class EmailLoginRequest {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank(message = "验证码不能为空")
        private String code;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "刷新 Token 不能为空")
        private String refreshToken;
    }
}
