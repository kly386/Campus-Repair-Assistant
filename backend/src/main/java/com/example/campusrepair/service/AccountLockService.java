package com.example.campusrepair.service;

import com.example.campusrepair.dto.ClientInfo;
import com.example.campusrepair.entity.LoginLock;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.exception.ErrorCode;
import com.example.campusrepair.repository.LoginLockRepository;
import com.example.campusrepair.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 账号锁定风控。
 *
 * <p>密码连续错误达到阈值后，账号在锁定期间禁止密码、短信、邮箱、微信所有登录方式；
 * 同时吊销该用户全部 JWT 会话。锁定剩余分钟数通过 423 响应返回前端。
 * 失败计数和锁定状态落库，重启服务后仍有效。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final LoginLockRepository loginLockRepository;
    private final UserRepository userRepository;
    private final TokenSessionService tokenSessionService;
    private final AuthAuditService auditService;

    @Value("${app.auth.max-login-fail:5}")
    private int maxLoginFail;

    @Value("${app.auth.lock-minutes:10}")
    private long lockMinutes;

    /**
     * 所有登录方式在完成身份识别后都必须调用本方法。
     * 锁定到期会在此自动解锁并写入审计日志。
     */
    @Transactional(noRollbackFor = AuthException.class)
    public void assertLoginAllowed(User user) {
        if (user == null) {
            return;
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw AuthException.forbidden("账号已被禁用，请联系管理员",
                    ErrorCode.ACCOUNT_DISABLED, ErrorCode.CHANNEL_AUTH);
        }
        LoginLock lock = loginLockRepository.findByUserIdAndLockTypeForUpdate(
                user.getId(), LoginLock.TYPE_PASSWORD).orElse(null);
        if (lock == null || !LoginLock.STATUS_ACTIVE.equals(lock.getStatus())) {
            if ("LOCKED".equals(user.getStatus())) {
                throw AuthException.of(org.springframework.http.HttpStatus.LOCKED,
                        "账号已被锁定，请联系管理员", ErrorCode.ACCOUNT_LOCKED,
                        ErrorCode.CHANNEL_AUTH, false, null);
            }
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (lock.getUnlockAt() == null || !lock.getUnlockAt().isAfter(now)) {
            releaseLock(lock, user, "LOCK_EXPIRED", null, null);
            return;
        }
        throw AuthException.accountLocked(remainingMinutes(lock.getUnlockAt()));
    }

    /**
     * 记录一次密码错误。使用行锁串行化并发失败请求，避免“并发 10 次只记 1 次”的漏洞。
     * 本方法不抛业务异常，让调用方在事务提交后再决定返回 401 还是 423。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureOutcome recordPasswordFailure(User user, ClientInfo client) {
        LoginLock lock = loginLockRepository.findByUserIdAndLockTypeForUpdate(
                        user.getId(), LoginLock.TYPE_PASSWORD)
                .orElseGet(() -> {
                    LoginLock created = new LoginLock();
                    created.setUserId(user.getId());
                    created.setLockType(LoginLock.TYPE_PASSWORD);
                    created.setStatus(LoginLock.STATUS_FAILING);
                    created.setFailCount(0);
                    created.setFirstFailedAt(LocalDateTime.now());
                    return created;
                });

        if (LoginLock.STATUS_RELEASED.equals(lock.getStatus())) {
            lock.setFailCount(0);
            lock.setFirstFailedAt(LocalDateTime.now());
            lock.setLockedAt(null);
            lock.setUnlockAt(null);
            lock.setReleasedAt(null);
            lock.setReleaseReason(null);
            lock.setStatus(LoginLock.STATUS_FAILING);
        }

        int failCount = lock.getFailCount() + 1;
        lock.setFailCount(failCount);
        lock.setStatus(failCount >= maxLoginFail ? LoginLock.STATUS_ACTIVE : LoginLock.STATUS_FAILING);
        if (failCount >= maxLoginFail) {
            LocalDateTime now = LocalDateTime.now();
            lock.setLockedAt(now);
            lock.setUnlockAt(now.plusMinutes(lockMinutes));
            user.setStatus("LOCKED");
            userRepository.save(user);
            int revoked = tokenSessionService.revokeAll(user.getId(), "ACCOUNT_LOCKED");
            auditService.audit("ACCOUNT_LOCKED", null, user.getId(), lock.getId(),
                    "连续密码错误 " + failCount + " 次，锁定 " + lockMinutes
                            + " 分钟，吊销会话 " + revoked + " 个",
                    client == null ? null : client.ip());
        }
        LoginLock saved = loginLockRepository.save(lock);
        return new FailureOutcome(failCount, Math.max(0, maxLoginFail - failCount),
                failCount >= maxLoginFail,
                lock.getUnlockAt() == null ? 0 : remainingMinutes(lock.getUnlockAt()),
                saved.getId());
    }

    /** 成功登录后清除失败累计，不把之前的历史失败带入下一次风控窗口。 */
    @Transactional
    public void clearPasswordFailures(User user) {
        loginLockRepository.findByUserIdAndLockTypeForUpdate(user.getId(), LoginLock.TYPE_PASSWORD)
                .ifPresent(lock -> {
                    if (LoginLock.STATUS_FAILING.equals(lock.getStatus())) {
                        lock.setStatus(LoginLock.STATUS_RELEASED);
                        lock.setFailCount(0);
                        lock.setReleasedAt(LocalDateTime.now());
                        lock.setReleaseReason("LOGIN_SUCCESS");
                    }
                });
    }

    /** 管理员立即锁定用户，并让已下发 Token 全部失效。 */
    @Transactional
    public void lockUser(Long targetUserId, long minutes, Long operatorId, String ip) {
        User user = userRepository.findByIdAndIsDeleted(targetUserId, 0)
                .orElseThrow(() -> AuthException.badRequest("用户不存在"));
        LoginLock lock = loginLockRepository.findByUserIdAndLockTypeForUpdate(
                        targetUserId, LoginLock.TYPE_PASSWORD)
                .orElseGet(() -> {
                    LoginLock created = new LoginLock();
                    created.setUserId(targetUserId);
                    created.setLockType(LoginLock.TYPE_PASSWORD);
                    return created;
                });
        LocalDateTime now = LocalDateTime.now();
        lock.setStatus(LoginLock.STATUS_ACTIVE);
        lock.setFailCount(Math.max(lock.getFailCount(), maxLoginFail));
        lock.setFirstFailedAt(lock.getFirstFailedAt() == null ? now : lock.getFirstFailedAt());
        lock.setLockedAt(now);
        lock.setUnlockAt(now.plusMinutes(minutes));
        lock.setReleasedAt(null);
        lock.setReleaseReason(null);
        loginLockRepository.save(lock);
        user.setStatus("LOCKED");
        userRepository.save(user);
        int revoked = tokenSessionService.revokeAll(targetUserId, "ACCOUNT_LOCKED_BY_ADMIN");
        auditService.audit("ACCOUNT_LOCKED_BY_ADMIN", operatorId, targetUserId, lock.getId(),
                "管理员锁定 " + minutes + " 分钟，吊销会话 " + revoked + " 个", ip);
    }

    /** 管理员手动解锁。 */
    @Transactional
    public void unlockUser(Long targetUserId, Long operatorId, String ip) {
        User user = userRepository.findByIdAndIsDeleted(targetUserId, 0)
                .orElseThrow(() -> AuthException.badRequest("用户不存在"));
        LoginLock lock = loginLockRepository.findByUserIdAndLockTypeForUpdate(
                targetUserId, LoginLock.TYPE_PASSWORD)
                .orElseThrow(() -> AuthException.badRequest("账号当前没有锁定记录"));
        releaseLock(lock, user, "UNLOCKED_BY_ADMIN", operatorId, ip);
    }

    private void releaseLock(LoginLock lock, User user, String reason, Long operatorId, String ip) {
        lock.setStatus(LoginLock.STATUS_RELEASED);
        lock.setFailCount(0);
        lock.setReleasedAt(LocalDateTime.now());
        lock.setReleaseReason(reason);
        user.setStatus("ACTIVE");
        userRepository.save(user);
        auditService.audit("ACCOUNT_UNLOCKED", operatorId, user.getId(), lock.getId(), reason, ip);
    }

    private long remainingMinutes(LocalDateTime unlockAt) {
        long seconds = Duration.between(LocalDateTime.now(), unlockAt).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    public record FailureOutcome(int failCount, int remainingAttempts, boolean locked,
                                 long lockRemainingMinutes, Long lockId) {
    }
}