-- =====================================================================
-- 调度 Agent 模块权限 + 演示数据补丁（开璐瑶，2026-11-17）
-- 执行方式：在主库执行一次（root），复制自动同步从库
--   docker cp 本文件 mysql-master:/tmp/d.sql
--   docker exec -i mysql-master sh -c "mysql --default-character-set=utf8mb4 -uroot -p*** campus_repair < /tmp/d.sql"
-- 依赖：docs/sql/init.sql（含演示师傅 id=3/5/6 与故障类别 1~5）
-- =====================================================================
USE campus_repair;

-- ---------------------------------------------------------------------
-- 1. 调度模块权限（挂到 menu:repair 之下）
-- ---------------------------------------------------------------------
-- 1a. 调度工作台菜单（顶级菜单；component=Dispatch 映射 dynamicRoutes.js 视图。
--     注意：不要挂到 menu:repair 之下，否则智能报修会被渲染成不可点击的分组）
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`)
SELECT 'menu:dispatch', '调度工作台', 'MENU', NULL, '/dispatch', 'Dispatch', NULL, NULL, 'Van', 10
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.`code` = 'menu:dispatch');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `permission` p
WHERE p.`code` = 'menu:dispatch'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp
                  WHERE rp.`role_id` = 1 AND rp.`permission_id` = p.`id`);

-- 1b. 调度模块接口权限
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`)
SELECT s.code, s.name, 'API', 9, NULL, NULL, s.api, s.method, s.icon, s.sort FROM (
    SELECT 'dispatch:auto' AS code, '自动派单' AS name, '/api/dispatch/**' AS api, 'POST' AS method, 'MagicStick' AS icon, 61 AS sort UNION ALL
    SELECT 'dispatch:reschedule', '工单改约', '/api/dispatch/**', 'PUT', 'Clock', 62 UNION ALL
    SELECT 'dispatch:read', '派单审计', '/api/dispatch/**', 'GET', 'View', 63
) s
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.`code` = s.code);

-- ---------------------------------------------------------------------
-- 2. 管理员角色（id=1）授予调度权限
-- ---------------------------------------------------------------------
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `permission` p
WHERE p.`code` IN ('dispatch:auto', 'dispatch:reschedule', 'dispatch:read')
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp
                  WHERE rp.`role_id` = 1 AND rp.`permission_id` = p.`id`);

-- ---------------------------------------------------------------------
-- 3. 演示排班（明天起 3 天，3 位师傅轮班；区域对应 init.sql 演示楼栋）
--    师傅：3=王师傅(水电) 5=张师傅(门窗/网络) 6=李师傅(电器/水电)
-- ---------------------------------------------------------------------
INSERT INTO `worker_schedule` (`worker_id`, `work_date`, `time_slot`, `area`, `status`, `max_orders`, `cur_orders`)
SELECT w.id, CURDATE() + INTERVAL s.day DAY, s.slot, s.area, 'FREE', 3, 0
FROM (SELECT 3 AS id UNION SELECT 5 UNION SELECT 6) w
JOIN (
    SELECT 1 AS day, 'MORNING'   AS slot, '紫荆1栋,紫荆2栋' AS area UNION ALL
    SELECT 1, 'AFTERNOON', '紫荆3栋,紫荆4栋' UNION ALL
    SELECT 2, 'MORNING',   '紫荆5栋,紫荆6栋' UNION ALL
    SELECT 2, 'AFTERNOON', '紫荆1栋,紫荆2栋' UNION ALL
    SELECT 3, 'MORNING',   '紫荆3栋,紫荆4栋' UNION ALL
    SELECT 3, 'AFTERNOON', '紫荆5栋,紫荆6栋'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM `worker_schedule` ws
    WHERE ws.`worker_id` = w.id AND ws.`work_date` = CURDATE() + INTERVAL s.day DAY AND ws.`time_slot` = s.slot
);

-- ---------------------------------------------------------------------
-- 3b. 补齐演示师傅 user_role 关联（user 5/6 缺失会导致接口 403；有则跳过）
-- ---------------------------------------------------------------------
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id` FROM `user` u, `role` r
WHERE u.`username` IN ('demo_worker1', 'demo_worker2') AND r.`code` = 'WORKER'
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur
                  WHERE ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`);

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id` FROM `user` u, `role` r
WHERE u.`username` IN ('demo_stu1', 'demo_stu2', 'demo_stu3') AND r.`code` = 'STUDENT'
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur
                  WHERE ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`);

-- ---------------------------------------------------------------------
-- 4. 师傅端工作台菜单（仅 WORKER 角色；ADMIN 不需要）
-- ---------------------------------------------------------------------
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`)
SELECT 'menu:workbench', '接单工作台', 'MENU', NULL, '/workbench', 'Workbench', NULL, NULL, 'Suitcase', 11
WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.`code` = 'menu:workbench');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id` FROM `role` r, `permission` p
WHERE r.`code` = 'WORKER' AND p.`code` = 'menu:workbench'
  AND NOT EXISTS (SELECT 1 FROM `role_permission` rp
                  WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`);

-- ---------------------------------------------------------------------
-- 5. 演示专长（工单类别 1~5 参照 init.sql；有则跳过）
-- ---------------------------------------------------------------------
INSERT INTO `worker_specialty` (`worker_id`, `category_id`)
SELECT s.worker_id, s.category_id FROM (
    SELECT 3 AS worker_id, 1 AS category_id UNION ALL
    SELECT 3, 2 UNION ALL
    SELECT 5, 3 UNION ALL
    SELECT 5, 4 UNION ALL
    SELECT 6, 5 UNION ALL
    SELECT 6, 1
) s
WHERE NOT EXISTS (
    SELECT 1 FROM `worker_specialty` ws
    WHERE ws.`worker_id` = s.worker_id AND ws.`category_id` = s.category_id
);
