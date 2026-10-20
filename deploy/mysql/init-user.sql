-- Campus Repair Assistant — 集群数据库初始化
-- 在 VM1 上以 root 执行：
--   sudo mysql < deploy/mysql/init-user.sql
-- 然后导入业务表结构：
--   mysql -u root < docs/sql/init.sql
--
-- 注意：请先把 ChangeMe_123 替换为实际密码，并与 setenv.sh / application-prod.properties 保持一致。

CREATE DATABASE IF NOT EXISTS campus_repair
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 应用账号：集群部署使用 ddl-auto=validate，只需要 DML 权限
CREATE USER IF NOT EXISTS 'campus'@'192.168.56.%' IDENTIFIED BY 'ChangeMe_123';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_repair.* TO 'campus'@'192.168.56.%';
FLUSH PRIVILEGES;

-- 查看授权：
-- SHOW GRANTS FOR 'campus'@'192.168.56.%';
