package com.example.campusrepair.datasource;

/**
 * 数据源路由上下文
 *
 * @Description 基于 ThreadLocal 保存当前线程的路由键；未设置时默认 MASTER，
 *              保证未标注的数据访问全部落主库（写安全优先）。
 *              切面切换路由键后必须在 finally 中调用 {@link #clear()}，
 *              避免 Tomcat 线程复用导致后续请求串库。
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
public final class DynamicDataSourceContextHolder {

    /** 线程级路由键 */
    private static final ThreadLocal<DataSourceKey> CONTEXT = new ThreadLocal<>();

    private DynamicDataSourceContextHolder() {
    }

    /**
     * 获取当前线程路由键，未设置时返回主库
     *
     * @return 当前路由键
     */
    public static DataSourceKey getKey() {
        DataSourceKey key = CONTEXT.get();
        return key == null ? DataSourceKey.MASTER : key;
    }

    /**
     * 路由到主库
     */
    public static void useMaster() {
        CONTEXT.set(DataSourceKey.MASTER);
    }

    /**
     * 路由到从库
     */
    public static void useSlave() {
        CONTEXT.set(DataSourceKey.SLAVE);
    }

    /**
     * 清空路由键，必须在 finally 中调用
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
