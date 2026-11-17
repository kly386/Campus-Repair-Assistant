package com.example.campusrepair.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类。
 *
 * <p>访问令牌与刷新令牌分离：Token 中只放 userId、会话 ID、jti 和类型，
 * 权限不写入 Token，而是每次请求从数据库动态加载。这样角色/权限调整后立即生效。
 * 服务端 {@code auth_session} 保存 jti，可主动吊销、刷新令牌轮换并识别旧刷新令牌重放。</p>
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String ISSUER = "campus-repair";

    private final SecretKey key;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.access-minutes:30}") long accessMinutes,
                   @Value("${app.jwt.refresh-days:7}") long refreshDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT 密钥至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessExpireSeconds = accessMinutes * 60;
        this.refreshExpireSeconds = refreshDays * 24 * 60 * 60;
    }

    public IssuedToken issueAccessToken(Long userId, String sessionId) {
        return issue(userId, sessionId, TYPE_ACCESS, accessExpireSeconds);
    }

    public IssuedToken issueRefreshToken(Long userId, String sessionId) {
        return issue(userId, sessionId, TYPE_REFRESH, refreshExpireSeconds);
    }

    private IssuedToken issue(Long userId, String sessionId, String type, long expireSeconds) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expireSeconds);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("sid", sessionId)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti,
                LocalDateTime.ofInstant(expiration, ZoneId.systemDefault()));
    }

    /** 解析并验证签名/有效期/签发者；失败返回 null，由调用方统一返回 401。 */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long remainingSeconds(Claims claims) {
        return Math.max(0, (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000);
    }

    public long accessExpireSeconds() {
        return accessExpireSeconds;
    }

    public record IssuedToken(String token, String jti, LocalDateTime expiresAt) {
    }
}