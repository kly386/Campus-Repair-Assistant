-- 大屏演示数据（开发库专用，仅本地演示使用）
USE campus_repair;
SET NAMES utf8mb4;

-- 演示用户：2 师傅 + 3 学生
INSERT IGNORE INTO `user` (`username`,`password`,`real_name`,`role`,`phone`,`building`,`room`) VALUES
('demo_worker1','demo123456','张师傅','WORKER','13900000001',NULL,NULL),
('demo_worker2','demo123456','李师傅','WORKER','13900000002',NULL,NULL),
('demo_stu1','demo123456','王小明','STUDENT','13900000011','紫荆1号楼','201'),
('demo_stu2','demo123456','赵小华','STUDENT','13900000012','紫荆2号楼','305'),
('demo_stu3','demo123456','陈小强','STUDENT','13900000013','桃李园A栋','502');

INSERT INTO `repair_order`
(`order_no`,`idempotency_key`,`title`,`description`,`category_id`,`emergency_level`,`student_id`,`building`,`room`,`appointment_time`,`worker_id`,`status`,`result`,`assign_score`,`assign_reason`,`version`,`is_deleted`,`gmt_create`,`finish_time`) VALUES
('RO20261101001','demo-idem-001','水龙头漏水','洗手台水龙头关不严，持续滴水',1,'MEDIUM',3,'紫荆1号楼','201',DATE_SUB(NOW(),INTERVAL 13 DAY)+INTERVAL 10 HOUR,1,'COMPLETED','已更换阀芯，测试正常',88.50,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 13 DAY),DATE_SUB(NOW(),INTERVAL 13 DAY)+INTERVAL 8 HOUR),
('RO20261102001','demo-idem-002','台灯不亮','台灯通电后不亮，怀疑灯管老化',1,'LOW',4,'紫荆2号楼','305',DATE_SUB(NOW(),INTERVAL 12 DAY)+INTERVAL 14 HOUR,1,'COMPLETED','更换灯管后恢复',76.00,'负载均衡',1,0,DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 12 DAY)+INTERVAL 3 HOUR),
('RO20261103001','demo-idem-003','网络时断时续','宿舍 WiFi 频繁掉线',2,'HIGH',5,'桃李园A栋','502',DATE_SUB(NOW(),INTERVAL 11 DAY)+INTERVAL 9 HOUR,2,'COMPLETED','端口重置后稳定',92.25,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 11 DAY),DATE_SUB(NOW(),INTERVAL 11 DAY)+INTERVAL 2 HOUR),
('RO20261104001','demo-idem-004','衣柜门脱落','柜门铰链断裂',3,'LOW',3,'紫荆1号楼','201',DATE_SUB(NOW(),INTERVAL 10 DAY)+INTERVAL 15 HOUR,2,'COMPLETED','已更换铰链',81.75,'负载均衡',1,0,DATE_SUB(NOW(),INTERVAL 10 DAY),DATE_SUB(NOW(),INTERVAL 10 DAY)+INTERVAL 5 HOUR),
('RO20261105001','demo-idem-005','马桶堵塞','冲水不畅，疑似异物堵塞',1,'HIGH',4,'紫荆2号楼','305',DATE_SUB(NOW(),INTERVAL 9 DAY)+INTERVAL 8 HOUR,1,'COMPLETED','疏通完毕',90.00,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 9 DAY),DATE_SUB(NOW(),INTERVAL 9 DAY)+INTERVAL 4 HOUR),
('RO20261106001','demo-idem-006','插座无电','墙面插座无电，跳闸后未恢复',1,'MEDIUM',5,'桃李园A栋','502',DATE_SUB(NOW(),INTERVAL 8 DAY)+INTERVAL 11 HOUR,1,'COMPLETED','复位空开并检查线路',85.00,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY)+INTERVAL 6 HOUR),
('RO20261107001','demo-idem-007','窗户合页松动','窗户开关困难',3,'LOW',3,'紫荆1号楼','201',DATE_SUB(NOW(),INTERVAL 7 DAY)+INTERVAL 16 HOUR,2,'COMPLETED','紧固合页螺丝',72.00,'负载均衡',1,0,DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY)+INTERVAL 2 HOUR),
('RO20261108001','demo-idem-008','门禁卡失效','宿舍门禁刷卡无反应',2,'MEDIUM',4,'紫荆2号楼','305',DATE_SUB(NOW(),INTERVAL 6 DAY)+INTERVAL 10 HOUR,2,'COMPLETED','重新授权成功',78.50,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY)+INTERVAL 1 HOUR),
('RO20261109001','demo-idem-009','热水器不加热','洗澡水温度上不来',1,'HIGH',5,'桃李园A栋','502',DATE_SUB(NOW(),INTERVAL 5 DAY)+INTERVAL 19 HOUR,1,'COMPLETED','更换加热棒',95.00,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 5 DAY),DATE_SUB(NOW(),INTERVAL 5 DAY)+INTERVAL 9 HOUR),
('RO20261110001','demo-idem-010','书桌抽屉卡死','抽屉变形推不进去',3,'LOW',3,'紫荆1号楼','201',DATE_SUB(NOW(),INTERVAL 4 DAY)+INTERVAL 13 HOUR,2,'COMPLETED','校正滑轨',69.75,'负载均衡',1,0,DATE_SUB(NOW(),INTERVAL 4 DAY),DATE_SUB(NOW(),INTERVAL 4 DAY)+INTERVAL 3 HOUR),
('RO20261111001','demo-idem-011','走廊灯管闪烁','走廊顶灯频繁闪烁',1,'MEDIUM',4,'紫荆2号楼','305',DATE_SUB(NOW(),INTERVAL 3 DAY)+INTERVAL 9 HOUR,1,'ON_THE_WAY',NULL,83.00,'专长匹配',1,0,DATE_SUB(NOW(),INTERVAL 3 DAY),NULL),
('RO20261112001','demo-idem-012','路由器频繁重启','设备每 10 分钟重启一次',2,'HIGH',5,'桃李园A栋','502',DATE_SUB(NOW(),INTERVAL 2 DAY)+INTERVAL 14 HOUR,2,'PROCESSING',NULL,91.00,'专长匹配',2,0,DATE_SUB(NOW(),INTERVAL 2 DAY),NULL),
('RO20261113001','demo-idem-013','床架螺丝松动','上下铺床架晃动',3,'MEDIUM',3,'紫荆1号楼','201',DATE_SUB(NOW(),INTERVAL 1 DAY)+INTERVAL 15 HOUR,2,'ASSIGNED',NULL,80.25,'负载均衡',1,0,DATE_SUB(NOW(),INTERVAL 1 DAY),NULL),
('RO20261114001','demo-idem-014','空调不制冷','制冷效果明显变差',1,'HIGH',4,'紫荆2号楼','305',NOW()+INTERVAL 1 DAY,1,'SUBMITTED',NULL,NULL,NULL,0,0,DATE_SUB(NOW(),INTERVAL 12 HOUR),NULL),
('RO20261115001','demo-idem-015','洗手池下水慢','排水缓慢易积水',1,'LOW',5,'桃李园A栋','502',NOW()+INTERVAL 2 DAY,1,'SUBMITTED',NULL,NULL,NULL,0,0,DATE_SUB(NOW(),INTERVAL 8 HOUR),NULL),
('RO20261116001','demo-idem-016','衣柜锁扣损坏','无法上锁',3,'LOW',3,'紫荆1号楼','201',NOW()+INTERVAL 2 DAY,2,'PENDING_ACCEPT','已维修待验收',74.50,'负载均衡',1,0,DATE_SUB(NOW(),INTERVAL 6 HOUR),NULL),
('RO20261117001','demo-idem-017','宿舍门异响','开关门有金属摩擦声',3,'MEDIUM',4,'紫荆2号楼','305',NOW()+INTERVAL 1 DAY,1,'CANCELLED',NULL,NULL,NULL,0,0,DATE_SUB(NOW(),INTERVAL 10 HOUR),NULL),
('RO20261118001','demo-idem-018','网速极慢','下载速度只有 100KB/s',2,'MEDIUM',5,'桃李园A栋','502',NOW()+INTERVAL 3 DAY,2,'SUBMITTED',NULL,NULL,NULL,0,0,NOW()-INTERVAL 3 HOUR,NULL);

-- 演示评价：已完成的 10 单全评，评分 3~5
INSERT IGNORE INTO `order_evaluation` (`order_id`,`student_id`,`worker_id`,`score`,`comment`)
SELECT `id`,`student_id`,`worker_id`, 3 + (`id` MOD 3), '演示评价数据' FROM `repair_order` WHERE `status`='COMPLETED' AND `is_deleted`=0;
