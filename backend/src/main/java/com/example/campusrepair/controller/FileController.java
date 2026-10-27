package com.example.campusrepair.controller;

import com.example.campusrepair.entity.FileObject;
import com.example.campusrepair.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 文件接口
 *
 * @Description 大文件分片上传（init/chunk/complete 三段式）与
 *              小文件批量上传/打包下载两类接口的 HTTP 入口。
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /** ---------- 大文件：分片上传 ---------- */

    /** 第 1 步：初始化，拿 uploadId */
    @PostMapping("/multipart/init")
    public Map<String, String> initMultipart(@RequestParam String fileName,
                                             @RequestParam(required = false) Long orderId) {
        return fileStorageService.initMultipartUpload(fileName, orderId);
    }

    /** 第 2 步：逐片上传（chunkIndex 从 1 开始） */
    @PostMapping("/multipart/{uploadId}/{chunkIndex}")
    public Map<String, Object> uploadChunk(@PathVariable String uploadId,
                                           @PathVariable int chunkIndex,
                                           @RequestParam MultipartFile chunk) {
        return fileStorageService.uploadMultipartChunk(uploadId, chunkIndex, chunk);
    }

    /** 第 3 步：合并分片，返回元数据 */
    @PostMapping("/multipart/{uploadId}/complete")
    public FileObject completeMultipart(@PathVariable String uploadId,
                                        @RequestParam(required = false) Long orderId) {
        return fileStorageService.completeMultipartUpload(uploadId, orderId);
    }

    /** ---------- 小文件：批量上传 ---------- */

    /**
     * 批量上传：一次请求提交 N 个文件（form-data 字段名固定为 files，可重复）。
     * curl -F "files=@1.jpg" -F "files=@2.jpg" http://localhost:8080/api/files/batch-upload
     */
    @PostMapping("/batch-upload")
    public List<FileObject> batchUpload(@RequestParam("files") List<MultipartFile> files,
                                        @RequestParam(required = false) Long orderId) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files 字段为空");
        }
        return fileStorageService.uploadSmallBatch(files, orderId);
    }

    /** ---------- 读 ---------- */

    /** 工单附件列表（走从库） */
    @GetMapping("/order/{orderId}")
    public List<FileObject> listByOrder(@PathVariable Long orderId) {
        return fileStorageService.listByOrder(orderId);
    }

    /** 单文件：返回临时签名 URL，前端直接 GET 该 URL 下载/预览 */
    @GetMapping("/{fileId}/url")
    public Map<String, String> signedUrl(@PathVariable Long fileId) {
        return fileStorageService.getSignedUrl(fileId);
    }

    /** 批量下载：多个文件打包 ZIP 流式返回。?ids=1,2,3 */
    @GetMapping("/zip")
    public void downloadZip(@RequestParam String ids, HttpServletResponse response) {
        List<Long> idList = List.of(ids.split(",")).stream()
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Long::valueOf)
                .toList();
        if (idList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids 参数无效");
        }
        fileStorageService.downloadZip(idList, response);
    }
}
