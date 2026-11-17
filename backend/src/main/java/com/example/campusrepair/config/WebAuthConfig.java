package com.example.campusrepair.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 鉴权拦截配置。
 *
 * <p>只放行必要的登录入口和扫码轮询；退出、刷新之外的业务接口全部统一经过
 * {@link AuthInterceptor}。刷新接口本身不需要 access Token，但会在控制器内校验 refresh Token。</p>
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
                        "/api/auth/captcha",
                        "/api/auth/login",
                        "/api/auth/code/send",
                        "/api/auth/sms/send",
                        "/api/auth/sms/login",
                        "/api/auth/email/send",
                        "/api/auth/email/login",
                        "/api/auth/wx/qrcode",
                        "/api/auth/wx/state",
                        "/api/auth/wx/callback",
                        "/api/auth/wx/mock/**",
                        "/api/auth/refresh",
                        "/api/system/node",
                        "/error"
                );
    }
}