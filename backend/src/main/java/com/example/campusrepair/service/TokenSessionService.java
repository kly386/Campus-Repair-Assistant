package com.example.campusrepair.service;

import com.example.campusrepair.dto.ClientInfo;
import com.example.campusrepair.entity.AuthSession;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.repository.AuthSessionRepository;
import com.example.campusrepair.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 服务端 Token 会话管理。
 *
 * <p>负责签发 access/refresh Token、刷新令牌轮换、退出吊销和按用户全量吊销。
 * access Token 的 jti 必须与数据库会话一致，因此账号锁定或权限变更后可立即失效。
 * 每次刷新都会替换 refresh jti；旧 refresh Token 再次出现会被判定为重放并吊销整个会话。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenSessionService {

    private final JwtUtil jwtUtil;
    private final AuthSessionRepository sessionRepository;

    @Transactional
    public TokenBundle createSession(User user, ClientInfo client) {
        String sessionId = UUID.randomUUID().toString();
        JwtUtil.IssuedToken access = jwtUtil.issueAccessToken(user.getId(), sessionId);
        JwtUtil.IssuedToken refresh = jwtUtil.issueRefreshToken(user.getId(), sessionId);

        AuthSession session = new AuthSession();
        session.setId(sessionId);
        session.setUserId(user.getId());
        session.setAccessJti(access.jti());
        session.setRefreshJti(refresh.jti());
        session.setAccessExpiresAt(access.expiresAt());
        session.setRefreshExpiresAt(refresh.expiresAt());
        session.setClientFingerprint(client.fingerprint());
        session.setIp(client.ip());
        session.setUserAgent(client.userAgent());
        sessionRepository.save(session);
        return new TokenBundle(sessionId, access.token(), refresh.token(),
                jwtUtil.accessExpireSeconds(), user.getId());
    }

    /**
     * 校验访问令牌对应的服务端会话，同时验证客户端指纹，防止 Token 被复制到其他终端使用。
     */
    @Transactional(readOnly = true)
    public CurrentSession validateAccess(Claims claims, String fingerprint) {
        if (!JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw AuthException.unauthorized("Token 类型错误");
        }
        String sessionId = claims.get("sid", String.class);
        AuthSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AuthException.unauthorized("登录会话不存在"));
        if (session.isRevoked() || session.getAccessExpiresAt().isBefore(LocalDateTime.now())) {
            throw AuthException.unauthorized("登录已失效，请重新登录");
        }
        if (!session.getAccessJti().equals(claims.getId())
                || !session.getUserId().equals(Long.valueOf(claims.getSubject()))) {
            throw AuthException.unauthorized("登录凭证已失效");
        }
        if (!session.getClientFingerprint().equals(fingerprint)) {
            throw AuthException.unauthorized("客户端指纹不一致，请重新登录");
        }
        return new CurrentSession(sessionId, session.getUserId());
    }

    /** 刷新 Token 轮换：旧 refresh jti 失效，新 Token 对写回同一会话。 */
    @Transactional(noRollbackFor = AuthException.class)
    public RefreshedToken refresh(String refreshToken, ClientInfo client) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (claims == null || !JwtUtil.TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw AuthException.unauthorized("刷新凭证无效或已过期");
        }
        String sessionId = claims.get("sid", String.class);
        AuthSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> AuthException.unauthorized("登录会话不存在"));
        if (session.isRevoked() || session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw AuthException.unauthorized("登录已过期，请重新登录");
        }
        if (!session.getClientFingerprint().equals(client.fingerprint())) {
            throw AuthException.unauthorized("客户端指纹不一致，请重新登录");
        }
        if (!session.getRefreshJti().equals(claims.getId())) {
            // 已轮换过的旧 refresh Token 被重放：吊销整条会话。
            session.setRevokedAt(LocalDateTime.now());
            session.setRevokedReason("REFRESH_TOKEN_REPLAY");
            throw AuthException.unauthorized("检测到刷新凭证重放，请重新登录");
        }
        Long userId = Long.valueOf(claims.getSubject());
        JwtUtil.IssuedToken access = jwtUtil.issueAccessToken(userId, sessionId);
        JwtUtil.IssuedToken refresh = jwtUtil.issueRefreshToken(userId, sessionId);
        session.setAccessJti(access.jti());
        session.setRefreshJti(refresh.jti());
        session.setAccessExpiresAt(access.expiresAt());
        session.setRefreshExpiresAt(refresh.expiresAt());
        session.setIp(client.ip());
        session.setUserAgent(client.userAgent());
        return new RefreshedToken(sessionId, userId, access.token(), refresh.token(),
                jwtUtil.accessExpireSeconds());
    }

    @Transactional
    public void revoke(String sessionId, String reason) {
        sessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> {
            if (!session.isRevoked()) {
                session.setRevokedAt(LocalDateTime.now());
                session.setRevokedReason(reason);
            }
        });
    }

    @Transactional
    public int revokeAll(Long userId, String reason) {
        return sessionRepository.revokeAllByUserId(userId, LocalDateTime.now(), reason);
    }

    public record TokenBundle(String sessionId, String accessToken, String refreshToken,
                              long expiresIn, Long userId) {
    }

    public record RefreshedToken(String sessionId, Long userId, String accessToken,
                                 String refreshToken, long expiresIn) {
    }

    public record CurrentSession(String sessionId, Long userId) {
    }
}