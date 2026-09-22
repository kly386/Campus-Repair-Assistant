-- 校园快修服务助手 数据库初始化脚本
-- 数据库：MySQL 8.x
-- 版本：对齐 docs/数据项设计.md v1.4（15 张业务表，遵循《阿里巴巴 Java 开发手册》MySQL 建表规约）
-- 说明：执行本脚本前请先创建数据库：CREATE DATABASE campus_repair DEFAULT CHARACTER SET utf8mb4;

USE campus_repair;

-- -----------------------------------------------------
-- 1. 用户表 user（学生 / 维修师傅 / 管理员）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`     VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`     VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
    `real_name`    VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    `role`         VARCHAR(20)  NOT NULL COMMENT '角色: STUDENT/WORKER/ADMIN',
    `phone`        VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `building`     VARCHAR(50)  DEFAULT NULL COMMENT '所在楼栋(学生专用)',
    `room`         VARCHAR(50)  DEFAULT NULL COMMENT '房间号(学生专用)',
    `is_deleted`   TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除: 0正常/1删除',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------
-- 2. 故障类别表 repair_category
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `repair_category` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '类别ID',
    `name`         VARCHAR(50)  NOT NULL COMMENT '类别名称',
    `code`         VARCHAR(30)  DEFAULT NULL COMMENT '类别编码: WATER_ELECTRIC/NETWORK/FURNITURE/OTHER',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '描述',
    `parent_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '父类别ID(逻辑关联本表,支持多级)',
    `is_self_fix`  TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否可自助解决: 1是/0否',
    `is_deleted`   TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除: 0正常/1删除',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障类别表';

-- -----------------------------------------------------
-- 3. 报修工单表 repair_order（核心业务表）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `repair_order` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `order_no`        VARCHAR(32)  NOT NULL COMMENT '工单编号',
    `idempotency_key` VARCHAR(64)  NOT NULL COMMENT '幂等键(前端生成,防重复提交)',
    `title`           VARCHAR(100) NOT NULL COMMENT '故障标题',
    `description`     TEXT         NOT NULL COMMENT '故障描述(业务必填)',
    `ai_parsed_info`  TEXT         DEFAULT NULL COMMENT 'AI解析的结构化信息(JSON)',
    `ai_confidence`   DECIMAL(4,3) DEFAULT NULL COMMENT 'AI置信度(0~1)',
    `category_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '故障类别ID',
    `emergency_level` VARCHAR(20)  NOT NULL DEFAULT 'LOW' COMMENT '紧急度: LOW/MEDIUM/HIGH',
    `student_id`      BIGINT UNSIGNED NOT NULL COMMENT '报修学生ID',
    `building`        VARCHAR(50)  NOT NULL COMMENT '楼栋',
    `room`            VARCHAR(50)  NOT NULL COMMENT '房间号',
    `appointment_time` DATETIME    DEFAULT NULL COMMENT '预约上门时间(业务必填)',
    `worker_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '指派师傅ID(role=WORKER)',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态: SUBMITTED/ASSIGNED/ON_THE_WAY/PROCESSING/PENDING_ACCEPT/COMPLETED/CANCELLED/RESCHEDULED',
    `result`          TEXT         DEFAULT NULL COMMENT '维修结论(知识沉淀来源)',
    `assign_score`    DECIMAL(5,2) DEFAULT NULL COMMENT '派单匹配得分(0~100)',
    `assign_reason`   VARCHAR(255) DEFAULT NULL COMMENT '派单决策理由',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `is_deleted`      TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除: 0正常/1删除',
    `gmt_create`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `gmt_modified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `finish_time`     DATETIME     DEFAULT NULL COMMENT '完成时间(看板统计用)',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
    KEY `idx_student` (`student_id`),
    KEY `idx_worker` (`worker_id`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修工单表';

-- -----------------------------------------------------
-- 4. 工单附件表 repair_order_attachment
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `repair_order_attachment` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '附件ID',
    `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '工单ID',
    `file_url`     VARCHAR(500) NOT NULL COMMENT '文件地址',
    `file_type`    VARCHAR(20)  NOT NULL COMMENT '文件类型: IMAGE/VIDEO/OTHER',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单附件表';

-- -----------------------------------------------------
-- 5. 师傅专长关联表 worker_specialty
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `worker_specialty` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `worker_id`    BIGINT UNSIGNED NOT NULL COMMENT '师傅ID(逻辑关联user.id)',
    `category_id`  BIGINT UNSIGNED NOT NULL COMMENT '故障类别ID(逻辑关联repair_category.id)',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_worker_category` (`worker_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='师傅专长关联表';

-- -----------------------------------------------------
-- 6. 师傅排班表 worker_schedule
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `worker_schedule` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '排班ID',
    `worker_id`    BIGINT UNSIGNED NOT NULL COMMENT '师傅ID(逻辑关联user.id)',
    `work_date`    DATE         NOT NULL COMMENT '排班日期',
    `time_slot`    VARCHAR(20)  NOT NULL COMMENT '时间段: MORNING/AFTERNOON/EVENING',
    `area`         VARCHAR(100) DEFAULT NULL COMMENT '负责区域(楼栋范围)',
    `status`       VARCHAR(20)  NOT NULL COMMENT '状态: FREE/BUSY/REST',
    `max_orders`   INT UNSIGNED NOT NULL COMMENT '最大接单数',
    `cur_orders`   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前已接单数',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_worker_date` (`worker_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修师傅排班表';

-- -----------------------------------------------------
-- 7. 维修知识库表 knowledge_base（RAG 自助诊断）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识ID',
    `title`           VARCHAR(100) DEFAULT NULL COMMENT '标题',
    `keyword`         VARCHAR(100) NOT NULL COMMENT '关键词(用于检索与去重)',
    `question`        VARCHAR(255) NOT NULL COMMENT '问题(故障现象)',
    `answer`          TEXT         NOT NULL COMMENT '答案(自助解决方案)',
    `category_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '故障类别ID(逻辑关联repair_category.id)',
    `source`          VARCHAR(20)  NOT NULL COMMENT '来源: MANUAL(手册)/ORDER(工单沉淀)',
    `source_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源工单ID(逻辑关联repair_order.id)',
    `helpful`         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被评价有帮助次数',
    `review_status`   VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING/APPROVED/REJECTED/OFFLINE',
    `embedding`       BLOB         DEFAULT NULL COMMENT '向量化结果',
    `is_deleted`      TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除: 0正常/1删除',
    `gmt_create`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_keyword` (`keyword`),
    KEY `idx_review_status` (`review_status`),
    KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修知识库表';

-- -----------------------------------------------------
-- 8. 知识库历史表 knowledge_history（支持回滚）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_history` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `knowledge_id` BIGINT UNSIGNED NOT NULL COMMENT '知识ID(逻辑关联knowledge_base.id)',
    `title`        VARCHAR(100) DEFAULT NULL COMMENT '标题',
    `question`     VARCHAR(255) NOT NULL COMMENT '问题',
    `answer`       TEXT         NOT NULL COMMENT '答案',
    `review_status` VARCHAR(20) NOT NULL COMMENT '审核状态',
    `operator_id`  BIGINT UNSIGNED NOT NULL COMMENT '操作人ID(逻辑关联user.id)',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_knowledge` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库历史表';

-- -----------------------------------------------------
-- 9. 自助诊断会话表 diagnosis_session（RAG 问答记录）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `diagnosis_session` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '提问学生ID(逻辑关联user.id)',
    `question`      VARCHAR(500) NOT NULL COMMENT '问题',
    `answer`        TEXT         DEFAULT NULL COMMENT 'RAG返回的答案',
    `category_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '识别到的故障类别ID',
    `matched_kb_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '命中知识条ID(逻辑关联knowledge_base.id)',
    `similarity`    DECIMAL(4,3) DEFAULT NULL COMMENT '检索相似度(0~1)',
    `is_solved`     TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否自助解决: 1是/0否',
    `gmt_create`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    `gmt_modified`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_kb` (`matched_kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助诊断会话表';

-- -----------------------------------------------------
-- 10. 状态流转日志表 order_status_log
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_status_log` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '工单ID(逻辑关联repair_order.id)',
    `from_status`  VARCHAR(20)  DEFAULT NULL COMMENT '原状态',
    `to_status`    VARCHAR(20)  NOT NULL COMMENT '新状态',
    `operator_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID(系统自动操作时为空)',
    `remark`       VARCHAR(200) DEFAULT NULL COMMENT '备注(如改约原因)',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单状态流转日志表';

-- -----------------------------------------------------
-- 11. 派单记录表 assignment（记录每次派单尝试）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `assignment` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '派单ID',
    `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '工单ID(逻辑关联repair_order.id)',
    `worker_id`    BIGINT UNSIGNED NOT NULL COMMENT '师傅ID(逻辑关联user.id)',
    `assign_score` DECIMAL(5,2) NOT NULL COMMENT '匹配得分(0~100)',
    `assign_reason` VARCHAR(255) DEFAULT NULL COMMENT '决策理由',
    `is_success`   TINYINT(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否派单成功: 1成功/0失败',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '派单时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_worker` (`worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单记录表';

-- -----------------------------------------------------
-- 12. 服务评价表 order_evaluation
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_evaluation` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '工单ID(一单一评)',
    `student_id`   BIGINT UNSIGNED NOT NULL COMMENT '评价学生ID',
    `worker_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '被评师傅ID',
    `score`        TINYINT UNSIGNED NOT NULL COMMENT '评分(1-5)',
    `comment`      VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务评价表';

-- -----------------------------------------------------
-- 13. 通知消息表 notification
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID(逻辑关联user.id)',
    `order_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID(逻辑关联repair_order.id)',
    `type`         VARCHAR(20)  NOT NULL COMMENT '类型: ORDER_STATUS/SYSTEM/REMIND',
    `title`        VARCHAR(100) NOT NULL COMMENT '标题',
    `content`      VARCHAR(500) DEFAULT NULL COMMENT '内容',
    `is_read`      TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否已读: 1是/0否',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

-- -----------------------------------------------------
-- 14. 操作日志表 audit_log（管理员关键操作审计）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '操作人ID(逻辑关联user.id)',
    `operation`    VARCHAR(200) NOT NULL COMMENT '操作描述',
    `method`       VARCHAR(10)  DEFAULT NULL COMMENT '请求方法: GET/POST/PUT/DELETE',
    `params`       TEXT         DEFAULT NULL COMMENT '请求参数(JSON)',
    `ip`           VARCHAR(50)  DEFAULT NULL COMMENT '操作IP',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- -----------------------------------------------------
-- 15. 系统配置表 system_config
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_config` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key`   VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '描述',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- -----------------------------------------------------
-- 初始数据
-- -----------------------------------------------------

-- 默认管理员（密码为占位，正式接入时需 BCrypt 加密）
INSERT INTO `user` (`username`, `password`, `real_name`, `role`) VALUES
('admin', 'admin123', '系统管理员', 'ADMIN');

-- 初始故障类别（部分可自助解决）
INSERT INTO `repair_category` (`name`, `code`, `description`, `is_self_fix`) VALUES
('水电维修', 'WATER_ELECTRIC', '水龙头、电灯、插座等', 0),
('网络故障', 'NETWORK', '路由器、校园网连接问题', 1),
('门窗家具', 'FURNITURE', '门锁、抽屉、床架等', 0),
('宿舍电器', 'OTHER', '热水器、空调、洗衣机等', 0),
('报修咨询', 'OTHER', '其他咨询类问题', 1);

-- 系统配置默认值（派单权重、RAG 相似度阈值等）
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('dispatch.weight.specialty', '0.6', '派单匹配-专长匹配权重'),
('dispatch.weight.distance', '0.3', '派单匹配-就近权重'),
('dispatch.weight.load', '0.1', '派单匹配-负载权重'),
('rag.similarity.threshold', '0.8', 'RAG 检索相似度阈值');