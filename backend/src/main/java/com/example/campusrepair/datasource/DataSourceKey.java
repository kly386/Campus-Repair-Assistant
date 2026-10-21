package com.example.campusrepair.datasource;

/**
 * 数据源路由键
 *
 * @Description 读写分离场景下的数据源标识：MASTER 承担写与默认访问，SLAVE 承担只读查询
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
public enum DataSourceKey {

    /** 主库：写操作与默认访问 */
    MASTER,

    /** 从库：只读查询 */
    SLAVE
}
