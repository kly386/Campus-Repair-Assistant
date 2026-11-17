-- AI 调用用量统计表（伍梦圆：AI 公共封装配套）
-- 执行方式：主库（mysql-master，13306）执行一次，复制自动同步从库。
-- 用途：答辩展示"密钥用量"——每次模型调用（含 mock）落一行。

SET NAMES utf8mb4;
USE campus_repair;

CREATE TABLE IF NOT EXISTS `ai_usage_log` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `caller`           VARCHAR(50)  NOT NULL COMMENT '调用方: repair_parse/rag_chat等',
    `model`            VARCHAR(64)  NOT NULL COMMENT '模型名',
    `is_mock`          TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否模拟调用: 0真实/1模拟',
    `success`          TINYINT(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否成功: 0失败/1成功',
    `prompt_tokens`    INT DEFAULT NULL COMMENT '提示词tokens',
    `completion_tokens` INT DEFAULT NULL COMMENT '补全tokens',
    `cost_ms`          INT DEFAULT NULL COMMENT '耗时毫秒',
    `error_message`    VARCHAR(500) DEFAULT NULL COMMENT '失败原因(成功为空)',
    `gmt_create`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_ai_caller_time` (`caller`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用用量统计表';
