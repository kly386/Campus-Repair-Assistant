-- =====================================================================
-- 校园快修服务助手：自助诊断旧库结构升级补丁
-- 作用：对已存在 diagnosis_session 表的旧库，补齐新代码所需的列与约束：
--       1) conversation_id / emergency_level / aborted 三个新列
--       2) is_solved 改为可空（NULL=用户未反馈，新代码会显式写入 NULL）
--       3) emergency_level 默认值 NORMAL，历史 NULL 回填
--       4) idx_conversation 复合索引（多轮对话查询）
-- 幂等：所有变更均通过 information_schema 判断后执行，可重复导入。
-- 执行方式（严禁 PowerShell 管道灌 SQL，中文会乱码）：
--   docker cp docs\sql\diagnosis-patch.sql mysql-master:/tmp/diag-patch.sql
--   docker exec mysql-master sh -c "mysql --default-character-set=utf8mb4 -uroot -p<密码> campus_repair < /tmp/diag-patch.sql"
-- =====================================================================
USE campus_repair;

SET @db := DATABASE();

-- [1] conversation_id
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='diagnosis_session' AND COLUMN_NAME='conversation_id'),
  'SELECT 1',
  "ALTER TABLE `diagnosis_session` ADD COLUMN `conversation_id` VARCHAR(64) DEFAULT NULL COMMENT '多轮对话ID(前端生成,同一对话共用)' AFTER `user_id`"
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- [2] emergency_level
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='diagnosis_session' AND COLUMN_NAME='emergency_level'),
  'SELECT 1',
  "ALTER TABLE `diagnosis_session` ADD COLUMN `emergency_level` VARCHAR(16) DEFAULT 'NORMAL' COMMENT '诊断结论紧急等级: HIGH/NORMAL' AFTER `matched_kb_id`"
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 老列若默认值仍为 NULL 也一并修正（MODIFY 只改元数据，不丢历史数据）
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='diagnosis_session'
           AND COLUMN_NAME='emergency_level' AND COLUMN_DEFAULT IS NULL),
  "ALTER TABLE `diagnosis_session` MODIFY COLUMN `emergency_level` VARCHAR(16) DEFAULT 'NORMAL' COMMENT '诊断结论紧急等级: HIGH/NORMAL'",
  'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE `diagnosis_session` SET `emergency_level`='NORMAL' WHERE `emergency_level` IS NULL;

-- [3] aborted
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='diagnosis_session' AND COLUMN_NAME='aborted'),
  'SELECT 1',
  "ALTER TABLE `diagnosis_session` ADD COLUMN `aborted` TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户是否主动中止: 0正常/1点停止或断链' AFTER `is_solved`"
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- [4] is_solved 必须可空：NULL 未反馈 / 0 未解决 / 1 已解决
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='diagnosis_session'
           AND COLUMN_NAME='is_solved' AND IS_NULLABLE='YES'),
  'SELECT 1',
  "ALTER TABLE `diagnosis_session` MODIFY COLUMN `is_solved` TINYINT(1) UNSIGNED DEFAULT NULL COMMENT '是否自助解决: NULL未反馈/0未解决/1已解决'"
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- [5] 多轮对话索引
SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='diagnosis_session' AND INDEX_NAME='idx_conversation'),
  'SELECT 1',
  'CREATE INDEX `idx_conversation` ON `diagnosis_session` (`conversation_id`, `user_id`, `gmt_create`)'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
