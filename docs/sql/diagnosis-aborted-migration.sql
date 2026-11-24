-- ============================================================
-- 诊断会话"用户主动中止"标记迁移（负责人：闵娟）
--
-- 适用范围：已经执行过 docs/sql/init.sql 的旧库（diagnosis_session 尚无 aborted 列）
-- 新库无需执行：init.sql 已包含该列。
--
-- 执行方式（仓库根目录，utf8mb4 必须显式指定）：
--   docker exec -i mysql-master mysql -uroot -p --default-character-set=utf8mb4 campus_repair < docs/sql/diagnosis-aborted-migration.sql
--
-- 本脚本幂等：重复执行会跳过已存在的列，不报错。
-- 不新建任何表、不改动 knowledge_base、不需要重新灌种子数据。
-- ============================================================

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'diagnosis_session'
      AND COLUMN_NAME = 'aborted'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `diagnosis_session` ADD COLUMN `aborted` TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT ''用户是否主动中止: 0正常/1点停止或断链'' AFTER `is_solved`',
    'SELECT ''aborted 列已存在，跳过'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
