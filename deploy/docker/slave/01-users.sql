-- 从库首次初始化：应用账号（docker-entrypoint-initdb.d，字母序 01）
-- 关键：账号存在 mysql 系统库，不随业务库复制，从库必须单独创建同名同密码账号
-- 密码请与 master/01-users.sql、application-prod.properties 保持一致

CREATE USER IF NOT EXISTS 'campus'@'%' IDENTIFIED BY 'ChangeMe_123';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_repair.* TO 'campus'@'%';

FLUSH PRIVILEGES;
