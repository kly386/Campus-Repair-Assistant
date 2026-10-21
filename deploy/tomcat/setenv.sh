#!/bin/bash
# Tomcat 11 环境变量模板
# 目标位置：/opt/tomcat/bin/setenv.sh
# 权限：640，属主 tomcat:tomcat（文件中含数据库密码）
# 注意：从 Windows 复制到 Linux 后必须转为 LF：
#   sed -i 's/\r$//' /opt/tomcat/bin/setenv.sh

# 以 readlink -f "$(which java)" 的实际路径为准
export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64

export CATALINA_PID=/opt/tomcat/temp/tomcat.pid
export CATALINA_OUT=/var/log/tomcat11/catalina.out

# 使用 CATALINA_OPTS 而不是 JAVA_OPTS：只用于 Tomcat 启动，不传给 shutdown.sh
export CATALINA_OPTS="$CATALINA_OPTS \
  -Xms512m -Xmx1024m \
  -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/tomcat/temp \
  -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai \
  -Djava.security.egd=file:/dev/./urandom"

# Spring Boot 生产配置（application-prod.properties）
export SPRING_PROFILES_ACTIVE=prod
# 主库（写 + 默认访问）
export DB_HOST=192.168.56.101
export DB_PORT=3306
export DB_USERNAME=campus
export DB_PASSWORD='ChangeMe_123'
# 从库（读写分离）：本机 Docker 从库；未就绪时可置为空串，读操作自动回落主库
export DB_SLAVE_HOST=localhost
export DB_SLAVE_PORT=3307
export UPLOAD_DIR=/opt/campus-repair/uploads
export CORS_ALLOWED_ORIGINS=http://192.168.56.101
