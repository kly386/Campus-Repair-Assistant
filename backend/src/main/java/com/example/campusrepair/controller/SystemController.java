package com.example.campusrepair.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统信息接口
 *
 * @Description 集群部署演示与排障用：返回当前处理请求的节点主机名与端口
 * @Author 闵娟
 * @Date 2026-10-20
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    /**
     * 返回当前节点信息，用于验证 Nginx 轮询与故障转移。
     *
     * @param request 当前请求
     * @return 主机名、本地端口、当前时间
     */
    @GetMapping("/node")
    public Map<String, Object> node(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hostname", resolveHostname());
        body.put("localPort", request.getLocalPort());
        body.put("time", OffsetDateTime.now().toString());
        return body;
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
