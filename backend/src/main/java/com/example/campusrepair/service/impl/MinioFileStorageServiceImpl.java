package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.MinioProperties;
import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.entity.FileObject;
import com.example.campusrepair.repository.FileObjectRepository;
import com.example.campusrepair.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储服务实现（MinIO 版 = S3 兼容协议，AWS S3 SDK v2 客户端）
 *
 * @Description 大文件分片上传使用 S3 原生 Multipart Upload：
 *              CreateMultipartUpload / UploadPart / ListParts / CompleteMultipartUpload，
 *              后端接收"一支分片"就立刻转发 MinIO，内存占用恒定为单分片大小。
 *              小文件批量上传则把 N 个对象一次 putObject 后做批量元数据落库。
 *              与原 OSS 版的差异：分片会话以 file_object 表为权威存放
 *              （init 即落一行 file_type='multipart' / file_size 为空的记录），
 *              内存 Map 仅作加速缓存——双 Tomcat 集群/进程重启不再丢会话。
 * @Author 开璐瑶
 * @Date 2026-10-31
 * @Version 1.0
 */
@Slf4j
@Service
public class MinioFileStorageServiceImpl implements FileStorageService {

    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024; // 单分片上限 10MB（演示收紧）
    private static final int MAX_PART_NUMBER = 10000;             // S3 协议：分片编号 1~10000
    private static final String TYPE_SMALL = "small";
    private static final String TYPE_MULTIPART = "multipart";

    private final MinioProperties minioProperties;
    private final FileObjectRepository fileObjectRepository;
    private final JdbcTemplate jdbcTemplate;

    /** 客户端可选注入：容器未运行/配置缺失时为 null，接口层给出明确提示而不是启动失败 */
    @Autowired(required = false)
    private S3Client s3Client;

    @Autowired(required = false)
    private S3Presigner s3Presigner;

    /** 会话缓存：uploadId -> objectKey。只是 file_object 表的前置缓存，重启/切实例可从库恢复 */
    private final Map<String, String> multipartSessions = new ConcurrentHashMap<>();

    /** 桶自检标记：进程内只做一次（HeadBucket），组员新机器上桶不存在时自动创建 */
    private volatile boolean bucketChecked = false;

