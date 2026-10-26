@echo off
REM ============================================================
REM  Campus Repair Assistant - Tomcat 实例环境变量模板
REM  目标位置：<实例目录>\bin\setenv.bat（由 setup-cluster.ps1 生成）
REM  生成时会替换 __JAVA_HOME__ 占位符为本机真实 JDK 路径。
REM  注意：数据库密码请改成真实值后再启动，不要提交真实密码。
REM ============================================================

REM ===== JVM 参数（本机双实例，各占约 1.5GB 峰值） =====
set "JAVA_HOME=__JAVA_HOME__"
set "CATALINA_OPTS=-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

REM ===== Spring Boot 生产配置（application-prod.properties 依赖这些环境变量） =====
set "SPRING_PROFILES_ACTIVE=prod"

REM 数据库读写分离（Docker 主从）：主库 3306 写 + 默认访问，从库 3307 供 @Slave 读
set "DB_HOST=localhost"
set "DB_PORT=3306"
set "DB_SLAVE_HOST=localhost"
set "DB_SLAVE_PORT=3307"
set "DB_USERNAME=campus"
set "DB_PASSWORD=ChangeMe_123"

REM 上传目录：两实例共享同一绝对路径（同机集群天然共享磁盘）
set "UPLOAD_DIR=%CAMPUS_ROOT%\uploads"

REM 经 Nginx 同源访问时 CORS 不触发；此处仅覆盖"直连节点调试"的场景
set "CORS_ALLOWED_ORIGINS=http://localhost:5173"
