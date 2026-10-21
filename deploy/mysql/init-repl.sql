-- Campus Repair Assistant — 复制账号初始化（读写分离）
-- 在主库（VM1）上以 root 执行：
--   sudo mysql < deploy/mysql/init-repl.sql
-- 说明：仅授予 REPLICATION SLAVE 最小权限；repl 账号只存在于主库
--       （账号存于 mysql 库，不会随业务库复制到从库）。
-- 注意：请先把 ChangeMe_Repl_123 替换为实际密码，
--       并与从库上 CHANGE REPLICATION SOURCE 的 SOURCE_PASSWORD 保持一致。

CREATE USER IF NOT EXISTS 'repl'@'192.168.56.%' IDENTIFIED BY 'ChangeMe_Repl_123';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'192.168.56.%';
FLUSH PRIVILEGES;

-- 查看授权：
-- SHOW GRANTS FOR 'repl'@'192.168.56.%';
