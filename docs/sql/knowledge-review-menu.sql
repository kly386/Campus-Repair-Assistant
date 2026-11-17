-- 知识库审核页：菜单 + 操作权限 + ADMIN 绑定（伍梦圆任务③，接口契约由闵娟提供）
USE campus_repair;

INSERT INTO permission (code, name, type, parent_id, route_path, component, icon, sort_no, is_deleted, gmt_create, gmt_modified)
VALUES ('menu:knowledge', '知识库审核', 'MENU', NULL, '/knowledge-review', 'KnowledgeReview', 'Reading', 6, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE route_path = VALUES(route_path), component = VALUES(component);

SET @menu_id = (SELECT id FROM permission WHERE code = 'menu:knowledge' AND type = 'MENU' LIMIT 1);

INSERT INTO permission (code, name, type, parent_id, sort_no, is_deleted, gmt_create, gmt_modified)
VALUES ('knowledge:review', '知识库审核操作', 'BUTTON', @menu_id, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id);

SET @btn_id = (SELECT id FROM permission WHERE code = 'knowledge:review' LIMIT 1);

INSERT INTO role_permission (role_id, permission_id, gmt_create)
SELECT 1, @menu_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM role_permission WHERE role_id = 1 AND permission_id = @menu_id);

INSERT INTO role_permission (role_id, permission_id, gmt_create)
SELECT 1, @btn_id, NOW()
WHERE NOT EXISTS (SELECT 1 FROM role_permission WHERE role_id = 1 AND permission_id = @btn_id);

SELECT p.id, p.code, p.name, p.route_path, p.component FROM permission p WHERE p.code IN ('menu:knowledge','knowledge:review');
SELECT r.code AS role_code, p.code AS perm_code FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id WHERE p.code IN ('menu:knowledge','knowledge:review');
