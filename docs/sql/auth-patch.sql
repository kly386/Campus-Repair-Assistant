-- 登录鉴权补丁（相对 docs/sql/init.sql 增量执行）
USE campus_repair;

-- 微信扫码登录需要 openid 绑定字段
ALTER TABLE `user`
    ADD COLUMN `wx_openid` VARCHAR(64) DEFAULT NULL COMMENT '微信openid(扫码登录绑定.identifier)' AFTER `phone`,
    ADD UNIQUE KEY `uk_wx_openid` (`wx_openid`);

-- 手机号加索引（短信登录高频查询）
ALTER TABLE `user` ADD KEY `idx_phone` (`phone`);

-- 预置三个角色的测试账号（密码 admin123 / student123 / worker123 留明文：
-- 后端 AuthService 兼容明文校验并提示升级，首次修改密码后自动变为 BCrypt 存储）
-- INSERT IGNORE：init.sql 已插过 admin，重复执行不报错
INSERT IGNORE INTO `user` (`username`, `password`, `real_name`, `role`, `phone`) VALUES
('admin',   'admin123',   '系统管理员',  'ADMIN',   '13800000001'),
('student', 'student123', '测试学生',    'STUDENT', '13800000002'),
('worker',  'worker123',  '测试维修工',  'WORKER',  '13800000003');
