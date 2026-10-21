package com.example.campusrepair.service.impl;

import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.service.DatabaseProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库节点探测服务实现
 *
 * @Description 通过 SELECT @@hostname 等系统变量识别实际连到的数据库实例；
 *              从库配置了 read_only=ON，可据此验证读写分离是否生效
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
@Service
@RequiredArgsConstructor
public class DatabaseProbeServiceImpl implements DatabaseProbeService {

    /** 探测 SQL：hostname/server_id 标识实例，read_only 标识读写角色 */
    private static final String PROBE_SQL =
            "SELECT @@hostname AS hostname, @@server_id AS serverId, "
                    + "@@read_only AS readOnly, @@version AS version";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> queryDatabaseInfo() {
        return probe();
    }

    @Override
    @Slave
    public Map<String, Object> queryDatabaseInfoFromSlave() {
        return probe();
    }

    /**
     * 执行探测 SQL 并整理为有序结果
     *
     * @return 数据库实例信息
     */
    private Map<String, Object> probe() {
        Map<String, Object> row = jdbcTemplate.queryForMap(PROBE_SQL);
        Map<String, Object> info = new LinkedHashMap<>(4);
        info.put("hostname", row.get("hostname"));
        info.put("serverId", row.get("serverId"));
        info.put("readOnly", row.get("readOnly"));
        // version 统一转字符串输出，避免不同驱动返回类型不一致
        info.put("version", String.valueOf(row.get("version")));
        return info;
    }
}
