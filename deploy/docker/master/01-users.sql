-- 主库首次初始化：应用账号 + 复制账号（docker-entrypoint-initdb.d，字母序 01）
-- 数据库 campus_repair 已由 MYSQL_DATABASE 环境变量先建好
-- 密码请与 application-prod.properties、slave/01-users.sql 保持一致

-- 应用账号：只给 DML，够集群 ddl-auto=validate 用
CREATE USER IF NOT EXISTS 'campus'@'%' IDENTIFIED BY 'ChangeMe_123';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_repair.* TO 'campus'@'%';

-- 复制账号：从库拉 binlog 专用，最小权限
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'ChangeMe_Repl_123';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';

FLUSH PRIVILEGES;
