-- 校园快修服务助手 数据库初始化脚本
-- 数据库：MySQL 8.x
-- 说明：执行本脚本前请先创建数据库：CREATE DATABASE campus_repair DEFAULT CHARACTER SET utf8mb4;

USE campus_repair;

-- -----------------------------------------------------
-- 1. 用户表（学生 / 维修师傅 / 管理员）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码(Bcrypt加密)',
    `real_name`   VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    `role`        VARCHAR(20)  NOT NULL COMMENT '角色: STUDENT/WORKER/ADMIN',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `building`    VARCHAR(50)  DEFAULT NULL COMMENT '所在楼栋(学生)',
    `room`        VARCHAR(50)  DEFAULT NULL COMMENT '房间号(学生)',
    `specialty`   VARCHAR(100) DEFAULT NULL COMMENT '擅长类别(师傅)',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------
-- 2. 故障类别表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `repair_category` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '类别ID',
    `name`         VARCHAR(50)  NOT NULL COMMENT '类别名称',
    `description`  VARCHAR(255) DEFAULT NULL COMMENT '类别描述',
    `parent_id`    BIGINT       DEFAULT NULL COMMENT '父类别ID(支持多级分类)',
    `can_self_fix` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否可自助解决: 1是/0否',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障类别表';

-- -----------------------------------------------------
-- 3. 报修工单表（核心表）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `repair_order` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `order_no`        VARCHAR(32)  NOT NULL COMMENT '工单编号',
    `idempotency_key` VARCHAR(64)  NOT NULL COMMENT '幂等键(前端生成,防重复提交)',
    `title`           VARCHAR(100) NOT NULL COMMENT '故障标题',
    `description`     TEXT         COMMENT '故障描述(原文)',
    `ai_parsed_info`  TEXT         COMMENT 'AI解析的结构化信息(JSON)',
    `ai_confidence`   FLOAT        DEFAULT NULL COMMENT 'AI置信度(0~1)',
    `category_id`     BIGINT       DEFAULT NULL COMMENT '故障类别ID',
    `emergency_level` VARCHAR(20)  NOT NULL DEFAULT 'LOW' COMMENT '紧急度: LOW/MEDIUM/HIGH',
    `student_id`      BIGINT       NOT NULL COMMENT '报修学生ID',
    `building`        VARCHAR(50)  NOT NULL COMMENT '楼栋',
    `room`            VARCHAR(50)  NOT NULL COMMENT '房间号',
    `appointment_time` DATETIME    DEFAULT NULL COMMENT '预约上门时间',
    `image_urls`      TEXT         COMMENT '照片URL列表(逗号分隔)',
    `worker_id`       BIGINT       DEFAULT NULL COMMENT '接单师傅ID',
    `assign_score`    DECIMAL(5,2) DEFAULT NULL COMMENT '派单匹配得分',
    `assign_reason`   VARCHAR(255) DEFAULT NULL COMMENT '派单决策理由',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态: SUBMITTED/ASSIGNED/ON_THE_WAY/PROCESSING/PENDING_ACCEPT/COMPLETED/CANCELLED/RESCHEDULED',
    `result`          TEXT         COMMENT '维修结论',
    `version`         BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `finish_time`     DATETIME     DEFAULT NULL COMMENT '完成时间(看板统计用)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
    KEY `idx_student` (`student_id`),
    KEY `idx_worker` (`worker_id`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修工单表';

-- -----------------------------------------------------
-- 4. 维修师傅排班表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `worker_schedule` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '排班ID',
    `worker_id`   BIGINT      NOT NULL COMMENT '师傅ID',
    `work_date`   DATE        NOT NULL COMMENT '排班日期',
    `time_slot`   VARCHAR(20) NOT NULL COMMENT '时间段: MORNING/AFTERNOON/EVENING',
    `area`        VARCHAR(50) DEFAULT NULL COMMENT '负责区域(楼栋范围)',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'FREE' COMMENT '状态: FREE/BUSY/REST',
    `max_orders`  INT         NOT NULL DEFAULT 5 COMMENT '最大接单数',
    `cur_orders`  INT         NOT NULL DEFAULT 0 COMMENT '当前已接单数',
    PRIMARY KEY (`id`),
    KEY `idx_worker_date` (`worker_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修师傅排班表';

-- -----------------------------------------------------
-- 5. 维修知识库表（RAG 自助诊断）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识ID',
    `title`        VARCHAR(100) DEFAULT NULL COMMENT '标题(兼容旧设计)',
    `keyword`      VARCHAR(100) NOT NULL COMMENT '关键词',
    `question`     VARCHAR(255) NOT NULL COMMENT '问题',
    `answer`       TEXT         NOT NULL COMMENT '答案/解决方案',
    `content`      TEXT         DEFAULT NULL COMMENT '内容(兼容旧设计)',
    `category_id`  BIGINT       DEFAULT NULL COMMENT '关联故障类别',
    `source`       VARCHAR(20)  DEFAULT NULL COMMENT '来源: MANUAL(手册)/ORDER(工单沉淀)',
    `source_order_id` BIGINT    DEFAULT NULL COMMENT '来源工单ID(工单沉淀时)',
    `helpful`      INT          NOT NULL DEFAULT 0 COMMENT '被评价有帮助次数',
    `review_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING/APPROVED/REJECTED/OFFLINE',
    `embedding`    BLOB         DEFAULT NULL COMMENT '向量化结果(可选)',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_keyword` (`keyword`),
    KEY `idx_review_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修知识库表';

-- -----------------------------------------------------
-- 6. 工单状态流转日志表（进度跟踪 + 幂等）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_status_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `order_id`    BIGINT       NOT NULL COMMENT '工单ID',
    `from_status` VARCHAR(20)  DEFAULT NULL COMMENT '原状态',
    `to_status`   VARCHAR(20)  NOT NULL COMMENT '新状态',
    `operator_id` BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单状态流转日志表';

-- -----------------------------------------------------
-- 7. 服务评价表（学生评价师傅 + 知识沉淀来源）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_evaluation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `order_id`      BIGINT       NOT NULL COMMENT '工单ID',
    `student_id`    BIGINT       NOT NULL COMMENT '评价学生ID',
    `worker_id`     BIGINT       NOT NULL COMMENT '被评师傅ID',
    `score`         INT          NOT NULL COMMENT '评分(1-5)',
    `comment`       VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    `submit_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务评价表';

-- -----------------------------------------------------
-- 8. 通知消息表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id`     BIGINT       NOT NULL COMMENT '接收用户ID',
    `order_id`    BIGINT       DEFAULT NULL COMMENT '关联工单ID',
    `type`        VARCHAR(20)  NOT NULL COMMENT '类型: ORDER_STATUS/SYSTEM/REMIND',
    `title`       VARCHAR(100) NOT NULL COMMENT '标题',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '内容',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读: 1是/0否',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

-- -----------------------------------------------------
-- 9. 自助诊断会话表（RAG 问答记录，用于自修解决率统计）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `diagnosis_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`       BIGINT       NOT NULL COMMENT '提问学生ID',
    `question`      VARCHAR(500) NOT NULL COMMENT '问题',
    `answer`        TEXT         COMMENT 'RAG返回的答案',
    `category_id`   BIGINT       DEFAULT NULL COMMENT '识别到的故障类别',
    `matched_kb_id` BIGINT       DEFAULT NULL COMMENT '命中知识条ID',
    `similarity`    DECIMAL(5,4) DEFAULT NULL COMMENT '检索相似度(0~1)',
    `is_solved`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否自助解决: 1是/0否',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_kb` (`matched_kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助诊断会话表';

-- -----------------------------------------------------
-- 初始数据：默认管理员
-- -----------------------------------------------------
-- 管理员账号：admin / admin123 (密码为占位，正式接入时需Bcrypt加密)
INSERT INTO `user` (`username`, `password`, `real_name`, `role`) VALUES
('admin', 'admin123', '系统管理员', 'ADMIN');

-- 初始故障类别（部分可自助解决）
INSERT INTO `repair_category` (`name`, `description`, `can_self_fix`) VALUES
('网络故障', '路由器、校园网连接问题', 1),
('水电维修', '水龙头、电灯、插座等', 0),
('门窗家具', '门锁、抽屉、床架等', 0),
('宿舍电器', '热水器、空调、洗衣机等', 0),
('报修咨询', '其他咨询类问题', 1);