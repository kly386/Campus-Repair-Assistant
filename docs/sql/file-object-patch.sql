-- 补充：file_object 文件元数据表
-- 背景：存储层迁移 MinIO 时 ddl-auto 由 update 改为 none，
--       但 init.sql 中遗漏了本表 DDL，导致新环境文件模块启动/查询报"表不存在"。
-- 执行方式：在主库（mysql-master，端口 13306）执行一次，复制自动同步到从库。
-- 导入：docker cp 本文件到主库容器后执行
--       mysql -uroot -p*** --default-character-set=utf8mb4 < patch.sql（首行 SET NAMES 兜底）

SET NAMES utf8mb4;

USE campus_repair;

CREATE TABLE IF NOT EXISTS `file_object` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `file_name`    VARCHAR(255) NOT NULL COMMENT '原始文件名(下载时还原用)',
    `object_key`   VARCHAR(500) NOT NULL COMMENT '对象键 uploads/{orderId}/{uuid}/{fileName}',
    `file_size`    BIGINT UNSIGNED DEFAULT NULL COMMENT '文件字节数(分片未合并前为空)',
    `content_type` VARCHAR(100) DEFAULT NULL COMMENT 'MIME 类型',
    `md5`          VARCHAR(64)  DEFAULT NULL COMMENT '文件MD5(完整性校验)',
    `file_type`    VARCHAR(20)  NOT NULL COMMENT '类型: small=小文件/multipart=分片大文件',
    `order_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID(半成品分片会话为空)',
    `upload_id`    VARCHAR(128) DEFAULT NULL COMMENT '分片上传ID(仅大文件)',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_upload_id` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件对象元数据表';
