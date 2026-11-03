package com.example.campusrepair.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储客户端装配（MinIO = S3 兼容服务，使用 AWS S3 SDK v2 客户端）
 *
 * @Description 生成全局单例 S3Client（小文件直传 + 大文件分片）与 S3Presigner（限时签名 URL）。
 *              通过 @Conditional(MinioReadyCondition) 保证：endpoint/账号未配置完整时
 *              上下文照常启动，只是文件上传接口明确提示"MinIO 未配置"，不影响其余功能。
 *              注意不能用 @ConditionalOnProperty——它只判断属性"是否存在"而非值非空。
 *              MinIO 不支持虚拟主机样式桶地址，必须 forcePathStyle。
 * @Author 开璐瑶
 * @Date 2026-10-31
 * @Version 1.0
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    /**
     * 真·判空条件：endpoint 与 access-key 解析后均为非空白文本才算"已配置"。
     */
    static class MinioReadyCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String accessKey = context.getEnvironment().getProperty("file.minio.access-key");
            String endpoint = context.getEnvironment().getProperty("file.minio.endpoint");
            return StringUtils.hasText(accessKey) && StringUtils.hasText(endpoint);
        }
    }

    /**
     * Sigv4 规范要求 region；MinIO 惯例使用 us-east-1，实际主机由 endpointOverride 决定
     */
    private static final Region MINIO_REGION = Region.US_EAST_1;

    @Bean
    @Conditional(MinioReadyCondition.class)
    public S3Client s3Client(MinioProperties p) {
        return S3Client.builder()
                .endpointOverride(URI.create(p.getEndpoint()))
                .region(MINIO_REGION)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
                .build();
    }

    @Bean
    @Conditional(MinioReadyCondition.class)
    public S3Presigner s3Presigner(MinioProperties p) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(p.getEndpoint()))
                .region(MINIO_REGION)
                // 签名 URL 同样必须走 path-style：MinIO 无 {bucket}.{host} 虚拟主机寻址，
                // 漏开会在签名阶段得到 http://{bucket}.localhost:9000/... 的不可用 URL（404）
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
                .build();
    }
}
