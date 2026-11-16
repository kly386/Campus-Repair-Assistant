package com.example.campusrepair.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 鉴权拦截配置
 *
 * @Description 拦截 /api/**，白名单仅放行 登录入口 /api/auth/** 与运维健康检查 /api/system/**。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@Configuration
@RequiredArgsConstructor
public class WebAuthConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",   // 登录入口
                        "/api/system/**"  // 运维健康检查（集群/读写分离验证）
                );
    }
}
