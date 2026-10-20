package com.example.campusrepair.service;

import java.util.Map;

/**
 * 数据库节点探测服务接口
 *
 * @Description 读写分离演示与排障用：查询当前所连数据库实例的自身信息，
 *              据 hostname / server_id / read_only 判断读写路由是否生效
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
public interface DatabaseProbeService {

    /**
     * 查询数据库实例信息（默认路由到主库）
     *
     * @return hostname、serverId、readOnly、version
     */
    Map<String, Object> queryDatabaseInfo();

    /**
     * 强制经从库路由查询数据库实例信息
     *
     * @return 同上，结果来自从库
     */
    Map<String, Object> queryDatabaseInfoFromSlave();
}
