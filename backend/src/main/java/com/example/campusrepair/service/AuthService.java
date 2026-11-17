package com.example.campusrepair.service;

import com.example.campusrepair.dto.ClientInfo;
import com.example.campusrepair.dto.LoginResult;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.exception.ErrorCode;
import com.example.campusrepair.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 统一认证服务。
 *
 * <p>四种登录方式最终都走 {@link #completeLogin}：检查账号锁定状态、签发
 * access/refresh Token、建立可吊销会话、加载动态 RBAC 权限并写登录审计。
 * 密码错误计数和锁定使用独立事务，保证失败记录不会因 401/423 异常回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final UserRegistrationService userRegistrationService;
    private final AuthCodeService authCodeService;
    private final AccountLockService accountLockService;
    private final PermissionService permissionService;
    private final TokenSessionService tokenSessionService;
    private final AuthAuditService auditService;
    private final SmsCodeSender smsCodeSender;
    private final EmailCodeSender emailSender;
    private final WechatLoginService wechatLoginService;

    // ======================== 1. 账号密码登录 ========================

    public LoginResult loginByPassword(String username, String rawPassword,
                                       String captchaId, String captchaCode, ClientInfo client) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            throw AuthException.badRequest("用户名和密码不能为空",
                    ErrorCode.VALIDATION_ERROR, ErrorCode.CHANNEL_AUTH);
        }
        if (!authCodeService.verifyCaptcha(captchaId, captchaCode)) {
            auditService.recordLogin(null, "PASSWORD", username, client, false, "图形验证码错误或已过期");
            throw AuthException.badRequest("图形验证码错误或已过期",
                    ErrorCode.CAPTCHA_INVALID, ErrorCode.CHANNEL_AUTH);
        }

        User user = userRepository.findByUsernameAndIsDeleted(username.trim(), 0).orElse(null);
        if (user == null) {
            auditService.recordLogin(null, "PASSWORD", username, client, false, "账号或密码错误");
            throw AuthException.unauthorized("账号或密码错误");
        }

        accountLockService.assertLoginAllowed(user);
        if (!passwordMatches(user, rawPassword)) {
            AccountLockService.FailureOutcome outcome = accountLockService.recordPasswordFailure(user, client);
            auditService.recordLogin(user.getId(), "PASSWORD", username, client, false,
                    outcome.locked() ? "连续密码错误，账号锁定" : "密码错误");
            if (outcome.locked()) {
                throw AuthException.accountLocked(outcome.lockRemainingMinutes());
            }
            throw AuthException.of(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "账号或密码错误，还可尝试 " + outcome.remainingAttempts() + " 次",
                    ErrorCode.INVALID_CREDENTIALS, ErrorCode.CHANNEL_AUTH, false,
                    Map.of("remainingAttempts", outcome.remainingAttempts()));
        }

        accountLockService.clearPasswordFailures(user);
        return completeLogin(user, "PASSWORD", client);
    }

    /** 兼容历史明文密码；命中后立即升级为 BCrypt，防止后续继续泄露明文。 */
    private boolean passwordMatches(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored != null && stored.startsWith("$2")) {
            return BCrypt.checkpw(rawPassword, stored);
        }
        if (stored != null && stored.equals(rawPassword)) {
            user.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
            userRepository.save(user);
            log.info("用户 {} 的明文密码已升级为 BCrypt", user.getId());
            return true;
        }
        return false;
    }

    /**
     * 统一验证码发送入口，按前端 type 分发到手机短信或邮箱发送器。
     */
    public CodeSendResult sendVerificationCode(String type, String target) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        return switch (normalizedType) {
            case "PHONE", "SMS" -> sendSmsCode(target);
            case "EMAIL" -> sendEmailCode(target);
            default -> throw AuthException.badRequest("不支持的验证码类型: " + type,
                    ErrorCode.CODE_TYPE_UNSUPPORTED, ErrorCode.CHANNEL_AUTH);
        };
    }

    // ======================== 2. 短信验证码登录 ========================

    public CodeSendResult sendSmsCode(String phone) {
        validatePhone(phone);
        String code = authCodeService.generateSmsCode(phone);
        try {
            smsCodeSender.send(phone, code);
        } catch (RuntimeException e) {
            authCodeService.verifySmsCode(phone, code);
            throw e;
        }
        return new CodeSendResult(smsCodeSender.isMock() ? code : null, smsCodeSender.isMock());
    }

    public LoginResult loginBySms(String phone, String code, ClientInfo client) {
        validatePhone(phone);
        User user = userRepository.findByPhoneAndIsDeleted(phone, 0).orElse(null);
        if (user != null) {
            accountLockService.assertLoginAllowed(user);
        }
        if (!authCodeService.verifySmsCode(phone, code)) {
            auditService.recordLogin(user == null ? null : user.getId(), "SMS", phone,
                    client, false, "短信验证码错误或已过期");
throw AuthException.unauthorized("验证码错误或已过期", ErrorCode.CODE_INVALID,
                    ErrorCode.CHANNEL_SMS);
        }
        User resolved = user != null ? user : userRegistrationService.register(
                "sms_" + randomSuffix(), "学生-" + phone.substring(7), "STUDENT", phone, null, null);
        return completeLogin(resolved, "SMS", client);
    }

    // ======================== 3. 邮箱验证码登录 ========================

    public CodeSendResult sendEmailCode(String email) {
        validateEmail(email);
        String normalized = email.trim().toLowerCase();
        String code = authCodeService.generateEmailCode(normalized);
        try {
            emailSender.send(normalized, code);
        } catch (RuntimeException e) {
            authCodeService.verifyEmailCode(normalized, code);
            throw e;
        }
        return new CodeSendResult(emailSender.isMock() ? code : null, emailSender.isMock());
    }

    public LoginResult loginByEmail(String email, String code, ClientInfo client) {
        validateEmail(email);
        String normalized = email.trim().toLowerCase();
        User user = userRepository.findByEmailAndIsDeleted(normalized, 0).orElse(null);
        if (user != null) {
            accountLockService.assertLoginAllowed(user);
        }
        if (!authCodeService.verifyEmailCode(normalized, code)) {
            auditService.recordLogin(user == null ? null : user.getId(), "EMAIL", normalized,
                    client, false, "邮箱验证码错误或已过期");
throw AuthException.unauthorized("验证码错误或已过期", ErrorCode.CODE_INVALID,
                    ErrorCode.CHANNEL_EMAIL);
        }
        User resolved = user != null ? user : userRegistrationService.register(
                "email_" + randomSuffix(), "邮箱用户", "STUDENT", null, normalized, null);
        return completeLogin(resolved, "EMAIL", client);
    }

    // ======================== 4. 微信扫码登录 ========================

    /**
     * 扫码确认后签发 Token。consumeConfirmed 保证同一扫码会话只成功一次，
     * 后续轮询返回 USED，不会重复签发。
     */
    public LoginResult finalizeWxLogin(String uuid, ClientInfo client) {
        String openid = wechatLoginService.consumeConfirmed(uuid);
        if (!StringUtils.hasText(openid)) {
            throw AuthException.badRequest("二维码未确认、已过期或已被使用");
        }
        User user = userRepository.findByWxOpenidAndIsDeleted(openid, 0).orElse(null);
        if (user != null) {
            accountLockService.assertLoginAllowed(user);
        }
        User resolved = user != null ? user : userRegistrationService.register(
                "wx_" + randomSuffix(), "微信用户", "STUDENT", null, null, openid);
        return completeLogin(resolved, "WECHAT", client);
    }

    // ======================== 5. 刷新与退出 ========================

    public LoginResult refresh(String refreshToken, ClientInfo client) {
        if (!StringUtils.hasText(refreshToken)) {
            throw AuthException.unauthorized("刷新凭证不能为空");
        }
        TokenSessionService.RefreshedToken refreshed = tokenSessionService.refresh(refreshToken, client);
        User user = userRepository.findByIdAndIsDeleted(refreshed.userId(), 0)
                .orElseThrow(() -> AuthException.unauthorized("用户不存在或已删除"));
        accountLockService.assertLoginAllowed(user);
        PermissionService.AccessProfile profile = permissionService.loadProfile(user.getId());
        auditService.recordLogin(user.getId(), "REFRESH", user.getUsername(), client, true, null);
        return buildLoginResult(user, refreshed.accessToken(), refreshed.refreshToken(),
                refreshed.expiresIn(), profile);
    }

    public void logout(String sessionId, Long userId, ClientInfo client) {
        tokenSessionService.revoke(sessionId, "LOGOUT");
        auditService.audit("LOGOUT", userId, userId, null, "用户主动退出，会话 " + sessionId, client.ip());
    }

    // ======================== 通用内部逻辑 ========================

    private LoginResult completeLogin(User user, String loginType, ClientInfo client) {
        accountLockService.assertLoginAllowed(user);
        TokenSessionService.TokenBundle tokens = tokenSessionService.createSession(user, client);
        PermissionService.AccessProfile profile = permissionService.loadProfile(user.getId());

        user.setLastLoginAt(LocalDateTime.now());
        if (!profile.roles().isEmpty()) {
            user.setRole(profile.roles().iterator().next());
        }
        userRepository.save(user);
        auditService.recordLogin(user.getId(), loginType,
                loginType.equals("SMS") ? user.getPhone()
                        : loginType.equals("EMAIL") ? user.getEmail() : user.getUsername(),
                client, true, null);
        return buildLoginResult(user, tokens.accessToken(), tokens.refreshToken(),
                tokens.expiresIn(), profile);
    }

    private LoginResult buildLoginResult(User user, String accessToken, String refreshToken,
                                         long expiresIn, PermissionService.AccessProfile profile) {
        String primaryRole = profile.roles().stream().findFirst().orElse(user.getRole());
        return new LoginResult(accessToken, accessToken, refreshToken, "Bearer", expiresIn,
                user.getId(), user.getUsername(), user.getRealName(), primaryRole,
                List.copyOf(profile.roles()), profile.permissions(), profile.menus());
    }


    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private void validatePhone(String phone) {
        if (!StringUtils.hasText(phone) || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw AuthException.badRequest("手机号格式不正确",
                    ErrorCode.SMS_TARGET_INVALID, ErrorCode.CHANNEL_SMS);
        }
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw AuthException.badRequest("邮箱格式不正确",
                    ErrorCode.EMAIL_TARGET_INVALID, ErrorCode.CHANNEL_EMAIL);
        }
    }

    public record CodeSendResult(String mockCode, boolean mock) {
    }
}
