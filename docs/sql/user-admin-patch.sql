-- =====================================================================
-- 用户管理权限补丁（开璐瑶 2026-11-24）
-- 只在原 Docker 主库执行，复制自动同步从库；可重复执行。
--   docker cp 本文件 mysql-master:/tmp/user-admin-patch.sql
--   docker exec -i mysql-master sh -c "mysql --default-character-set=utf8mb4 -uroot -p*** campus_repair < /tmp/user-admin-patch.sql"
-- 菜单 menu:system:user / menu:system:role 已由 auth-seed.sql 提供。
-- =====================================================================
USE campus_repair;

-- 1. 用户列表 / 编辑接口权限
INSERT INTO `permission`
(`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`)
SELECT s.code, s.name, 'API', NULL, NULL, NULL, s.api, s.method, NULL, s.sort
FROM (
    SELECT 'system:user:read'   AS code, '查看用户' AS name, '/api/admin/users'     AS api, 'GET' AS method, 38 AS sort UNION ALL
    SELECT 'system:user:update', '修改用户',              '/api/admin/users/*',           'PUT',      39
) s
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.`code` = s.code);

-- 2. 管理员：用户/角色菜单 + 全部 RBAC 接口权限（含已有角色分配、锁定解锁）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r, `permission` p
WHERE r.`code` = 'ADMIN'
  AND p.`is_deleted` = 0
  AND (p.`code` IN ('menu:system:user', 'menu:system:role')
       OR p.`code` LIKE 'system:%')
  AND NOT EXISTS (
      SELECT 1 FROM `role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
