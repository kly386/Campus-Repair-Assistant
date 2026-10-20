package com.example.campusrepair.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态路由数据源
 *
 * @Description 按 {@link DynamicDataSourceContextHolder} 中的路由键在主/从
 *              HikariCP 连接池之间选择连接；默认目标为主库，写安全优先。
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDataSourceContextHolder.getKey();
    }
}
