package com.example.campusrepair.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接口频次限流（固定窗口，内存实现）
 *
 * @Description 反爬核心：按 key（一般为 IP 或 IP+接口维度）在固定 60s 窗口内计数，
 *              超阈值拒绝。进程内 AtomicLong 计数，切换分布式可直接替换为 Redis INCR+EXPIRE。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Service
public class RateLimitService {

    /** 登录类接口：每 IP 每分钟最多 10 次（防爆破/防刷验证码） */
    public static final int LIMIT_LOGIN = 10;

    /** 普通业务接口：每 IP 每分钟最多 120 次（防脚本高频刷） */
    public static final int LIMIT_API = 120;

    private record Window(long startMillis, AtomicLong counter) {
    }

    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试放行
     *
     * @param key   限流维度（如 "login:IP" 或 "api:IP"）
     * @param limit 窗口内最大次数
     * @return true=放行 / false=已超限
     */
    public boolean tryAcquire(String key, int limit) {
        long now = System.currentTimeMillis();
        long windowStart = now / 60_000 * 60_000; // 60s 固定窗口起点
        Window w = buckets.compute(key, (k, old) ->
                (old == null || old.startMillis() != windowStart)
                        ? new Window(windowStart, new AtomicLong()) : old);
        return w.counter().incrementAndGet() <= limit;
    }
}
