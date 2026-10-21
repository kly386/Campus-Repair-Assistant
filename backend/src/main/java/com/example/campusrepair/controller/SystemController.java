package com.example.campusrepair.controller;

import com.example.campusrepair.service.DatabaseProbeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
 * @Description 集群部署演示与排障用：返回当前处理请求的节点主机名、端口，
 *              以及主/从数据库实例信息（验证读写分离）
 * @Author 闵娟
 * @Date 2026-10-20
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final DatabaseProbeService databaseProbeService;

    /** 读写分离开关，仅用于演示接口回显 */
    @Value("${app.datasource.routing-enabled:false}")
    private boolean routingEnabled;

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

    /**
     * 返回主/从数据库实例信息，用于验证读写分离路由是否生效。
     * 预期：master 的 read_only=false、slave 的 read_only=true，且两者 server_id 不同。
     *
     * @return 读写分离开关与主、从库实例信息
     */
    @GetMapping("/db")
    public Map<String, Object> db() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routingEnabled", routingEnabled);
        body.put("master", databaseProbeService.queryDatabaseInfo());
        body.put("slave", databaseProbeService.queryDatabaseInfoFromSlave());
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
