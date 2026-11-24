-- ============================================================
-- 校园快修助手：自助诊断数据与表结构一键导入
--
-- 只处理自助诊断相关内容：
--   1. knowledge_base       知识库表
--   2. knowledge_history    知识历史表
--   3. diagnosis_session    诊断会话表
--   4. ai_usage_log         模型调用统计表
--   5. 100 条 APPROVED 自助诊断知识种子
--
-- 不包含报修、调度、认证、看板演示等其他模块的数据。
-- 新库/已有库均可执行；表使用 CREATE TABLE IF NOT EXISTS，种子可重复导入。
-- embedding 统一写 NULL，启动时由硅基流动 BAAI/bge-m3 重新向量化。
-- 安全：本文件不包含任何 API key。
--
-- 用法（仓库根目录，必须显式 utf8mb4）：
--   mysql -uroot -p --default-character-set=utf8mb4 < docs/sql/diagnosis-setup.sql
--
-- PowerShell：
--   Get-Content -Raw -Encoding UTF8 docs/sql/diagnosis-setup.sql | mysql -uroot -p --default-character-set=utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS `campus_repair`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `campus_repair`;
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- [01/05] knowledge_base 表
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- [02/05] knowledge_history 表
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- [03/05] diagnosis_session 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `diagnosis_session` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '提问学生ID(逻辑关联user.id)',
    `conversation_id` VARCHAR(64)     DEFAULT NULL COMMENT '多轮对话ID(前端生成,同一对话共用)',
    `question`        VARCHAR(500)    NOT NULL COMMENT '问题',
    `answer`          TEXT            DEFAULT NULL COMMENT 'RAG返回的答案',
    `category_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '识别到的故障类别ID',
    `matched_kb_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '命中知识条ID(逻辑关联knowledge_base.id)',
    `emergency_level` VARCHAR(16)     DEFAULT 'NORMAL' COMMENT '诊断结论紧急等级: HIGH/NORMAL',
    `similarity`      DECIMAL(4,3)    DEFAULT NULL COMMENT '检索相似度(0~1)',
    `is_solved`       TINYINT(1) UNSIGNED DEFAULT NULL COMMENT '是否自助解决: NULL未反馈/0未解决/1已解决',
    `aborted`         TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户是否主动中止: 0正常/1点停止或断链',
    `gmt_create`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    `gmt_modified`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_kb` (`matched_kb_id`),
    KEY `idx_conversation` (`conversation_id`, `user_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助诊断会话表';

