-- ============================================================
-- 自助诊断多轮上下文 + 报修接力字段迁移（负责人：闵娟）
--
-- 适用范围：已经执行过 docs/sql/init.sql 的旧库（diagnosis_session 尚无可空 is_solved）
-- 新库无需执行本文件，init.sql 已同步这些变更。
-- 执行方式（仓库根目录，utf8mb4 必须显式指定）：
--   docker exec -i mysql-master mysql -uroot -p --default-character-set=utf8mb4 campus_repair < docs/sql/diagnosis-conversation-migration.sql
--
-- 注意：本文件只执行一次；重复执行会因列已存在报错，属于正常的迁移保护。
-- ============================================================

ALTER TABLE `diagnosis_session`
    ADD COLUMN `conversation_id` VARCHAR(64) DEFAULT NULL COMMENT '多轮对话ID(前端生成,同一对话共用)' AFTER `user_id`,
    ADD COLUMN `emergency_level` VARCHAR(16) DEFAULT 'NORMAL' COMMENT '诊断结论紧急等级: HIGH/NORMAL' AFTER `matched_kb_id`,
    MODIFY COLUMN `is_solved` TINYINT(1) UNSIGNED NULL DEFAULT NULL COMMENT '是否自助解决: NULL未反馈/0未解决/1已解决',
    ADD INDEX `idx_conversation` (`conversation_id`, `user_id`, `gmt_create`);

-- 历史数据统一按"未反馈"处理：旧库的 0 分不清是真没解决还是压根没点反馈。
-- 演示库可接受；若生产库需要保留历史反馈，请先备份再决定是否执行下面这句。
UPDATE `diagnosis_session` SET `is_solved` = NULL WHERE `is_solved` = 0;
