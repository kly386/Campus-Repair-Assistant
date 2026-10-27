package com.example.campusrepair.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件对象元数据实体
 *
 * @Description 记录每一次上传产生的文件信息（小文件批量上传的元数据落库，
 *              大文件分片上传完成后合并写入），文件内容保存在阿里云 OSS，
 *              本表只存"文件在 OSS 的地址 + 工单关联关系"。
 *              对应 file_object 表。
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
@Entity
@Table(name = "file_object")
@Data
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件名（下载时还原用） */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** OSS 对象键：uploads/{orderId}/{uuid}/{fileName}，全局唯一 */
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    /** 文件字节数 */
    @Column(name = "file_size")
    private Long fileSize;

    /** MIME 类型，如 image/png */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 文件 MD5（分片合并完整性校验用；小文件为前端计算的快校验值，可空） */
    @Column(name = "md5", length = 64)
    private String md5;

    /** 文件类型：small=单请求小文件；multipart=分片上传大文件 */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    /** 关联工单 ID，未关联则为空 */
    @Column(name = "order_id")
    private Long orderId;

    /** OSS 分片上传 ID（仅大文件，便于排查未合并的分片痕迹） */
    @Column(name = "upload_id", length = 128)
    private String uploadId;

    @Column(name = "gmt_create")
    private LocalDateTime gmtCreate;
}
