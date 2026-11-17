-- =====================================================================
-- 校园快修服务助手：知识库模块权限补丁
-- 作用：注册知识库管理权限码与前端菜单，管理员（ADMIN 持有 *:* 通配）
--       展开时自动获得全部权限行，无需再配 role_permission；
--       学生/师傅默认不授予管理权限，学生口走已有的 menu:diagnosis。
-- 幂等：可重复执行（ON DUPLICATE KEY UPDATE）。
-- 执行方式（注意 --default-character-set=utf8mb4，否则中文乱码）：
--   docker exec -i mysql-master mysql -uroot -p<密码> --default-character-set=utf8mb4 < docs/sql/knowledge-patch.sql
-- @Author 开璐瑶  @Date 2026-11-17
-- =====================================================================
USE campus_repair;

-- 知识库管理（管理员口全部接口）
INSERT INTO `permission`
(`code`, `name`, `type`, `parent_id`, `route_path`, `component`, `api_path`, `http_method`, `icon`, `sort_no`, `is_deleted`)
VALUES
('knowledge:manage', '知识库管理', 'API', NULL, NULL, NULL, '/api/knowledge', NULL, NULL, 100, 0),
('menu:knowledge',   '知识管理',   'MENU', NULL, '/knowledge', 'Knowledge', NULL, NULL, 'Collection', 7, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `type` = VALUES(`type`), `route_path` = VALUES(`route_path`),
  `component` = VALUES(`component`), `api_path` = VALUES(`api_path`), `sort_no` = VALUES(`sort_no`);
