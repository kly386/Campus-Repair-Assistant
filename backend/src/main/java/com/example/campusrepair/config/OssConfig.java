package com.example.campusrepair.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端装配
 *
 * @Description 生成全局单例 OSS 客户端 Bean。通过 @ConditionalOnProperty 保证：
 *              未配置密钥时（如同学克隆仓库后未设环境变量）上下文照常启动，
 *              只是文件上传接口明确提示"OSS 未配置"，不影响其余功能。
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "access-key-id")
    public OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }
}
