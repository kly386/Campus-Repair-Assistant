package com.example.campusrepair.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.example.campusrepair.config.OssProperties;
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

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储服务实现（阿里云 OSS 版）
 *
 * @Description 大文件分片上传使用 OSS 原生 Multipart Upload：
 *              后端接收"一支分片"就立刻转发 OSS（InitiateMultipartUpload /
 *              UploadPart / CompleteMultipartUpload），内存占用恒定为单分片大小。
 *              小文件批量上传则把 N 个对象一次 putObject 后做批量元数据落库。
 *              注：分片会话当前保存在本进程的 Map 中，适合课程演示；
 *              若部署到双 Tomcat 集群需要外置会话（如落表），文档中有说明。
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024; // 单分片上限 10MB（演示收紧）
    private static final String TYPE_SMALL = "small";
    private static final String TYPE_MULTIPART = "multipart";

    private final OssProperties ossProperties;
    private final FileObjectRepository fileObjectRepository;
    private final JdbcTemplate jdbcTemplate;

    /** OSS 客户端可选注入：未配置密钥时为 null，接口层给出明确提示而不是启动失败 */
    @Autowired(required = false)
    private OSS ossClient;

    /** 分片上传会话：uploadId -> objectKey，同一 uploadId 的分片都指向该对象 */
    private final Map<String, String> multipartSessions = new ConcurrentHashMap<>();

    public FileStorageServiceImpl(OssProperties ossProperties,
                                  FileObjectRepository fileObjectRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.ossProperties = ossProperties;
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
        requireOssReady();
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
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(file.getContentType());
                metadata.setContentLength(file.getSize());
                ossClient.putObject(ossProperties.getBucket(), objectKey, in, metadata);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "OSS 上传失败: " + e.getMessage(), e);
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
        requireOssReady();
        String objectKey = buildObjectKey(orderId, fileName);
        InitiateMultipartUploadResult initResult = ossClient.initiateMultipartUpload(
                new InitiateMultipartUploadRequest(ossProperties.getBucket(), objectKey));
        String uploadId = initResult.getUploadId();
        multipartSessions.put(uploadId, objectKey);
        log.info("初始化分片上传: uploadId={}, objectKey={}", uploadId, objectKey);
        Map<String, String> result = new HashMap<>();
        result.put("uploadId", uploadId);
        result.put("objectKey", objectKey);
        return result;
    }

    @Override
    public Map<String, Object> uploadMultipartChunk(String uploadId, int chunkIndex, MultipartFile chunk) {
        requireOssReady();
        String objectKey = multipartSessions.get(uploadId);
        if (objectKey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分片会话不存在或已过期: " + uploadId);
        }
        if (chunk.getSize() > MAX_CHUNK_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单分片超过 10MB 限制");
        }
        try (InputStream in = chunk.getInputStream()) {
            // OSS 元件编号从 1 开始，同一 uploadId 内必须唯一
            UploadPartRequest partReq = new UploadPartRequest(
                    ossProperties.getBucket(), objectKey, uploadId, chunkIndex, in, chunk.getSize());
            UploadPartResult partResult = ossClient.uploadPart(partReq);
            Map<String, Object> result = new HashMap<>();
            result.put("chunkIndex", chunkIndex);
            result.put("etag", partResult.getPartETag().getETag());
            result.put("size", chunk.getSize());
            return result;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public FileObject completeMultipartUpload(String uploadId, Long orderId) {
        requireOssReady();
        String objectKey = multipartSessions.get(uploadId);
        if (objectKey == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分片会话不存在: " + uploadId);
        }
        // 分页拉取该 uploadId 的全部元件，形式合并清单
        List<PartETag> partETags = new ArrayList<>();
        ListPartsRequest listReq = new ListPartsRequest(
                ossProperties.getBucket(), objectKey, uploadId);
        PartListing partListing;
        do {
            partListing = ossClient.listParts(listReq);
            for (PartSummary part : partListing.getParts()) {
                partETags.add(new PartETag(part.getPartNumber(), part.getETag()));
            }
            listReq.setPartNumberMarker(partListing.getNextPartNumberMarker());
        } while (partListing.isTruncated());
        if (partETags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有任何分片，无法合并");
        }

        ossClient.completeMultipartUpload(new CompleteMultipartUploadRequest(
                ossProperties.getBucket(), objectKey, uploadId, partETags));
        multipartSessions.remove(uploadId);

        Long size = ossClient.getObjectMetadata(ossProperties.getBucket(), objectKey)
                .getContentLength();
        FileObject meta = new FileObject();
        meta.setFileName(fileNameOfObjectKey(objectKey));
        meta.setObjectKey(objectKey);
        meta.setFileSize(size);
        meta.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        meta.setFileType(TYPE_MULTIPART);
        meta.setOrderId(orderId);
        meta.setUploadId(uploadId);
        meta.setGmtCreate(LocalDateTime.now());

        jdbcTemplate.update(
                "INSERT INTO file_object (file_name, object_key, file_size, content_type, md5, "
                        + "file_type, order_id, upload_id, gmt_create) VALUES (?,?,?,?,?,?,?,?,?)",
                meta.getFileName(), meta.getObjectKey(), meta.getFileSize(), meta.getContentType(),
                meta.getMd5(), meta.getFileType(), meta.getOrderId(), meta.getUploadId(), meta.getGmtCreate());
        meta.setId(jdbcTemplate.queryForObject(
                "SELECT id FROM file_object WHERE upload_id = ?", Long.class, uploadId));
        return meta;
    }

    /** ============================== 下载 ============================== */

    @Override
    public Map<String, String> getSignedUrl(Long fileId) {
        requireOssReady();
        FileObject meta = fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在: " + fileId));
        long expireMs = Math.max(60, expireSeconds()) * 1000;
        String url = ossClient.generatePresignedUrl(ossProperties.getBucket(),
                meta.getObjectKey(), new Date(System.currentTimeMillis() + expireMs)).toString();
        return Map.of("signedUrl", url, "fileName", String.valueOf(meta.getFileName()));
    }

    private long expireSeconds() {
        return ossProperties.getUrlExpireSeconds() > 0 ? ossProperties.getUrlExpireSeconds() : 1800;
    }

    @Override
    @Slave // 演示读写分离：附件列表查询路由到从库
    public List<FileObject> listByOrder(Long orderId) {
        return fileObjectRepository.findByOrderId(orderId);
    }

    @Override
    public void downloadZip(List<Long> ids, HttpServletResponse response) {
        requireOssReady();
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
                // 边从 OSS 拉流边写进 ZIP，不在内存里攒整个包
                OSSObject object = ossClient.getObject(ossProperties.getBucket(), meta.getObjectKey());
                zipStream.putNextEntry(new java.util.zip.ZipEntry(meta.getFileName()));
                try (InputStream in = object.getObjectContent()) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        zipStream.write(buffer, 0, n);
                    }
                }
                zipStream.closeEntry();
            }
            zipStream.finish();
        } catch (Exception e) {
            log.error("ZIP 打包下载失败: ids={}", ids, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ZIP 打包下载失败: " + e.getMessage(), e);
        }
    }

    /** ============================== 内部工具 ============================== */

    private void requireOssReady() {
        if (ossClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OSS 未配置：请设置环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET 后重启");
        }
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
