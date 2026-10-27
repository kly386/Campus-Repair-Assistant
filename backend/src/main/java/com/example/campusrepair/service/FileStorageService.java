package com.example.campusrepair.service;

import com.example.campusrepair.entity.FileObject;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文件存储服务接口
 *
 * @Description 职责两件事：
 *              ① 大文件：走阿里云 OSS 原生分片上传（init → chunk* → complete），
 *              每支分片独立请求，天然支持断点续传；
 *              ② 小文件批量读写：一次请求打包上传 N 个文件（元数据批量落库），
 *              一次请求把多个文件打包成 ZIP 下载。
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
public interface FileStorageService {

    /** 单请求小文件同步上传（调试便利路径） */
    FileObject uploadSmall(MultipartFile file, Long orderId);

    /** 小文件批量上传：N 个文件一次请求提交，元数据一次批量落库 */
    List<FileObject> uploadSmallBatch(List<MultipartFile> files, Long orderId);

    /** 大文件分片上传第一步：初始化，返回 uploadId */
    Map<String, String> initMultipartUpload(String fileName, Long orderId);

    /** 大文件分片上传第二步：上传一支分片（index 从 1 起） */
    Map<String, Object> uploadMultipartChunk(String uploadId, int chunkIndex, MultipartFile chunk);

    /** 大文件分片上传第三步：通知 OSS 合并分片，并写入元数据 */
    FileObject completeMultipartUpload(String uploadId, Long orderId);

    /** 生成私有 Bucket 的临时签名下载 URL（默认 30 分钟有效） */
    Map<String, String> getSignedUrl(Long fileId);

    /** 工单附件列表（演示 @Slave 从库路由） */
    List<FileObject> listByOrder(Long orderId);

    /** 多文件打包 ZIP 下载（流式边拉边写，不在内存里攒整包） */
    void downloadZip(List<Long> ids, HttpServletResponse response);
}
