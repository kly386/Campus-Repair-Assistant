package com.example.campusrepair.dto;

/** 登录风控和会话绑定所需的客户端信息。 */
public record ClientInfo(String ip, String userAgent, String fingerprint) {
}