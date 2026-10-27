package com.example.campusrepair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置项
 *
 * @Description 从 application.properties 注入 OSS 连接参数，
 *              AccessKey 一律通过环境变量注入，禁止把真实密钥写进仓库
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** OSS Endpoint，如 https://oss-cn-beijing.aliyuncs.com */
    private String endpoint;

    /** Bucket 名称 */
    private String bucket;

    /** AccessKeyId（环境变量 OSS_ACCESS_KEY_ID，未配置则为空，OSS 相关接口自动不可用） */
    private String accessKeyId;

    /** AccessKeySecret（环境变量 OSS_ACCESS_KEY_SECRET，同样不入库不入仓库） */
    private String accessKeySecret;

    /** 下载签名 URL 有效期（秒），默认 30 分钟 */
    private long urlExpireSeconds = 1800;

    /** 是否已配置 OSS（密钥齐全） */
    public boolean isReady() {
        return endpoint != null && !endpoint.isBlank()
                && bucket != null && !bucket.isBlank()
                && accessKeyId != null && !accessKeyId.isBlank()
                && accessKeySecret != null && !accessKeySecret.isBlank();
    }
}
