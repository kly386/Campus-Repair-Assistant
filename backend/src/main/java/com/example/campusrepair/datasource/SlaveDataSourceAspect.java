package com.example.campusrepair.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 从库路由切面
 *
 * @Description 拦截标注了 {@link Slave} 的 Service 方法：执行前将路由键切为 SLAVE，
 *              执行完毕后清理 ThreadLocal。
 *              优先级必须高于 @Transactional（Ordered.HIGHEST_PRECEDENCE）：
 *              JPA 在事务开启时即申请连接，路由键必须先于事务拦截器设置，否则仍会连主库。
 * @Author 开璐瑶
 * @Date 2026-10-20
 * @Version 1.0
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlaveDataSourceAspect {

    /**
     * 在目标方法执行前后完成路由键的切换与清理
     *
     * @param joinPoint 被拦截的方法
     * @return 目标方法返回值
     * @throws Throwable 目标方法异常
     */
    @Around("@annotation(com.example.campusrepair.datasource.Slave)")
    public Object routeToSlave(ProceedingJoinPoint joinPoint) throws Throwable {
        DynamicDataSourceContextHolder.useSlave();
        try {
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }
}