    public MinioFileStorageServiceImpl(MinioProperties minioProperties,
                                       FileObjectRepository fileObjectRepository,
                                       JdbcTemplate jdbcTemplate) {
        this.minioProperties = minioProperties;
        this.fileObjectRepository = fileObjectRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** ============================== 小文件：批量上传 ============================== */

    @Override
    public FileObject uploadSmall(MultipartFile file, Long orderId) {
        return uploadSmallBatch(List.of(file), orderId).get(0);
    }

    @Override
    public List<FileObject> uploadSmallBatch(List<MultipartFile> files, Long orderId) {
        requireMinioReady();
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未收到任何文件");
        }
        // 1. 逐个 putObject（小文件整体一次读入，内存占用 = 单文件大小，可控）
        List<FileObject> metas = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String objectKey = buildObjectKey(orderId, file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(minioProperties.getBucket())
                                .key(objectKey)
                                .contentType(file.getContentType())
                                .contentLength(file.getSize())
                                .build(),
                        RequestBody.fromInputStream(in, file.getSize()));
            } catch (Exception e) {
                throw translate(e, "MinIO 上传失败");
            }
            FileObject meta = new FileObject();
            meta.setFileName(file.getOriginalFilename());
            meta.setObjectKey(objectKey);
            meta.setFileSize(file.getSize());
            meta.setContentType(file.getContentType());
            meta.setFileType(TYPE_SMALL);
            meta.setOrderId(orderId);
            metas.add(meta);
        }
        if (metas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "全部文件均为空文件");
        }
        // 2. 元数据批量落库：一条多行 VALUES 语句写 N 行，而不是 N 次单行提交
        return batchInsert(metas);
    }

    /** 批量写元数据：拼一条"N 行 VALUES + 参数化"的 INSERT，单次往返写入 */
    private List<FileObject> batchInsert(List<FileObject> metas) {
        LocalDateTime now = LocalDateTime.now();
        String row = "(?,?,?,?,?,?,?,?,?)";
        String placeholders = String.join(",", Collections.nCopies(metas.size(), row));
        String sql = "INSERT INTO file_object "
                + "(file_name, object_key, file_size, content_type, md5, file_type, order_id, upload_id, gmt_create) "
                + "VALUES " + placeholders;

        Object[] params = new Object[metas.size() * 9];
        for (int i = 0; i < metas.size(); i++) {
            FileObject m = metas.get(i);
            m.setGmtCreate(now);
            int b = i * 9;
            params[b] = m.getFileName();
            params[b + 1] = m.getObjectKey();
            params[b + 2] = m.getFileSize();
            params[b + 3] = m.getContentType();
            params[b + 4] = m.getMd5();
            params[b + 5] = m.getFileType();
            params[b + 6] = m.getOrderId();
            params[b + 7] = m.getUploadId();
            params[b + 8] = m.getGmtCreate();
        }
        jdbcTemplate.update(sql, params);

        // 按 objectKey 把自增主键查回来（比"推进 AUTO_INCREMENT 值"的估算方式可靠）
        String keys = String.join(",", Collections.nCopies(metas.size(), "?"));
        List<Map<String, Object>> idRows = jdbcTemplate.queryForList(
                "SELECT id, object_key FROM file_object WHERE object_key IN (" + keys + ")",
                metas.stream().map(FileObject::getObjectKey).toArray());
        Map<String, Long> keyToId = new HashMap<>();
        for (Map<String, Object> r : idRows) {
            keyToId.put((String) r.get("object_key"), ((Number) r.get("id")).longValue());
        }
        metas.forEach(m -> m.setId(keyToId.get(m.getObjectKey())));
        return metas;
    }

    /** ============================== 大文件：分片上传 ============================== */

    @Override
    public Map<String, String> initMultipartUpload(String fileName, Long orderId) {
        requireMinioReady();
        String objectKey = buildObjectKey(orderId, fileName);
        try {
            CreateMultipartUploadResponse init = s3Client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(minioProperties.getBucket())
                            .key(objectKey)
                            .build());
            String uploadId = init.uploadId();
            multipartSessions.put(uploadId, objectKey);

            // 会话落库（升级点）：init 即占一行记录，uploadId 反查 objectKey 由此还原，
            // order_id 留空、file_size 留空，合并时更新——上传中的半成品不会混进工单列表
            jdbcTemplate.update(
                    "INSERT INTO file_object (file_name, object_key, file_size, content_type, md5, "
                            + "file_type, order_id, upload_id, gmt_create) VALUES (?,?,?,?,?,?,?,?,?)",
                    fileName, objectKey, null, null, null, TYPE_MULTIPART, null, uploadId, LocalDateTime.now());

            log.info("初始化分片上传: uploadId={}, objectKey={}", uploadId, objectKey);
            Map<String, String> result = new HashMap<>();
            result.put("uploadId", uploadId);
            result.put("objectKey", objectKey);
            return result;
        } catch (Exception e) {
            throw translate(e, "初始化分片上传失败");
        }
    }

    /** 会话解析：内存缓存优先，未命中从 file_object 表反查（集群/重启场景），结果回填缓存 */
    private String resolveObjectKey(String uploadId) {
        String objectKey = multipartSessions.get(uploadId);
        if (objectKey != null) {
            return objectKey;
        }
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT object_key FROM file_object WHERE upload_id = ? AND file_type = ? "
                        + "AND file_size IS NULL LIMIT 1",
                String.class, uploadId, TYPE_MULTIPART);
        if (keys.isEmpty()) {
            return null;
        }
        objectKey = keys.get(0);
        multipartSessions.put(uploadId, objectKey);
        return objectKey;
    }

    @Override
    public Map<String, Object> uploadMultipartChunk(String uploadId, int chunkIndex, MultipartFile chunk) {
        requireMinioReady();
        String objectKey = resolveObjectKey(uploadId);
        if (objectKey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分片会话不存在或已过期: " + uploadId);
        }
        if (chunkIndex < 1 || chunkIndex > MAX_PART_NUMBER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "分片编号越界（S3 协议要求 1~" + MAX_PART_NUMBER + "）: " + chunkIndex);
        }
        if (chunk.getSize() > MAX_CHUNK_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单分片超过 10MB 限制");
        }
        try (InputStream in = chunk.getInputStream()) {
            // S3 元件编号从 1 开始，同一 uploadId 内必须唯一
            UploadPartResponse part = s3Client.uploadPart(
                    UploadPartRequest.builder()
                            .bucket(minioProperties.getBucket())
                            .key(objectKey)
                            .uploadId(uploadId)
                            .partNumber(chunkIndex)
                            .build(),
                    RequestBody.fromInputStream(in, chunk.getSize()));
            Map<String, Object> result = new HashMap<>();
            result.put("chunkIndex", chunkIndex);
            result.put("etag", part.eTag());
            result.put("size", chunk.getSize());
            return result;
        } catch (Exception e) {
            throw translate(e, "分片上传失败");
        }
    }

    @Override
    public FileObject completeMultipartUpload(String uploadId, Long orderId) {
        requireMinioReady();
        String objectKey = resolveObjectKey(uploadId);
        if (objectKey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分片会话不存在: " + uploadId);
        }
        // 分页拉取该 uploadId 的全部元件，组装合并清单
        List<CompletedPart> parts = new ArrayList<>();
        int marker = 0;
        while (true) {
            ListPartsResponse listing = s3Client.listParts(ListPartsRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumberMarker(marker > 0 ? marker : null)
                    .build());
            listing.parts().forEach(p -> parts.add(CompletedPart.builder()
                    .partNumber(p.partNumber())
                    .eTag(p.eTag())
                    .build()));
            if (!listing.isTruncated()) {
                break;
            }
            marker = listing.nextPartNumberMarker();
        }
        if (parts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有任何分片，无法合并");
        }

        try {
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
        } catch (Exception e) {
            throw translate(e, "分片合并失败");
        }
        multipartSessions.remove(uploadId);

        long size;
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .key(objectKey)
                    .build());
            size = head.contentLength();
        } catch (Exception e) {
            throw translate(e, "合并后读取对象信息失败");
        }

        // 会话已终结：更新 init 时预落的行（补充大小/类型/归属工单），保持一行整记录
        jdbcTemplate.update(
                "UPDATE file_object SET file_size = ?, content_type = ?, order_id = ? "
                        + "WHERE upload_id = ? AND file_type = ? AND file_size IS NULL",
                size, MediaType.APPLICATION_OCTET_STREAM_VALUE, orderId, uploadId, TYPE_MULTIPART);

        FileObject meta = new FileObject();
        meta.setFileName(fileNameOfObjectKey(objectKey));
        meta.setObjectKey(objectKey);
        meta.setFileSize(size);
        meta.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        meta.setFileType(TYPE_MULTIPART);
        meta.setOrderId(orderId);
        meta.setUploadId(uploadId);
        meta.setGmtCreate(LocalDateTime.now());
        meta.setId(jdbcTemplate.queryForObject(
                "SELECT id FROM file_object WHERE upload_id = ? AND file_type = ? LIMIT 1",
                Long.class, uploadId, TYPE_MULTIPART));
        return meta;
    }

    /** ============================== 下载 ============================== */

    @Override
    public Map<String, String> getSignedUrl(Long fileId) {
        requireMinioReady();
        FileObject meta = fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在: " + fileId));
        long expireSec = Math.max(60, expireSeconds());
        try {
            String url = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(expireSec))
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(minioProperties.getBucket())
                                    .key(meta.getObjectKey())
                                    .build())
                            .build())
                    .url().toString();
            return Map.of("signedUrl", url, "fileName", String.valueOf(meta.getFileName()));
        } catch (Exception e) {
            throw translate(e, "生成签名 URL 失败");
        }
    }

    private long expireSeconds() {
        return minioProperties.getUrlExpireSeconds() > 0 ? minioProperties.getUrlExpireSeconds() : 1800;
    }

    @Override
    @Slave // 演示读写分离：附件列表查询路由到从库
    public List<FileObject> listByOrder(Long orderId) {
        return fileObjectRepository.findByOrderId(orderId);
    }

    @Override
    public void downloadZip(List<Long> ids, HttpServletResponse response) {
        requireMinioReady();
        List<FileObject> metas = fileObjectRepository.findByIdIn(ids);
        if (metas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有可下载的文件");
        }
        String zipName = "campus-repair-files-" + System.currentTimeMillis() + ".zip";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(zipName, StandardCharsets.UTF_8));
        try (java.util.zip.ZipOutputStream zipStream = new java.util.zip.ZipOutputStream(
                response.getOutputStream())) {
            byte[] buffer = new byte[8192];
            for (FileObject meta : metas) {
                // 边从 MinIO 拉流边写进 ZIP，不在内存里攒整个包
                try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(minioProperties.getBucket())
                                .key(meta.getObjectKey())
                                .build())) {
                    zipStream.putNextEntry(new java.util.zip.ZipEntry(meta.getFileName()));
                    int n;
                    while ((n = object.read(buffer)) != -1) {
                        zipStream.write(buffer, 0, n);
                    }
                    zipStream.closeEntry();
                }
            }
            zipStream.finish();
        } catch (Exception e) {
            log.error("ZIP 打包下载失败: ids={}", ids, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ZIP 打包下载失败: " + e.getMessage(), e);
        }
    }

    /** ============================== 内部工具 ============================== */

    private void requireMinioReady() {
        if (s3Client == null || s3Presigner == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MinIO 未配置：请先启动 campus-minio 容器（docker start campus-minio）并检查 file.minio.* 配置");
        }
        ensureBucket();
    }

    /**
     * 桶自检 + 自动创建（进程内一次）：组员克隆仓库后 MinIO 里是全新空桶区，
     * 首个文件请求会 HeadBucket 发现桶缺失并自动 create，无需手工装 mc 客户端。
     */
    private void ensureBucket() {
        if (bucketChecked) {
            return;
        }
        String bucket = minioProperties.getBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException absent) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("MinIO 桶不存在，已自动创建: {}（新环境首次运行，属预期行为）", bucket);
        }
        bucketChecked = true;
    }

    /** 把 S3 SDK 异常翻译成带 HTTP 语义的提示（保留栈便于排障） */
    private ResponseStatusException translate(Exception e, String prefix) {
        if (e instanceof S3Exception s3e) {
            return new ResponseStatusException(
                    HttpStatus.resolve(s3e.statusCode()) == null
                            ? HttpStatus.INTERNAL_SERVER_ERROR
                            : HttpStatus.resolve(s3e.statusCode()),
                    prefix + ": " + s3e.getMessage(), e);
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, prefix + ": " + e.getMessage(), e);
    }

    /** 对象键规则：uploads/{orderId 或 none}/{uuid}/{fileName}，防重名也防路径穿越 */
    private String buildObjectKey(Long orderId, String fileName) {
        String safeName = Optional.ofNullable(fileName).orElse("unnamed")
                .replace('\\', '_').replace('/', '_');
        String folder = orderId == null ? "none" : String.valueOf(orderId);
        return "uploads/" + folder + "/" + UUID.randomUUID() + "/" + safeName;
    }

    private String fileNameOfObjectKey(String objectKey) {
        int idx = objectKey.lastIndexOf('/');
        return idx < 0 ? objectKey : objectKey.substring(idx + 1);
    }
}
