-- repair_order 联系方式字段补丁（幂等）
-- 执行方式：
--   docker cp docs/sql/repair-order-contact-patch.sql mysql-master:/tmp/repair-contact.sql
--   docker exec mysql-master sh -c "mysql --default-character-set=utf8mb4 -uroot -p<密码> campus_repair < /tmp/repair-contact.sql"

USE campus_repair;
SET @db := DATABASE();

SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='repair_order' AND COLUMN_NAME='contact_phone'),
  'SELECT 1',
  "ALTER TABLE `repair_order` ADD COLUMN `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话' AFTER `room`"
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=@db AND TABLE_NAME='repair_order' AND COLUMN_NAME='backup_phone'),
  'SELECT 1',
  "ALTER TABLE `repair_order` ADD COLUMN `backup_phone` VARCHAR(20) DEFAULT NULL COMMENT '备用电话' AFTER `contact_phone`"
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
