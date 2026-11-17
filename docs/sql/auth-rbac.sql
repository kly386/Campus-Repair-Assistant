-- =====================================================================
-- 校园快修服务助手：统一认证 + 动态 RBAC 数据库结构
-- 数据库：MySQL 8.x；执行前先执行 docs/sql/init.sql
-- 本脚本为增量脚本，首次升级执行一次；已有 wx_openid 时跳过对应 ALTER
-- =====================================================================
USE campus_repair;

-- ---------------------------------------------------------------------
-- 1. 扩展用户表：邮箱登录、账号状态、全局 Token 版本、最后登录时间
-- ---------------------------------------------------------------------
ALTER TABLE `user`
    ADD COLUMN `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱(邮箱验证码登录)' AFTER `phone`,
    ADD COLUMN `wx_openid` VARCHAR(64) DEFAULT NULL COMMENT '微信openid(扫码登录)' AFTER `email`,
    ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '账号状态: ACTIVE/LOCKED/DISABLED' AFTER `wx_openid`,
    ADD COLUMN `token_version` BIGINT NOT NULL DEFAULT 0
        COMMENT '全局Token版本(预留一次性吊销)' AFTER `status`,
    ADD COLUMN `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间' AFTER `room`,
    ADD UNIQUE KEY `uk_user_email` (`email`),
    ADD UNIQUE KEY `uk_user_wx_openid` (`wx_openid`),
    ADD KEY `idx_user_phone` (`phone`),
    ADD KEY `idx_user_status` (`status`);

-- ---------------------------------------------------------------------
-- 2. 角色表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `code`         VARCHAR(50)  NOT NULL COMMENT '角色编码: ADMIN/STUDENT/WORKER',
    `name`         VARCHAR(50)  NOT NULL COMMENT '角色名称',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    `is_deleted`   TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除',
    `gmt_create`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC角色表';

-- ---------------------------------------------------------------------
-- 3. 权限表：MENU/API/BUTTON 共享同一权限标识
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `permission` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `code`         VARCHAR(100) NOT NULL COMMENT '权限标识，如 order:create',
    `name`         VARCHAR(50)  NOT NULL COMMENT '权限名称',
    `type`         VARCHAR(20)  NOT NULL COMMENT '类型: MENU/API/BUTTON',
    `parent_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '父权限ID(菜单树)',
    `route_path`   VARCHAR(200) DEFAULT NULL COMMENT '前端路由路径',
    `component`    VARCHAR(200) DEFAULT NULL COMMENT '前端组件名',
    `api_path`     VARCHAR(200) DEFAULT NULL COMMENT '后端接口路径',
    `http_method`  VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP方法',
    `icon`         VARCHAR(50)  DEFAULT NULL COMMENT '菜单图标',
    `sort_no`      INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `is_deleted`   TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除',
    `gmt_create`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`code`),
    KEY `idx_permission_parent` (`parent_id`),
    KEY `idx_permission_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC权限表';

-- ---------------------------------------------------------------------
-- 4. 用户-角色关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `role_id`      BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `gmt_create`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ---------------------------------------------------------------------
-- 5. 角色-权限关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `role_id`       BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
    `gmt_create`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ---------------------------------------------------------------------
-- 6. 登录会话表：刷新 Token 轮换与主动吊销
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `auth_session` (
    `id`                 VARCHAR(36) NOT NULL COMMENT '会话ID(sid)',
    `user_id`            BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `access_jti`         VARCHAR(64) NOT NULL COMMENT '当前access Token jti',
    `refresh_jti`        VARCHAR(64) NOT NULL COMMENT '当前refresh Token jti',
    `access_expires_at`  DATETIME NOT NULL COMMENT 'access Token过期时间',
    `refresh_expires_at` DATETIME NOT NULL COMMENT 'refresh Token过期时间',
    `client_fingerprint` VARCHAR(64) NOT NULL COMMENT '客户端指纹',
    `ip`                 VARCHAR(50) DEFAULT NULL COMMENT '登录IP',
    `user_agent`         VARCHAR(500) DEFAULT NULL COMMENT '终端UA',
    `revoked_at`         DATETIME DEFAULT NULL COMMENT '吊销时间',
    `revoked_reason`     VARCHAR(100) DEFAULT NULL COMMENT '吊销原因',
    `gmt_create`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session_user` (`user_id`),
    KEY `idx_session_refresh_expire` (`refresh_expires_at`),
    KEY `idx_session_revoked` (`revoked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JWT登录会话与吊销表';

-- ---------------------------------------------------------------------
-- 7. 登录记录表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `login_record` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`            BIGINT UNSIGNED DEFAULT NULL COMMENT '用户ID(账号不存在时为空)',
    `login_type`         VARCHAR(20) NOT NULL COMMENT 'PASSWORD/SMS/EMAIL/WECHAT/REFRESH',
    `identifier`         VARCHAR(100) DEFAULT NULL COMMENT '用户名/手机号/邮箱',
    `ip`                 VARCHAR(50) DEFAULT NULL,
    `user_agent`         VARCHAR(500) DEFAULT NULL,
    `client_fingerprint` VARCHAR(64) DEFAULT NULL,
    `success`            TINYINT(1) NOT NULL COMMENT '是否成功',
    `failure_reason`     VARCHAR(200) DEFAULT NULL,
    `gmt_create`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_login_user_time` (`user_id`, `gmt_create`),
    KEY `idx_login_ip_time` (`ip`, `gmt_create`),
    KEY `idx_login_type_time` (`login_type`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录审计记录表';

-- ---------------------------------------------------------------------
-- 8. 登录锁定表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `login_lock` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `lock_type`      VARCHAR(30) NOT NULL DEFAULT 'PASSWORD' COMMENT '锁定类型',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'FAILING'
        COMMENT 'FAILING/ACTIVE/RELEASED',
    `fail_count`     INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `first_failed_at` DATETIME DEFAULT NULL COMMENT '首次失败时间',
    `locked_at`      DATETIME DEFAULT NULL COMMENT '锁定时间',
    `unlock_at`      DATETIME DEFAULT NULL COMMENT '解锁时间',
    `released_at`    DATETIME DEFAULT NULL COMMENT '解锁时间',
    `release_reason` VARCHAR(200) DEFAULT NULL COMMENT '解锁原因',
    `gmt_create`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_lock_type` (`user_id`, `lock_type`),
    KEY `idx_lock_status_unlock` (`status`, `unlock_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号登录锁定表';

-- ---------------------------------------------------------------------
-- 9. 鉴权审计日志表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `auth_audit_log` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `event_type`     VARCHAR(50) NOT NULL COMMENT 'ACCOUNT_LOCKED/UNLOCKED/USER_ROLE_CHANGED等',
    `operator_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID(系统内操作为空)',
    `target_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '目标用户ID',
    `target_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '目标角色/权限/锁定记录ID',
    `detail`         VARCHAR(1000) DEFAULT NULL COMMENT '事件详情',
    `ip`             VARCHAR(50) DEFAULT NULL,
    `gmt_create`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_event_time` (`event_type`, `gmt_create`),
    KEY `idx_audit_target_user` (`target_user_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鉴权与权限变更审计日志表';