package com.example.campusrepair.util;

import com.example.campusrepair.dto.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 从请求中提取风控所需信息，并生成稳定的客户端指纹。 */
public final class RequestUtils {

    private RequestUtils() {
    }

    /**
     * 获取客户端 IP。生产环境由 Spring ForwardedHeaderFilter 依据可信代理配置处理
     * X-Forwarded-For，避免业务代码直接信任可伪造的请求头。
     */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    /** 客户端主动上报指纹优先；未上报时用 UA/Accept-Language 生成降级指纹。 */
    public static ClientInfo clientInfo(HttpServletRequest request) {
        String userAgent = truncate(request.getHeader("User-Agent"), 500);
        String supplied = request.getHeader("X-Client-Fingerprint");
        String source = supplied != null && !supplied.isBlank()
                ? supplied
                : request.getHeader("User-Agent") + "|" + request.getHeader("Accept-Language");
        return new ClientInfo(clientIp(request), userAgent, sha256(source));
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}