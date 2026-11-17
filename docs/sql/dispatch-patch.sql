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
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`) VALUES
('dispatch:auto',       '自动派单',   'API', 9, NULL, NULL, '/api/dispatch/**', 'POST',   'MagicStick', 61),
('dispatch:reschedule', '工单改约',   'API', 9, NULL, NULL, '/api/dispatch/**', 'PUT',    'Clock',      62),
('dispatch:read',       '派单审计',   'API', 9, NULL, NULL, '/api/dispatch/**', 'GET',    'View',       63);

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
-- 4. 演示专长（工单类别 1~5 参照 init.sql；有则跳过）
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
