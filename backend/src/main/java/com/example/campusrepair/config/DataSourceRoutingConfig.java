package com.example.campusrepair.config;

import com.example.campusrepair.datasource.DataSourceKey;
import com.example.campusrepair.datasource.RoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离数据源配置
 *
 * @Description 仅在 app.datasource.routing-enabled=true 时生效（集群生产环境）：
 *              构建主/从两个 HikariCP 连接池并注册 {@link RoutingDataSource}；
 *              从库 URL 留空时读操作自动回落主库，便于应用先于从库上线。
 *              未启用时沿用 spring.datasource.* 单数据源，本地开发与测试不受影响。
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
@Configuration
@ConditionalOnProperty(prefix = "app.datasource", name = "routing-enabled", havingValue = "true")
public class DataSourceRoutingConfig {

    /**
     * 主库连接池，配置前缀 spring.datasource.master
     *
     * @return 主库 HikariCP 数据源
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public HikariDataSource masterDataSource() {
        return new HikariDataSource();
    }

    /**
     * 从库连接池，配置前缀 spring.datasource.slave
     *
     * @return 从库 HikariCP 数据源
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public HikariDataSource slaveDataSource() {
        return new HikariDataSource();
    }

    /**
     * 路由数据源：按 ThreadLocal 路由键在主/从连接池间选择
     *
     * @param masterDataSource 主库连接池
     * @param slaveDataSource  从库连接池
     * @return 主/从双目标的路由数据源
     */
    @Bean
    @Primary
    public RoutingDataSource dataSource(HikariDataSource masterDataSource, HikariDataSource slaveDataSource) {
        Map<Object, Object> targetDataSources = new HashMap<>(4);
        targetDataSources.put(DataSourceKey.MASTER, masterDataSource);
        // 从库未配置时读操作回落主库，保证应用可平滑上线
        targetDataSources.put(DataSourceKey.SLAVE,
                StringUtils.hasText(slaveDataSource.getJdbcUrl()) ? slaveDataSource : masterDataSource);

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }
}