-- ------------------------------------------------------------
-- [04/05] ai_usage_log 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_usage_log` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `caller`           VARCHAR(50)  NOT NULL COMMENT '调用方: repair_parse/rag_chat等',
    `model`            VARCHAR(64)  NOT NULL COMMENT '模型名',
    `is_mock`          TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否模拟调用: 0真实/1模拟',
    `success`          TINYINT(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否成功: 0失败/1成功',
    `prompt_tokens`    INT DEFAULT NULL COMMENT '提示词tokens',
    `completion_tokens` INT DEFAULT NULL COMMENT '补全tokens',
    `cost_ms`          INT DEFAULT NULL COMMENT '耗时毫秒',
    `error_message`    VARCHAR(500) DEFAULT NULL COMMENT '失败原因(成功为空)',
    `gmt_create`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    CONSTRAINT `pk_id` PRIMARY KEY (`id`),
    KEY `idx_ai_caller_time` (`caller`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用用量统计表';

-- ------------------------------------------------------------
-- [05/05] 100 条自助诊断知识种子（embedding 由后端运行时重算）
-- ------------------------------------------------------------
INSERT INTO `knowledge_base`
(`id`,`title`,`keyword`,`question`,`answer`,`category_id`,`source`,`source_order_id`,`helpful`,`review_status`,`embedding`,`is_deleted`)
VALUES
(2,_utf8mb4'电热水器不加热',_utf8mb4'热水器,不加热,温控器,加热管',_utf8mb4'电热水器插电后不加热，指示灯亮但不升温怎么办？',_utf8mb4'1.检查温控器档位是否调到最低；2.复位漏电保护开关（按一下复位按钮即可）；3.若仍不加热，多为加热管烧断，需要更换加热管，建议报修师傅处理。
成功判据：加热指示灯亮起且水温逐步上升即为成功；档位与漏保都正常仍不升温，即可判定需报修换加热管。',1,_utf8mb4'MANUAL',NULL,2,_utf8mb4'APPROVED',NULL,0),
(3,_utf8mb4'电热水器水温忽冷忽热',_utf8mb4'热水器,水温,忽冷忽热,水压',_utf8mb4'洗澡时热水器水温忽冷忽热怎么办？',_utf8mb4'1.确认热水阀门是否全开，避免洗澡时同时开多个水龙头；2.检查混水阀是否松动，向冷水侧微调；3.若水压波动大或热水器频繁启停，属于设备故障，建议报修。
成功判据：同一时间只用一个热水龙头时水温稳定即为成功；水压波动仍明显即判为设备故障需报修。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(4,_utf8mb4'路由器亮红灯断网',_utf8mb4'路由器,红灯,断网,校园网',_utf8mb4'宿舍路由器亮红灯，全屋断网怎么办？',_utf8mb4'1.断电重启光猫和路由器，等待 2 分钟；2.检查网线是否插紧；3.登录校园网认证页面重新认证；4.若光猫亮红灯说明线路故障，报修运营商。
成功判据：重启后路由指示灯恢复常亮、网页能打开即为成功；光猫仍亮红灯即判为线路故障。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(5,_utf8mb4'宿舍断电跳闸',_utf8mb4'跳闸,断电,空开,电闸',_utf8mb4'宿舍突然全部断电了怎么办？',_utf8mb4'1.找到宿舍电闸箱，查看空气开关是否跳到 OFF 位；2.先拔掉大功率电器（电吹风、电煮锅等）再合闸；3.反复跳闸说明有短路或漏电，立即报修。
成功判据：拔掉大功率电器后合闸不再跳即为成功；反复跳闸即判为短路或漏电，停止合闸并报修。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(6,_utf8mb4'水龙头漏水',_utf8mb4'水龙头,漏水,阀门,垫片',_utf8mb4'水龙头关不紧一直滴水怎么办？',_utf8mb4'1.顺时针拧紧阀芯试试；2.滴水不止说明阀芯垫片老化，可自行更换阀芯；3.动手能力有限建议报修，师傅带全套工具可快速换好。
成功判据：拧紧阀芯后不再滴水即为成功；仍持续滴水即判为垫片老化，需报修更换。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(7,_utf8mb4'门锁打不开',_utf8mb4'门锁,打不开,锁芯,钥匙',_utf8mb4'宿舍门锁钥匙拧不动打不开怎么办？',_utf8mb4'1.往锁孔里滴少量铅笔芯粉或润滑油再试；2.检查是否门体变形导致锁舌卡住，可轻轻推门同时拧钥匙；3.切勿暴力踹门，直接报修换锁芯更安全。
成功判据：润滑或推门辅助后钥匙能顺畅转动即为成功；仍打不开即报修换锁芯，不要暴力开门。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(8,_utf8mb4'台灯不亮',_utf8mb4'台灯,不亮,灯泡,接触不良',_utf8mb4'台灯突然不亮了怎么排查？',_utf8mb4'1.先换一个灯泡测试，这是最常见原因；2.检查插头和开关处接触是否松动；3.若灯泡和接触都正常，是灯座线路问题，报修处理。
成功判据：换灯泡后能正常亮即为成功；灯泡与接触均正常仍不亮，即判为灯座线路故障。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(9,_utf8mb4'马桶堵塞',_utf8mb4'马桶,堵塞,疏通,皮搋子',_utf8mb4'宿舍马桶堵了怎么办？',_utf8mb4'1.用皮搋子对准排水口反复抽压 5 到 10 次；2.倒入热水加洗洁精静置 20 分钟再冲；3.不要用铁丝乱捅会刮伤陶瓷；4.疏通无效报修专业疏通。
成功判据：皮搋子疏通后能正常冲水即为成功；仍不下水即报修专业疏通。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(10,_utf8mb4'水龙头出水量小',_utf8mb4'水龙头,出水小,水压,滤网',_utf8mb4'水龙头出水越来越小，水流很细怎么办？',_utf8mb4'1.拆下水龙头起泡器，冲洗滤网上的水垢和泥沙；2.检查角阀是否完全打开；3.整间宿舍都变小可能是水压问题，报修师傅上门检查。
成功判据：清洗起泡器滤网后水流恢复即为成功；全屋出水都小即判为水压问题。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(11,_utf8mb4'花洒出水忽大忽小',_utf8mb4'花洒,出水不均,水压,堵塞',_utf8mb4'洗澡花洒出水忽大忽小还刺痛皮肤怎么办？',_utf8mb4'1.拆下花洒面板用白醋浸泡除水垢；2.检查花洒软管是否被压弯打结；3.清理后仍不稳定可能是混水阀或水压问题，报修处理。
成功判据：除垢理顺软管后出水均匀即为成功；仍忽大忽小即报修检查混水阀或水压。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(12,_utf8mb4'洗手池下水管漏水',_utf8mb4'洗手池,下水管,漏水,存水弯',_utf8mb4'洗手池下面一直滴水怎么办？',_utf8mb4'1.先拧紧下水器与台盆之间的螺母；2.检查存水弯接口胶垫是否错位，重新旋紧；3.用干纸巾擦干后观察半小时，仍漏水就报修更换管件。
成功判据：重新旋紧接口并用干纸巾观察后不再渗水即为成功；仍滴水即报修换管件。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(13,_utf8mb4'地漏反味返水',_utf8mb4'地漏,反味,返水,防臭芯',_utf8mb4'卫生间地漏有臭味还往上返水怎么办？',_utf8mb4'1.取出地漏芯清洗毛发和污垢，装回防臭芯；2.往地漏倒一盆清水建立水封；3.冲洗后仍返水说明管道堵塞，报修疏通。
成功判据：清洗地漏芯补水后无臭味不返水即为成功；仍返水即判为管道堵塞报修。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(14,_utf8mb4'卫生间天花板渗水',_utf8mb4'天花板,渗水,漏水,楼上',_utf8mb4'卫生间天花板出现水渍、滴水怎么办？',_utf8mb4'1.先拍照记录渗水位置和时间；2.暂时避开渗水区域，防止滑倒；3.及时报修并说明是否楼上用水后加重，师傅会检查管道和防水层。
成功判据：避开渗水区并拍照记录后即完成初步处理；渗水持续即需报修检查管道防水层。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(15,_utf8mb4'水管接头滴水',_utf8mb4'水管,接头,滴水,角阀',_utf8mb4'洗手台下面的水管接头一直渗水怎么办？',_utf8mb4'1.关闭角阀，用干布擦干接头；2.用扳手轻轻拧紧接口螺母，不要过度用力；3.拧紧后仍渗水说明密封圈老化，报修更换。
成功判据：关闭角阀擦干后拧紧接头不再渗水即为成功；仍渗水即判为密封圈老化报修。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(16,_utf8mb4'电热水器漏水',_utf8mb4'热水器,漏水,安全阀,进水阀',_utf8mb4'电热水器底部漏水还能继续用吗？',_utf8mb4'1.立即拔掉电源插头，关闭进水阀；2.不要继续洗澡或触摸漏电部位；3.拍照记录漏水位置并报修，师傅确认后再恢复使用。
成功判据：拔电源关进水阀即完成安全处置；底部持续漏水必须报修确认后才能复用。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(17,_utf8mb4'电热水器跳闸',_utf8mb4'热水器,跳闸,漏电保护,大功率',_utf8mb4'一开电热水器就跳闸怎么办？',_utf8mb4'1.先断开热水器电源，复位空开；2.检查是否同时使用电吹风等大功率电器；3.单独开启仍跳闸可能是漏电或加热管故障，停止使用并报修。
成功判据：单独开启不再跳闸即为成功；仍跳闸即判为漏电或加热管故障，停用并报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(18,_utf8mb4'淋浴水温调节失灵',_utf8mb4'淋浴,水温,混水阀,调节失灵',_utf8mb4'淋浴开关调不动，水温只能烫或只能冷怎么办？',_utf8mb4'1.轻轻来回转动混水阀手柄，不要硬拧；2.清理阀柄周围水垢；3.阀芯损坏会导致无法调温，报修更换混水阀。
成功判据：清理水垢后手柄能顺畅调温即为成功；阀芯损坏即报修更换混水阀。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(19,_utf8mb4'开水器不出热水',_utf8mb4'开水器,不出热水,指示灯,宿舍楼',_utf8mb4'宿舍楼开水器不出热水怎么办？',_utf8mb4'1.查看设备是否显示加热中或故障代码；2.确认刷卡或扫码流程是否完成；3.长时间无热水不要自行拆机，联系宿管或报修。
成功判据：设备显示正常加热且能放出热水即为成功；长时间无热水不要拆机，联系宿管或报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(20,_utf8mb4'饮水机漏水',_utf8mb4'饮水机,漏水,水桶,接口',_utf8mb4'饮水机不断漏水怎么处理？',_utf8mb4'1.先拔掉电源，检查水桶是否放正、桶口是否破损；2.擦干聪明座和接口后重新安装水桶；3.仍漏水可能是内部阀门损坏，报修更换。
成功判据：水桶放正接口擦干后不再漏水即为成功；仍漏水即内部阀门损坏报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(21,_utf8mb4'插座没电',_utf8mb4'插座,没电,空开,插排',_utf8mb4'墙上插座突然没电，充电器插上去没反应怎么办？',_utf8mb4'1.换一个已知正常的电器测试，排除电器本身故障；2.检查对应空开是否跳闸，复位后再试；3.单个插座仍无电不要自行拆开，报修检查线路。
成功判据：换电器测试并复位空开后插座恢复供电即为成功；单插座仍无电不要拆开，报修查线。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(22,_utf8mb4'插座发烫有焦味',_utf8mb4'插座,发烫,焦味,异味,安全',_utf8mb4'插座摸起来发烫还有塑料焦味怎么办？',_utf8mb4'1.立即停止使用并拔掉插头，必要时先断开对应空开；2.不要继续插电测试，也不要自行拆修；3.远离易燃物并尽快报修，由师傅检查更换。
成功判据：停用断电并远离易燃物即完成安全处置；焦味未消除前不得再通电，尽快报修更换。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(23,_utf8mb4'空气开关反复跳闸',_utf8mb4'空开,反复跳闸,短路,漏电',_utf8mb4'空气开关合上几分钟又跳闸怎么办？',_utf8mb4'1.先拔掉所有大功率电器再试合一次；2.若仍跳闸，说明线路或设备存在短路、漏电；3.不要反复强行合闸，报修排查。
成功判据：拔掉大功率电器后合闸稳定即为成功；仍跳闸即判为线路短路漏电，报修排查。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(24,_utf8mb4'宿舍灯不亮',_utf8mb4'宿舍灯,不亮,灯管,镇流器',_utf8mb4'宿舍顶灯突然不亮了怎么办？',_utf8mb4'1.确认空开没有跳闸，开关是否接触良好；2.更换同规格灯管测试；3.换灯后仍不亮可能是镇流器或线路问题，报修处理。
成功判据：换同规格灯管后能亮即为成功；仍不亮即判为镇流器或线路故障报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(25,_utf8mb4'灯管闪烁',_utf8mb4'灯管,闪烁,接触不良,镇流器',_utf8mb4'日光灯一直闪烁，晃得难受怎么办？',_utf8mb4'1.关闭电源后重新安装灯管，检查两端卡口是否松动；2.仍闪烁多为镇流器老化，不要长期通电使用；3.报修更换镇流器或灯具。
成功判据：断电重装灯管后不再闪烁即为成功；仍闪烁即镇流器老化，停用并报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(26,_utf8mb4'智能门锁没电',_utf8mb4'智能门锁,没电,应急钥匙,门锁',_utf8mb4'智能门锁没电打不开门怎么办？',_utf8mb4'1.用配套应急钥匙或机械钥匙开门；2.找到门外的应急充电接口，用充电宝临时供电；3.开门后及时更换电池，仍无法开则报修。
成功判据：应急钥匙或充电宝供电后能开门即为成功；开门后换电池，仍打不开报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(27,_utf8mb4'门禁刷卡无反应',_utf8mb4'门禁,刷卡无反应,校园卡,读卡器',_utf8mb4'宿舍门禁刷卡没反应进不去怎么办？',_utf8mb4'1.擦干净校园卡和读卡器表面再试；2.换一张卡测试，确认是否卡片消磁；3.多人都刷不开说明设备故障，联系宿管或报修。
成功判据：擦净卡面换卡测试后能开门即为成功；多卡都失败即判为设备故障，联系宿管。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(28,_utf8mb4'窗户关不严',_utf8mb4'窗户,关不严,锁点,密封条',_utf8mb4'宿舍窗户关不严，刮风一直响怎么办？',_utf8mb4'1.检查窗框导轨是否有异物，清理后重新推拉；2.调节锁点螺丝让窗扇压紧；3.密封条脱落可先临时按回，仍漏风报修更换。
成功判据：清理导轨并调锁点后窗户能压紧不响即为成功；仍漏风报修换密封条。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(29,_utf8mb4'门合页异响下垂',_utf8mb4'门,合页,异响,下垂',_utf8mb4'宿舍门开关时嘎吱响还往下坠怎么办？',_utf8mb4'1.在合页处滴少量润滑油，开关几次；2.用螺丝刀拧紧合页固定螺丝；3.门体明显下垂影响上锁时报修调整合页。
成功判据：滴润滑油紧螺丝后开关无异响即为成功；门体仍下垂影响上锁时报修调合页。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(30,_utf8mb4'抽屉滑轨卡住',_utf8mb4'抽屉,滑轨,卡住,家具',_utf8mb4'书桌抽屉拉不动或卡住了怎么办？',_utf8mb4'1.先清空抽屉，检查是否有异物卡住滑轨；2.轻拉轻推并用干布擦掉滑轨灰尘；3.滑轨变形或脱落时不要硬拽，报修更换。
成功判据：清空异物擦净滑轨后能顺畅拉动即为成功；滑轨变形不要硬拽，报修更换。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(31,_utf8mb4'书桌晃动',_utf8mb4'书桌,晃动,螺丝,家具',_utf8mb4'书桌老是晃，写字很不稳怎么办？',_utf8mb4'1.检查桌腿和横撑螺丝是否松动，用扳手拧紧；2.地面不平可在桌脚垫硬纸板临时找平；3.桌体变形或焊点开裂报修处理。
成功判据：拧紧螺丝垫平桌脚后不晃即为成功；桌体变形或焊点开裂报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(32,_utf8mb4'床架异响',_utf8mb4'床架,异响,松动,螺丝',_utf8mb4'上下铺床架一翻身就吱呀响怎么办？',_utf8mb4'1.检查床架连接处螺丝，逐个拧紧；2.检查护栏和梯步是否松动，有松动立即停止攀爬；3.仍有异响或结构变形报修。
成功判据：拧紧螺丝后翻身无异响、护栏牢固即为成功；结构变形立即停用并报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(33,_utf8mb4'衣柜门脱轨',_utf8mb4'衣柜,门,脱轨,滑轨',_utf8mb4'衣柜推拉门滑不动或掉出来了怎么办？',_utf8mb4'1.不要强行推拉，先观察下轨是否有异物；2.把门扇抬起重回下轨，再装回上轨导向轮；3.轨道变形或滑轮损坏报修更换。
成功判据：清轨后门扇能顺畅滑动即为成功；轨道变形滑轮损坏报修更换。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(34,_utf8mb4'窗帘杆掉落',_utf8mb4'窗帘杆,掉落,膨胀螺丝,墙面',_utf8mb4'窗帘杆掉了下来怎么临时处理？',_utf8mb4'1.先取下窗帘避免砸伤，妥善收好；2.检查墙面膨胀螺丝是否松脱，不要继续悬挂重物；3.报修师傅重新安装并加固。
成功判据：取下窗帘避免砸伤即完成临时处置；墙面松脱不得继续挂重物，报修加固。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(35,_utf8mb4'空调不制冷',_utf8mb4'空调,不制冷,滤网,外机',_utf8mb4'宿舍空调开着但不凉快怎么办？',_utf8mb4'1.清洗室内机滤网，检查出风口是否被遮挡；2.确认模式为制冷、温度设置合理；3.仍不制冷可能是缺氟或外机故障，报修检查。
成功判据：清洗滤网设对制冷模式后出风明显变凉即为成功；仍不制冷即报修查缺氟或外机。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(36,_utf8mb4'空调室内机漏水',_utf8mb4'空调,漏水,排水管,室内机',_utf8mb4'空调室内机一直往下滴水怎么办？',_utf8mb4'1.先接住滴水，避免地面湿滑；2.检查滤网是否太脏，清洗后观察；3.持续滴水多为排水管堵塞或安装倾斜，报修疏通调整。
成功判据：接水防滑并清洗滤网后不再滴水即为成功；持续滴水即排水管堵塞或安装倾斜，报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(37,_utf8mb4'空调遥控器失灵',_utf8mb4'空调遥控器,失灵,电池,接收板',_utf8mb4'空调遥控器按了没反应怎么办？',_utf8mb4'1.更换电池并确认正负极方向；2.用手机摄像头检查遥控发射头是否亮紫光；3.遥控正常但空调无反应可能是接收板故障，报修处理。
成功判据：换电池后遥控有反应即为成功；遥控正常空调无反应即接收板故障，报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(38,_utf8mb4'电风扇不转',_utf8mb4'电风扇,不转,电容,扇叶',_utf8mb4'电风扇通电但不转怎么办？',_utf8mb4'1.断电后转动扇叶，检查是否被卡住或积灰太多；2.通电后听到嗡嗡声但不转，多为电容损坏；3.不要自行拆电机，报修更换。
成功判据：断电清灰后扇叶转动正常即为成功；嗡嗡响不转即电容损坏，报修换电容。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(39,_utf8mb4'WiFi能连上但没网',_utf8mb4'WiFi,连上没网,DNS,校园网',_utf8mb4'手机连上WiFi但网页打不开怎么办？',_utf8mb4'1.忘记网络后重新连接，确认密码正确；2.重启路由器和光猫，等待 2 分钟；3.检查是否完成校园网认证，仍无网报修。
成功判据：重连 WiFi 并完成认证后能打开网页即为成功；仍无网即报修检查线路设备。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(40,_utf8mb4'校园网认证失败',_utf8mb4'校园网,认证失败,账号,密码',_utf8mb4'校园网认证页面一直提示账号或密码错误怎么办？',_utf8mb4'1.确认使用学校统一账号，注意大小写和是否开启 Caps Lock；2.通过官方渠道重置密码后重试；3.账号正常但认证失败，报修网络故障。
成功判据：重置密码后认证通过即为成功；账号正常仍认证失败即报修网络故障。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(41,_utf8mb4'网口灯不亮',_utf8mb4'网口,网灯不亮,网线,路由器',_utf8mb4'电脑插上网线但网口灯不亮怎么办？',_utf8mb4'1.换一根已知正常的网线测试；2.检查网线两端是否插到底；3.换路由器 LAN 口仍不亮，可能是网口或网卡故障，报修检测。
成功判据：换网线换 LAN 口后网口灯亮即为成功；仍不亮即判为网口或网卡故障报修。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(42,_utf8mb4'网速慢延迟高',_utf8mb4'网速慢,延迟高,WiFi,带宽',_utf8mb4'宿舍网速很慢、游戏延迟高怎么办？',_utf8mb4'1.靠近路由器或改连 5GHz WiFi；2.暂停他人的下载和投屏，避开晚高峰；3.测速仍明显偏低，报修师傅检查线路和设备。
成功判据：靠近路由器或换 5GHz 后测速恢复正常即为成功；仍明显偏低报修检查线路。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(43,_utf8mb4'路由器频繁掉线',_utf8mb4'路由器,掉线,重启,过热',_utf8mb4'路由器每隔几分钟就断一次网怎么办？',_utf8mb4'1.把路由器放到通风位置，清理灰尘避免过热；2.更新固件或恢复出厂设置后重新配置；3.仍频繁掉线可能是设备故障，报修更换。
成功判据：通风清灰后不再频繁掉线即为成功；仍掉线即设备故障报修更换。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(44,_utf8mb4'洗衣机不脱水',_utf8mb4'洗衣机,不脱水,衣物,平衡',_utf8mb4'宿舍洗衣机洗完不脱水怎么办？',_utf8mb4'1.把衣物重新抖散均匀分布，避免单侧堆积；2.确认机盖完全关好，安全开关是否弹回；3.仍不脱水可能是皮带或电机故障，报修。
成功判据：衣物抖散关好机盖后能正常脱水即为成功；仍不脱水即皮带电机故障报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(45,_utf8mb4'洗衣机不排水',_utf8mb4'洗衣机,不排水,过滤器,排水管',_utf8mb4'洗衣机洗完后桶里还有水怎么办？',_utf8mb4'1.检查排水管是否弯折或插得太高；2.断电后清理右下角过滤器里的硬币和毛发；3.仍不排水报修检修排水泵。
成功判据：理顺排水管清理过滤器后能正常排水即为成功；仍积水即排水泵故障报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(46,_utf8mb4'烘干机不工作',_utf8mb4'烘干机,不工作,滤网,电源',_utf8mb4'公共烘干机启动后不转也不加热怎么办？',_utf8mb4'1.检查是否投币或扫码成功，确认电源指示灯；2.清理滤网和绒毛，确认门已关紧；3.仍不工作不要反复投币，联系宿管或报修。
成功判据：确认支付清滤网关门后能启动即为成功；仍不工作不要重复投币，联系宿管报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(47,_utf8mb4'微波炉不加热',_utf8mb4'微波炉,不加热,门,磁控管',_utf8mb4'微波炉灯亮转盘转但食物不热怎么办？',_utf8mb4'1.确认炉门完全关好，门钩是否损坏；2.不要空载运行，检查是否误设了解冻或低火档；3.仍不加热可能是磁控管故障，停止使用并报修。
成功判据：关好炉门选对火力后能正常加热即为成功；仍不加热即磁控管故障，停用报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(48,_utf8mb4'电梯故障困人',_utf8mb4'电梯,困人,故障,报警',_utf8mb4'被困在电梯里应该怎么办？',_utf8mb4'1.保持冷静，按紧急报警按钮并拨打值班电话；2.不要扒门或自行攀爬；3.等待专业人员救援，脱困后向宿管反馈并记录电梯编号。
成功判据：按报警按钮并拨打值班电话等待救援即完成正确处置；切勿扒门攀爬。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(49,_utf8mb4'公共区域漏水',_utf8mb4'走廊,漏水,水表间,公共区域',_utf8mb4'走廊或水表间一直滴水，地面很滑怎么办？',_utf8mb4'1.避开积水区域，提醒周围同学防滑；2.拍照记录位置和滴水量；3.及时报修，说明具体楼层和房间附近位置。
成功判据：避开积水拍照记录并提醒同学即完成现场处置；地面持续滴水及时报修。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(50,_utf8mb4'报修进度查询',_utf8mb4'报修进度,工单,查询,处理中',_utf8mb4'之前提交的报修单，在哪里看处理进度？',_utf8mb4'1.打开系统的报修工单页面，查看状态是待接单、处理中还是已完成；2.状态长时间不变可联系宿管确认；3.紧急情况不要等工单，直接联系值班人员。
成功判据：在工单页确认状态即完成查询；状态长期不变联系宿管，紧急情况直接找值班人员。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(51,_utf8mb4'报修需要准备什么',_utf8mb4'报修,准备信息,照片,位置',_utf8mb4'第一次使用报修，需要准备哪些信息？',_utf8mb4'1.写清楚宿舍楼栋、房间号和故障位置；2.描述故障现象和开始时间，尽量上传照片；3.留下联系电话，方便师傅上门前联系。
成功判据：写清楼栋房间现象并上传照片、留电话即为完整报修信息。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(52,_utf8mb4'马桶水箱一直流水',_utf8mb4'马桶,水箱,流水,浮球',_utf8mb4'马桶冲完后水箱一直哗哗流水怎么办？',_utf8mb4'1.打开水箱盖，检查浮球是否卡住或位置过高；2.轻压浮球、调整水位到溢流管下方；3.若排水阀密封圈老化导致一直漏水，报修更换。
成功判据：调整浮球后水位停止上升且不再从溢流管流水即为成功；密封圈老化漏水报修更换。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(53,_utf8mb4'马桶冲水无力',_utf8mb4'马桶,冲水无力,水位,堵塞',_utf8mb4'马桶按下冲水后冲不干净怎么办？',_utf8mb4'1.检查水箱水位是否过低，调整浮球提高水位；2.用马桶刷清理出水口水垢；3.仍冲不干净可能是管道堵塞，报修疏通。
成功判据：水位正常且出水通畅能一次冲净即为成功；反复堵塞报修疏通。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(54,_utf8mb4'地漏排水慢',_utf8mb4'地漏,排水慢,堵塞,毛发',_utf8mb4'卫生间地漏下水很慢，洗澡水排不走怎么办？',_utf8mb4'1.取出地漏芯，清理毛发和杂物；2.用热水加少量洗洁精冲洗管道；3.排水仍慢说明管道有堵塞，报修疏通。
成功判据：倒水后能快速流走且不积水即为成功；持续积水报修疏通。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(55,_utf8mb4'洗手池下水堵塞',_utf8mb4'洗手池,下水堵塞,皮搋子,返水',_utf8mb4'洗手池放水后一直不下去还往上返水怎么办？',_utf8mb4'1.先清空台盆积水，取出下水口的滤网和堵头；2.用皮搋子对准下水口抽压数次；3.不要用强腐蚀剂，堵塞未通报修疏通。
成功判据：放水后水位快速下降且无返水即为成功；仍堵塞报修疏通。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(56,_utf8mb4'水管水锤异响',_utf8mb4'水管,异响,水锤,角阀',_utf8mb4'关水龙头时墙里咚咚响怎么办？',_utf8mb4'1.记录异响出现的用水时段，避免同时快速开关多个水龙头；2.检查水龙头和角阀是否松动，轻轻拧紧；3.持续剧烈敲击声报修检查管道固定和减压阀。
成功判据：缓慢开关水龙头后异响消失即为成功；持续敲击声报修处理。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(57,_utf8mb4'阀门锈死拧不动',_utf8mb4'阀门,锈死,拧不动,角阀',_utf8mb4'水龙头下面的阀门锈死了拧不动怎么办？',_utf8mb4'1.不要用力硬拧，先喷少量除锈润滑剂等待几分钟；2.用干布防滑后轻轻转动阀门；3.阀门仍不动或滴水报修更换，避免拧断管件。
成功判据：阀门能顺畅转动且接口不渗水即为成功；锈死或渗水报修更换。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(58,_utf8mb4'水龙头把手松动',_utf8mb4'水龙头,把手,松动,阀芯',_utf8mb4'水龙头开关把手松松垮垮怎么办？',_utf8mb4'1.关闭角阀，擦干水龙头根部；2.找到把手下方紧固螺丝并用合适螺丝刀拧紧；3.把手脱落或阀芯晃动报修更换阀芯。
成功判据：把手牢固、开关顺畅且手柄不晃动即为成功；阀芯松动报修更换。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(59,_utf8mb4'漏电保护器复位不了',_utf8mb4'漏保,复位不了,跳闸,漏电',_utf8mb4'漏电保护器的复位按钮按不下去怎么办？',_utf8mb4'1.先拔掉该回路所有电器插头，再尝试按下漏电保护器复位按钮；2.若仍弹起，说明线路或设备存在漏电，不要反复硬按；3.立即报修排查，等待师傅处理后再用电。
成功判据：断开电器后能稳定复位且不再跳闸即为成功；仍弹起严禁自行合闸并报修。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(60,_utf8mb4'插排发热冒烟',_utf8mb4'插排,发热,冒烟,安全',_utf8mb4'插排摸起来发烫还冒烟了怎么办？',_utf8mb4'1.立即断开插排电源，拔掉所有插头；2.不要继续使用该插排，也不要用水浇；3.远离窗帘被褥等易燃物并尽快报修或更换合格插排。
成功判据：断电后发热冒烟停止且环境安全即为成功；插排变形烧焦不得再次使用。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(61,_utf8mb4'楼道声控灯不亮',_utf8mb4'声控灯,楼道,不亮,开关',_utf8mb4'楼道声控灯拍手也不亮怎么办？',_utf8mb4'1.在灯下拍手或跺脚，确认是否超过声控延时时间；2.观察同层其他声控灯是否同样不亮；3.多盏灯都不亮或长明不灭，联系宿管或报修检查线路和声控开关。
成功判据：拍手后灯能正常亮起并在延时后熄灭即为成功；整层异常报修。',1,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(62,_utf8mb4'认证成功仍无网',_utf8mb4'校园网,认证成功,无网,重连',_utf8mb4'校园网页面显示认证成功，但手机还是上不了网怎么办？',_utf8mb4'1.关闭 WiFi 后重新连接，让设备重新获取地址；2.打开浏览器访问任意网页触发认证跳转，确认没有停留在登录缓存页；3.重启路由器和光猫，仍无网报修网络故障。
成功判据：重新连接后能打开网页即为成功；认证状态正常仍无法上网报修。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(63,_utf8mb4'内网正常外网不通',_utf8mb4'校园网,内网,外网,代理',_utf8mb4'电脑能打开校内网站但上不了外网怎么办？',_utf8mb4'1.确认校园网账号未欠费或过期；2.检查浏览器代理设置是否误开，关闭代理后重试；3.内网正常外网不通多为网关或 DNS 异常，报修处理。
成功判据：内网资源可访问且外网网页能打开即为成功；仅内网可用报修网络。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(64,_utf8mb4'WiFi反复弹认证',_utf8mb4'WiFi,认证弹窗,缓存,校园网',_utf8mb4'手机连上 WiFi 后一直弹校园网认证怎么办？',_utf8mb4'1.先退出校园网账号再重新登录，避免旧会话冲突；2.忘记网络后重新连接，清除浏览器缓存；3.多台设备反复弹认证说明认证系统或设备异常，联系宿管并报修。
成功判据：登录一次后能稳定上网且不再反复弹窗即为成功；多设备异常报修。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(65,_utf8mb4'5GHz WiFi搜不到',_utf8mb4'5GHz,WiFi,路由器,搜不到',_utf8mb4'手机搜不到宿舍路由器的 5GHz 信号怎么办？',_utf8mb4'1.确认手机或电脑支持 5GHz，部分旧设备只能连 2.4GHz；2.登录路由器管理页检查 5GHz 开关是否开启、地区设置是否正确；3.近距离仍搜不到且路由器设置正常，报修检测设备。
成功判据：设备能搜索并连接 5GHz 信号且速率正常即为成功；设备不支持时改用 2.4GHz。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(66,_utf8mb4'光猫LOS红灯',_utf8mb4'光猫,LOS,红灯,光纤',_utf8mb4'光猫 LOS 灯亮红灯，宽带一直掉线怎么办？',_utf8mb4'1.检查光纤线是否松动或被弯折压住，保持自然弧度；2.断电重启光猫，等待光信号灯稳定；3.LOS 持续红灯说明入户线路故障，报修运营商或网络师傅。
成功判据：LOS 灯熄灭、光信号灯稳定且能正常上网即为成功；持续红灯报修线路。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(67,_utf8mb4'路由器WAN口灯不亮',_utf8mb4'路由器,WAN口,灯不亮,网线',_utf8mb4'电脑连上路由器但 WAN 口灯不亮怎么办？',_utf8mb4'1.检查网线是否从光猫网口接到路由器 WAN 口，并插到底；2.换一根网线或换光猫 LAN 口测试；3.端口灯仍不亮可能是路由器 WAN 口故障，报修更换设备。
成功判据：WAN 口灯常亮或闪烁且能获取地址即为成功；端口无反应报修更换。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(68,_utf8mb4'DNS解析失败',_utf8mb4'DNS,解析失败,网页打不开,校园网',_utf8mb4'网页提示 DNS 解析失败怎么办？',_utf8mb4'1.先判断其他同学同一网络是否也打不开网页；2.将 DNS 改为学校推荐地址或公共 DNS 后重试；3.更换 DNS 仍解析失败，报修检查网络配置。
成功判据：输入网址能正常解析并打开页面即为成功；多人解析失败报修网络。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(69,_utf8mb4'网线水晶头松动',_utf8mb4'网线,水晶头,松动,网口灯',_utf8mb4'电脑插上网线后接触不良、网口灯忽亮忽灭怎么办？',_utf8mb4'1.捏住水晶头重新插到底，听到卡扣轻响；2.检查水晶头金属触点是否氧化发黑，可用橡皮轻擦；3.插槽松动或反复接触不良，报修更换网线或网口模块。
成功判据：插紧后网口灯稳定亮起且网络不掉线即为成功；接触不良报修更换。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(70,_utf8mb4'测速正常网页慢',_utf8mb4'测速,网页慢,浏览器,带宽',_utf8mb4'测速很快但打开网页很慢怎么办？',_utf8mb4'1.先换浏览器或无痕窗口测试，排除插件和缓存影响；2.关闭下载、投屏和云同步占用带宽的应用；3.测速正常但特定网站慢，多为外部服务问题；所有网页都慢报修网络。
成功判据：常用网页能流畅打开且延迟稳定即为成功；全体异常报修网络。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(71,_utf8mb4'共享打印机连不上',_utf8mb4'打印机,共享,连不上,驱动',_utf8mb4'电脑搜索不到宿舍楼共享打印机怎么办？',_utf8mb4'1.确认打印机已开机且联网，电脑和打印机在同一网络；2.删除旧的打印机驱动后重新添加共享打印机；3.多人无法连接，报修检查打印机服务端。
成功判据：电脑能发现打印机并成功打印测试页即为成功；多人失败报修。',2,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(72,_utf8mb4'钥匙断在锁孔',_utf8mb4'钥匙,断,锁孔,门锁',_utf8mb4'门锁钥匙断在锁孔里了怎么办？',_utf8mb4'1.不要继续往里推，尝试用镊子夹住露出部分轻轻取出；2.断得太深不要自行掏挖，避免损坏锁芯；3.取不出或门无法上锁，报修师傅处理并更换钥匙。
成功判据：断钥匙完整取出且钥匙能正常开锁即为成功；无法取出报修换锁芯。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(73,_utf8mb4'门锁锁舌对不齐',_utf8mb4'门锁,锁舌,错位,门框',_utf8mb4'关门时锁舌对不上门框怎么办？',_utf8mb4'1.关门时轻轻推拉门扇，观察锁舌是否碰到门框；2.检查门框锁扣板螺丝是否松动，必要时拧紧；3.门体变形导致长期错位，报修调整门框或合页。
成功判据：门锁能顺畅上锁且门扇无明显歪斜即为成功；持续错位报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(74,_utf8mb4'窗户玻璃破损',_utf8mb4'窗户,玻璃,破损,安全',_utf8mb4'宿舍窗户玻璃裂了怎么临时处理？',_utf8mb4'1.先远离碎玻璃并提醒同学不要靠近；2.用胶带在裂纹处贴成网格，防止碎片掉落；3.拍照记录位置并尽快报修更换玻璃，不要自行拆卸。
成功判据：临时固定后无碎片掉落且已提交报修即为成功；破损玻璃必须报修更换。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(75,_utf8mb4'纱窗脱落',_utf8mb4'纱窗,脱落,滑轨,卡扣',_utf8mb4'宿舍纱窗掉了出来怎么办？',_utf8mb4'1.先取下纱窗，检查边框是否变形；2.将纱窗沿滑轨重新装入，确认卡扣到位；3.边框变形或纱网破洞较大，报修更换纱窗。
成功判据：纱窗能顺畅推拉且卡扣牢固即为成功；变形破损报修更换。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(76,_utf8mb4'椅子螺丝松',_utf8mb4'椅子,螺丝,松动,家具',_utf8mb4'椅子坐上去总是晃怎么办？',_utf8mb4'1.翻过椅子检查座面和椅腿连接螺丝，逐个拧紧；2.检查椅脚防滑垫是否脱落，缺失可临时垫平；3.椅背或支架开裂报修，不要继续承重。
成功判据：椅子坐上去不摇晃且螺丝无松动即为成功；支架开裂报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(77,_utf8mb4'床护栏松动',_utf8mb4'床护栏,松动,安全,上铺',_utf8mb4'上铺床护栏晃得厉害怎么办？',_utf8mb4'1.立即停止攀爬上铺，检查护栏固定螺丝；2.用工具拧紧松动的连接件，确认护栏无明显晃动；3.护栏变形或焊点开裂报修，修复前不要睡上铺。
成功判据：护栏牢固无晃动且螺丝拧紧即为成功；结构变形必须报修停用。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(78,_utf8mb4'衣柜隔板下垂',_utf8mb4'衣柜,隔板,下垂,层板托',_utf8mb4'衣柜隔板放了书以后往下弯怎么办？',_utf8mb4'1.取下隔板上的重物，查看层板托是否脱落；2.把层板托重新卡入预留孔位，轻轻压回隔板；3.孔位损坏或层板开裂报修更换配件。
成功判据：隔板放平后能承重且不继续下垂即为成功；层板开裂报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(79,_utf8mb4'书架层板掉落',_utf8mb4'书架,层板,掉落,卡槽',_utf8mb4'书架层板突然掉下来怎么办？',_utf8mb4'1.先取下上方书籍，避免二次掉落砸伤；2.检查层板托和卡槽是否完好，按原位装回；3.卡槽断裂或层板变形报修处理。
成功判据：层板装回后水平稳固且能正常放书即为成功；卡槽损坏报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(80,_utf8mb4'桌面板翘边开裂',_utf8mb4'书桌,翘边,开裂,封边',_utf8mb4'桌面封边翘起来还裂了怎么办？',_utf8mb4'1.清理桌面杂物，检查翘边处封边条是否脱落；2.可用木工胶临时粘合封边并压平，等待完全干燥；3.桌面大面积开裂或影响承重时报修更换。
成功判据：封边粘牢后桌面平整无尖锐边角即为成功；大面积开裂报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(81,_utf8mb4'门把手脱落',_utf8mb4'门把手,脱落,锁体,方杆',_utf8mb4'宿舍门把手掉下来了怎么办？',_utf8mb4'1.捡回门把手和固定螺丝，检查门板孔位是否松动；2.用螺丝刀重新固定把手，确认方杆插入锁体；3.锁体损坏或孔位滑丝报修更换门锁配件。
成功判据：把手牢固、开关门顺畅且能正常上锁即为成功；锁体故障报修。',3,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(82,_utf8mb4'电饭煲不通电',_utf8mb4'电饭煲,不通电,插座,温控',_utf8mb4'电饭煲插上电没有任何反应怎么办？',_utf8mb4'1.换一个已知正常的插座测试，排除插座故障；2.检查电源线和锅底温控触点是否干净干燥；3.仍不通电多为内部保险或温控器损坏，停止使用并报修。
成功判据：通电后指示灯亮且能正常加热即为成功；无反应报修检测。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(83,_utf8mb4'电煮锅一用就跳闸',_utf8mb4'电煮锅,跳闸,大功率,漏电',_utf8mb4'一插电煮锅宿舍就跳闸怎么办？',_utf8mb4'1.先断开电源，确认没有和其他大功率电器共用插排；2.检查锅底是否干烧变形、插头是否进水；3.单独使用仍跳闸说明设备漏电，停止使用并报修。
成功判据：单独接入后能正常供电且空开不跳即为成功；反复跳闸停用报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(84,_utf8mb4'电吹风不转有焦味',_utf8mb4'电吹风,不转,焦味,进风口',_utf8mb4'电吹风打开后不转还闻到焦味怎么办？',_utf8mb4'1.立即停止使用并拔掉插头，等待冷却；2.检查进风口是否被毛发堵住，清理后不要马上通电；3.有焦味或电机卡住不得继续使用，报修更换。
成功判据：清理进风口后能正常出风且无异味即为成功；焦味或卡转必须停用报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(85,_utf8mb4'LED台灯闪烁',_utf8mb4'台灯,LED,闪烁,驱动',_utf8mb4'LED 台灯一直闪烁，看着眼睛难受怎么办？',_utf8mb4'1.确认灯泡与灯座规格匹配并拧紧；2.换一个插座或插排测试，排除接触不良；3.仍闪烁多为驱动电源故障，断电后报修更换。
成功判据：灯光稳定不闪烁且无频闪感即为成功；持续闪烁报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(86,_utf8mb4'空调外机不转',_utf8mb4'空调,外机,不转,制冷',_utf8mb4'空调内机有风但室外机不转怎么办？',_utf8mb4'1.确认遥控器设置为制冷模式且温度低于室温；2.查看室外机是否被异物遮挡、散热片是否积灰；3.内机有风但外机不启动，报修检查压缩机和线路。
成功判据：外机启动后出风口吹热风且室温下降即为成功；外机不启动报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(87,_utf8mb4'空调有异味',_utf8mb4'空调,异味,滤网,接水盘',_utf8mb4'空调一开就有霉味或焦糊味怎么办？',_utf8mb4'1.关闭电源后清洗室内机滤网和接水盘；2.连续开启送风模式半小时，保持门窗通风；3.霉味或焦糊味持续不散，报修深度清洗或检查线路。
成功判据：运行一段时间后异味明显消失且出风正常即为成功；焦糊味立即停用报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(88,_utf8mb4'洗衣机门打不开',_utf8mb4'洗衣机,门打不开,应急拉绳,排水',_utf8mb4'洗衣机洗完后门打不开怎么办？',_utf8mb4'1.先断电，等待滚筒停止并确认桶内无水；2.找到右下角应急拉绳或门禁拉杆，轻轻拉动开门；3.拉不开或桶内有水，报修处理，不要强行撬门。
成功判据：安全断电排水后门能正常打开即为成功；无法打开报修。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(89,_utf8mb4'洗衣机有异味洗不净',_utf8mb4'洗衣机,异味,筒自洁,滤网',_utf8mb4'公共洗衣机洗完衣服有味道还洗不干净怎么办？',_utf8mb4'1.清理洗涤剂盒和滤网，取下衣物避免长期潮湿堆放；2.选择筒自洁模式运行一次，不加衣物和洗涤剂；3.仍有污垢或异味报修拆洗内筒。
成功判据：自洁后桶内无明显异味且衣物洗净即为成功；持续异味报修清洗。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(90,_utf8mb4'开水器显示故障码',_utf8mb4'开水器,故障码,刷卡,宿管',_utf8mb4'公共开水器显示故障代码不出水怎么办？',_utf8mb4'1.记录屏幕上的故障代码和位置，停止接水；2.检查刷卡或扫码是否重复扣费，保留支付记录；3.不要自行拆机，联系宿管并按代码报修。
成功判据：记录故障码并完成报修即为成功；设备异常不要继续使用。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(91,_utf8mb4'公共吹风机不工作',_utf8mb4'吹风机,扫码,不工作,出风口',_utf8mb4'公共吹风机扫码后不吹风怎么办？',_utf8mb4'1.确认扫码或投币成功、设备指示灯是否亮起；2.检查出风口是否被毛发堵塞，清理后重试；3.多次扣费仍不工作，停止使用并联系宿管报修。
成功判据：扫码后能正常出风且扣费一次即为成功；异常扣费报修登记。',4,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(92,_utf8mb4'报修后多久上门',_utf8mb4'报修,上门时间,接单,工单',_utf8mb4'提交报修后大概多久师傅会上门？',_utf8mb4'1.提交后可在工单页查看当前状态，一般先由调度匹配师傅；2.紧急故障会优先处理，页面会显示预计接单时间；3.超过页面承诺时间仍未接单，联系宿管催单。
成功判据：工单状态更新为已接单或处理中即可视为调度成功；超时未接单联系宿管。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(93,_utf8mb4'取消报修工单',_utf8mb4'取消工单,待接单,报修,宿管',_utf8mb4'报修提交错了，怎么取消工单？',_utf8mb4'1.在工单详情查看是否仍处于待接单状态；2.待接单可在页面点击取消并填写原因；3.师傅已接单或上门后需要联系宿管协调，不要直接重复提交新工单。
成功判据：工单状态变为已取消且收到提示即为成功；已接单取消需联系宿管。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(94,_utf8mb4'改约上门时间',_utf8mb4'改约,上门时间,工单,时间窗',_utf8mb4'师傅还没来，我想改个上门时间怎么办？',_utf8mb4'1.打开工单详情，查看是否提供改约入口；2.选择新的可用时间窗并提交，等待师傅确认；3.师傅已出发时直接电话沟通，不要让师傅白跑。
成功判据：工单时间更新且师傅确认即为成功；无法线上改约联系宿管协调。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(95,_utf8mb4'报修照片怎么拍',_utf8mb4'报修照片,拍照,近景,隐私',_utf8mb4'报修时要上传什么样的照片？',_utf8mb4'1.拍一张能看清故障整体位置的远景，再拍一张故障部位近景；2.漏水、渗水要拍到水流路径和周围参照物；3.光线不足可开闪光灯，但不要拍到他人隐私。
成功判据：照片能看清位置、故障细节和参照物即为合格；模糊照片建议重拍。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(96,_utf8mb4'紧急故障联系谁',_utf8mb4'紧急故障,漏水,漏电,值班电话',_utf8mb4'宿舍突然漏水漏电，应该先找谁？',_utf8mb4'1.漏水、漏电、冒烟、困人等紧急情况先离开危险区域；2.立即联系宿管或值班电话，并在系统提交工单留痕；3.不要自行处理高压电、燃气或电梯故障。
成功判据：已撤离危险区域并联系值班人员、提交工单即为正确处置；严禁自行冒险处理。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(97,_utf8mb4'师傅上门准备',_utf8mb4'师傅上门,准备,工单,宿舍',_utf8mb4'师傅来修之前我需要准备什么？',_utf8mb4'1.保留工单编号和联系电话，确保宿舍有人；2.提前清理故障周围杂物，方便师傅操作；3.涉及水电时要告知是否停水停电，宠物或贵重物品妥善安置。
成功判据：师傅到场后能直接进入并快速定位故障即为准备充分；无人等待会导致改约。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(98,_utf8mb4'报修是否收费',_utf8mb4'报修费用,收费,材料费,宿管',_utf8mb4'校园快修上门维修要收费吗？',_utf8mb4'1.日常小修一般由学校承担，具体以工单页面和宿管通知为准；2.人为损坏或额外材料费用可能另行说明，师傅上门前会告知；3.对费用有疑问可联系宿管确认，不要私下转账。
成功判据：费用规则清楚且双方确认后再维修即为成功；私下收费应拒绝并反馈。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(99,_utf8mb4'工单完成后怎么评价',_utf8mb4'工单评价,评分,已完成,反馈',_utf8mb4'维修完成后在哪里评价师傅？',_utf8mb4'1.在工单详情找到已完成工单，点击评价；2.按维修结果、态度和响应时间打分，可补充文字说明；3.评价提交后一般不能修改，有问题先联系宿管。
成功判据：评价页面提交成功并显示已评价即为完成；有遗留问题先反馈再评价。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(100,_utf8mb4'自助诊断没解决',_utf8mb4'自助诊断,没解决,去报修,已尝试',_utf8mb4'按自助诊断的步骤做完还是没修好怎么办？',_utf8mb4'1.按诊断回答逐项尝试后仍无效，点击诊断页的去报修，把已尝试步骤带过去；2.补充故障照片和宿舍位置，避免师傅重复询问；3.紧急情况不要继续自助尝试，直接联系值班人员。
成功判据：携带着已尝试步骤提交完整工单即为成功；紧急问题直接求助值班人员。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
(101,_utf8mb4'重复提交工单',_utf8mb4'重复报修,工单,合并,宿管',_utf8mb4'同一个故障我不小心提交了好几次怎么办？',_utf8mb4'1.先在工单页搜索同一故障，确认是否已有处理中的工单；2.已存在工单时不要重复提交，可在原工单下补充照片或说明；3.若旧工单已关闭但故障复发，说明复发情况后重新提交。
成功判据：同一故障只保留一个处理中工单即为成功；重复工单联系宿管合并。',5,_utf8mb4'MANUAL',NULL,0,_utf8mb4'APPROVED',NULL,0),
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `keyword` = VALUES(`keyword`),
  `question` = VALUES(`question`),
  `answer` = VALUES(`answer`),
  `category_id` = VALUES(`category_id`),
  `source` = VALUES(`source`),
  `helpful` = VALUES(`helpful`),
  `review_status` = VALUES(`review_status`),
  `embedding` = VALUES(`embedding`),
  `is_deleted` = VALUES(`is_deleted`);

-- ------------------------------------------------------------
-- 导入校验：应为 100
-- ------------------------------------------------------------
SELECT COUNT(*) AS diagnosis_seed_count
FROM `knowledge_base`
WHERE `review_status` = 'APPROVED' AND `is_deleted` = 0;
