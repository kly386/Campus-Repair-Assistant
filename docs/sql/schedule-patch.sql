-- =====================================================================
-- 排班 CRUD 权限补丁（开璐瑶 2026-11-24）
-- 只在原 Docker 主库执行，复制自动同步从库；可重复执行。
--   docker cp 本文件 mysql-master:/tmp/schedule-patch.sql
--   docker exec -i mysql-master sh -c "mysql --default-character-set=utf8mb4 -uroot -p*** campus_repair < /tmp/schedule-patch.sql"
-- 菜单 menu:schedule 已由 auth-seed.sql 提供（/schedule -> Schedule），这里只补接口权限。
-- =====================================================================
USE campus_repair;

-- 1. 排班管理接口权限
INSERT INTO `permission`
(`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`)
SELECT s.code, s.name, 'API', NULL, NULL, NULL, s.api, s.method, NULL, s.sort
FROM (
    SELECT 'schedule:read'   AS code, '查看排班' AS name, '/api/schedule'     AS api, 'GET'    AS method, 70 AS sort UNION ALL
    SELECT 'schedule:create', '新增排班',              '/api/schedule',              'POST',   71 UNION ALL
    SELECT 'schedule:update', '修改排班',              '/api/schedule/*',            'PUT',    72 UNION ALL
    SELECT 'schedule:delete', '删除排班',              '/api/schedule/*',            'DELETE', 73
) s
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.`code` = s.code);

-- 2. 管理员：菜单 + 全部排班接口权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r, `permission` p
WHERE r.`code` = 'ADMIN'
  AND p.`code` IN ('menu:schedule', 'schedule:read', 'schedule:create', 'schedule:update', 'schedule:delete')
  AND p.`is_deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );

-- 3. 师傅：允许查看排班（只读，不能改）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `role` r, `permission` p
WHERE r.`code` = 'WORKER'
  AND p.`code` IN ('menu:schedule', 'schedule:read')
  AND p.`is_deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `role_permission` rp
      WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`
  );
