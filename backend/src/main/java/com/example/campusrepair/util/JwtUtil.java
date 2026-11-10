package com.example.campusrepair.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具
 *
 * @Description 签发/解析 HMAC HS256 JWT。payload：sub=用户ID、role=角色。
 *              鉴权要点：令牌无状态，服务端不存 session，适合 Tomcat 集群多节点共享校验。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-minutes:120}") long expireMinutes) {
        // HS256 要求密钥至少 32 字节，配置基座在 application.properties
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMinutes * 60_000;
    }

    /**
     * 签发 token
     *
     * @param userId 用户ID
     * @param role   角色
     * @return JWT 字符串
     */
    public String issue(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 token
     *
     * @param token JWT 字符串
     * @return claims（含 sub/role），无效或过期返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** token 剩余有效期毫秒数（-1 表示无所谓/不适用） */
    public long remainMillis(Claims claims) {
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }
}
