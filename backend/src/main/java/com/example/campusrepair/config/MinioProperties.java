package com.example.campusrepair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置项（S3 兼容开源对象存储）
 *
 * @Description 从 application.properties 注入 MinIO 连接参数。
 *              本地演示账号与 campus-minio 容器的 MINIO_ROOT_USER/PASSWORD 对齐，
 *              因此克隆仓库即可用；正式环境用环境变量 MINIO_* 覆盖，禁止将真实密钥写进仓库。
 * @Author 开璐瑶
 * @Date 2026-10-31
 * @Version 1.0
 */
@Data
@ConfigurationProperties(prefix = "file.minio")
public class MinioProperties {

    /** 服务地址，如 http://localhost:9000；局域网演示时改成本机 IP + 9000 */
    private String endpoint;

    /** 桶名称（启动时由部署脚本/控制台创建） */
    private String bucket;

    /** 用户名（容器内对齐 MINIO_ROOT_USER） */
    private String accessKey;

    /** 密码（容器内对齐 MINIO_ROOT_PASSWORD） */
    private String secretKey;

    /** 下载签名 URL 有效期（秒），默认 30 分钟 */
    private long urlExpireSeconds = 1800;

    /** 是否已具备 MinIO 客户端可用的最小配置 */
    public boolean isReady() {
        return endpoint != null && !endpoint.isBlank()
                && bucket != null && !bucket.isBlank()
                && accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
