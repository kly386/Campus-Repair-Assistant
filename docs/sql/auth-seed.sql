-- =====================================================================
-- 校园快修：RBAC 初始数据 + 演示账号
-- 前置：先执行 docs/sql/auth-rbac.sql
-- 演示密码：admin123 / student123 / worker123（均为 BCrypt）
-- =====================================================================
USE campus_repair;

-- 角色
INSERT INTO `role` (`id`, `code`, `name`, `description`) VALUES
(1, 'ADMIN',   '系统管理员', '拥有全部菜单与接口权限'),
(2, 'STUDENT', '学生',       '报修、进度查看、评价'),
(3, 'WORKER',  '维修师傅',   '接单、处理、提交维修结果')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `description`=VALUES(`description`);

-- 菜单 / 接口权限。相同 code 可由菜单、接口和按钮共同引用
INSERT INTO `permission`
(`id`, `code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`) VALUES
(1,  'menu:dashboard',       '工作台',       'MENU', NULL, '/dashboard', 'Dashboard', NULL, NULL, 'House', 1),
(2,  'menu:orders',          '报修工单',     'MENU', NULL, '/orders',    'Orders',    '/api/orders', 'GET', 'Tickets', 2),
(3,  'menu:schedule',        '调度排班',     'MENU', NULL, '/schedule',  'Schedule',  NULL, NULL, 'Calendar', 3),
(4,  'menu:diagnosis',       '自助诊断',     'MENU', NULL, '/diagnosis', 'Diagnosis', NULL, NULL, 'FirstAidKit', 4),
(5,  'menu:files',           '文件中心',     'MENU', NULL, '/files',     'Files',     '/api/files', 'GET', 'Folder', 5),
(9,  'menu:repair',          '智能报修',     'MENU', NULL, '/repair',    'Repair',    '/api/orders', 'POST', 'Tools', 0),
(6,  'menu:system',          '系统管理',     'MENU', NULL, '/system',    'Layout',    NULL, NULL, 'Setting', 6),
(7,  'menu:system:user',     '用户权限',     'MENU', 6,    '/system/users', 'Users',  '/api/admin', 'GET', 'User', 1),
(8,  'menu:system:role',     '角色权限',     'MENU', 6,    '/system/roles', 'Roles',  '/api/admin/roles', 'GET', 'Key', 2),

(11, 'order:create',         '创建工单',     'API', NULL, NULL, NULL, '/api/orders', 'POST', NULL, 10),
(12, 'order:read',           '查看工单',     'API', NULL, NULL, NULL, '/api/orders/**', 'GET', NULL, 11),
(13, 'order:update',         '修改工单',     'API', NULL, NULL, NULL, '/api/orders', 'PUT', NULL, 12),
(14, 'order:delete',         '删除工单',     'API', NULL, NULL, NULL, '/api/orders/**', 'DELETE', NULL, 13),

(21, 'file:upload',          '上传附件',     'API', NULL, NULL, NULL, '/api/files/**', 'POST', NULL, 20),
(22, 'file:read',            '读取附件',     'API', NULL, NULL, NULL, '/api/files/**', 'GET', NULL, 21),

(31, 'system:role:read',     '查看角色',     'API', NULL, NULL, NULL, '/api/admin/roles', 'GET', NULL, 30),
(32, 'system:permission:read','查看权限',    'API', NULL, NULL, NULL, '/api/admin/permissions', 'GET', NULL, 31),
(33, 'system:user:role',     '分配用户角色', 'API', NULL, NULL, NULL, '/api/admin/users/*/roles', 'PUT', NULL, 32),
(34, 'system:role:permission','配置角色权限','API', NULL, NULL, NULL, '/api/admin/roles/*/permissions', 'PUT', NULL, 33),
(35, 'system:user:lock',     '锁定账号',     'API', NULL, NULL, NULL, '/api/admin/users/*/lock', 'POST', NULL, 34),
(36, 'system:user:unlock',   '解锁账号',     'API', NULL, NULL, NULL, '/api/admin/users/*/unlock', 'POST', NULL, 35),
(37, 'system:db:read',       '数据库运维信息','API', NULL, NULL, NULL, '/api/system/db', 'GET', NULL, 36),
(40, '*:*',                  '超级权限',     'API', NULL, NULL, NULL, NULL, NULL, NULL, 99)
ON DUPLICATE KEY UPDATE
`name`=VALUES(`name`), `type`=VALUES(`type`), `parent_id`=VALUES(`parent_id`),
`route_path`=VALUES(`route_path`), `component`=VALUES(`component`),
`api_path`=VALUES(`api_path`), `http_method`=VALUES(`http_method`), `sort_no`=VALUES(`sort_no`);

-- 演示账号（BCrypt）
INSERT INTO `user`
(`username`, `password`, `real_name`, `role`, `phone`, `email`, `status`, `is_deleted`) VALUES
('admin',   '$2a$10$bmdruIR2O6cS96sRDRL.jOaLuz2pOoGh2i3Oi5YjmJRig3CQOlOpC', '系统管理员', 'ADMIN',   '13800000001', 'admin@campus.local',   'ACTIVE', 0),
('student', '$2a$10$ZA/dF/1ImxbMwWzYDWgpPe1sPIAcrSGP5oyNJyqYzwpiOkHVgtiXe', '测试学生',   'STUDENT', '13800000002', 'student@campus.local', 'ACTIVE', 0),
('worker',  '$2a$10$veCFNGbcMkS.9d9.YKq1BOIpsljjTuHrYVfAyK0G1ux/bhRZ6GYta', '测试维修工', 'WORKER',  '13800000003', 'worker@campus.local',  'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
`password`=VALUES(`password`), `real_name`=VALUES(`real_name`),
`role`=VALUES(`role`), `phone`=VALUES(`phone`), `email`=VALUES(`email`), `status`='ACTIVE';

-- 角色权限
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 40),
(2, 1), (2, 2), (2, 4), (2, 5), (2, 9), (2, 11), (2, 12), (2, 21), (2, 22),
(3, 1), (3, 2), (3, 3), (3, 5), (3, 12), (3, 13), (3, 22);

-- 用户角色（按用户名关联，已存在则忽略）
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u JOIN `role` r ON r.code = u.role
WHERE u.username IN ('admin', 'student', 'worker') AND u.is_deleted = 0;
