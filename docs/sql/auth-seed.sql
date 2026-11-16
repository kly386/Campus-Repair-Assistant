USE campus_repair;
UPDATE `user` SET `real_name`='系统管理员' WHERE `username`='admin';
INSERT IGNORE INTO `user` (`username`,`password`,`real_name`,`role`,`phone`) VALUES
('admin','admin123','系统管理员','ADMIN','13800000001'),
('student','student123','测试学生','STUDENT','13800000002'),
('worker','worker123','测试维修工','WORKER','13800000003');
