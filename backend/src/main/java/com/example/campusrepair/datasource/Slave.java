package com.example.campusrepair.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 从库读取标记
 *
 * @Description 标注在 Service 方法上，由 {@link SlaveDataSourceAspect} 将其数据库访问路由到从库。
 *              仅可标注只读方法；主从复制存在延迟，"写后立读"的场景不要使用。
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Slave {
}
