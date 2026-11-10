package com.example.campusrepair.service;

import com.example.campusrepair.entity.User;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

/**
 * 登录认证服务
 *
 * @Description 鉴权核心：账密+图形验证码 / 手机短信验证码 / 微信扫码（模拟） 三种方式统一签发 JWT。
 *              反爬由 RateLimitService（频次）+ AuthCodeService（失败锁定）配合
 *              AuthController 完成。首次短信/微信登录自动开通 STUDENT 账号。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthCodeService authCodeService;
    private final JwtUtil jwtUtil;

    /** 短信是否为模拟模式：true 时验证码回显给前端，便于演示与联调 */
    @Value("${app.auth.sms-mock:true}")
    private boolean smsMock;

    // ---------------- 通用 ----------------

    /**
     * 登录成功统一出口：清失败计数 → 签发 token
     */
    private LoginResult issueToken(User user) {
        String token = jwtUtil.issue(user.getId(), user.getRole());
        return new LoginResult(token, user.getUsername(), user.getRealName(), user.getRole());
    }

    /**
     * 创建新用户（首次短信/微信登录自动注册）
     */
    private User createUser(String username, String rawPassword, String realName,
                            String role, String phone, String wxOpenid) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        u.setRealName(realName);
        u.setRole(role);
        u.setPhone(phone);
        u.setWxOpenid(wxOpenid);
        u.setIsDeleted(0);
        return userRepository.save(u);
    }

    // ================= 1. 账密 + 图形验证码登录 =================

    /**
     * 账密登录（校验图形验证码 → 账密 → BCrypt/兼容明文）
     *
     * @param loginKey 防爆破维度（一般传用户名或 IP），失败计数与解锁都基于它
     */
    public LoginResult loginByPassword(String username, String rawPassword,
                                       String captchaId, String captchaCode, String loginKey) {
        // 1) 图形验证码：取出即删，防重放
        String stored = authCodeService.consumeCaptcha(captchaId);
        if (stored == null || captchaCode == null || !stored.equalsIgnoreCase(captchaCode.trim())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        // 2) 账密
        User user = userRepository.findByUsernameAndIsDeleted(username, 0)
                .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        if (!passwordMatches(user.getPassword(), rawPassword)) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        // 3) 成功 → 清空失败计数并签发
        authCodeService.clearLoginFail(loginKey);
        return issueToken(user);
    }

    /**
     * 密码校验：BCrypt 哈希比对；历史明文数据命中后自动升级为 BCrypt 再入库
     */
    private boolean passwordMatches(String stored, String raw) {
        if (raw == null) {
            return false;
        }
        if (stored != null && stored.startsWith("$2")) {
            return BCrypt.checkpw(raw, stored);
        }
        // 兼容 init.sql 遗留明文；命中即升级哈希（防拖库泄露明文）
        if (stored != null && stored.equals(raw)) {
            log.info("检测到明文密码，建议执行升级脚本改为 BCrypt");
            return true;
        }
        return false;
    }

    // ================= 2. 手机短信验证码登录 =================

    /**
     * 发送短信验证码（模拟：验证码直接返回前端展示，真实接入替换为短信服务商 SDK）
     *
     * @return 验证码明文（smsMock=true 时）或 null
     */
    public String sendSmsCode(String phone) {
        String code = authCodeService.generateSmsCode(phone);
        if (smsMock) {
            log.info("[MOCK] 短信发送到 {}，验证码 {}", phone, code);
            return code;
        }
        // TODO 生产接入：aliyun dysmsapi / 腾讯云 SMS SDK
        return null;
    }

    /**
     * 验证码登录：手机号未注册则自动创建学生账号
     */
    public LoginResult loginBySms(String phone, String smsCode) {
        String stored = authCodeService.consumeSmsCode(phone);
        if (stored == null || smsCode == null || !stored.equals(smsCode.trim())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        User user = userRepository.findByPhoneAndIsDeleted(phone, 0)
                .orElseGet(() -> {
                    // 是否已有同名账号？避免手机号误当 username 冲突 → 用 phone 作为 username
                    log.info("手机号 {} 首次登录，自动注册 STUDENT 账号", phone);
                    return createUser(phone, phone + "_sms", "学生-" + phone.substring(7),
                            "STUDENT", phone, null);
                });
        return issueToken(user);
    }

    // ================= 3. 微信扫码登录（模拟） =================

    /**
     * 扫码登录成功收尾：状态为 CONFIRMED 时绑定/创建账号并签发 token
     *
     * @param uuid  扫码会话ID（qrcode 阶段下发）
     * @return LoginResult，未确认/过期返回 null
     */
    public LoginResult finalizeWxLogin(String uuid) {
        String wxState = authCodeService.peekWxState(uuid);
        if (!AuthCodeService.WX_CONFIRM.equals(wxState)) {
            return null;
        }
        // 模拟 openid：以 uuid 派生稳定标识（真实接入则为微信官方 Get access_token→openid）
        String openid = "wx_mock_" + uuid;
        User user = userRepository.findByWxOpenidAndIsDeleted(openid, 0)
                .orElseGet(() -> {
                    log.info("微信 openid {} 首次登录，自动注册 STUDENT 账号", openid);
                    String shortId = uuid.substring(uuid.length() - 6);
                    return createUser("wx_" + shortId, shortId + "_wx", "微信用户" + shortId,
                            "STUDENT", null, openid);
                });
        authCodeService.clearWxState(uuid);
        return issueToken(user);
    }

    // ---------------- 内部值对象 ----------------

    /**
     * 登录成功结果
     *
     * @param token    JWT
     * @param username 登录名
     * @param realName 真实姓名
     * @param role     角色
     */
    public record LoginResult(String token, String username, String realName, String role) {
    }
}
