# deploy 配置模板

本目录存放「Nginx + 双 Tomcat 11 + MySQL」三虚拟机集群部署所需的配置模板。
完整步骤、参数说明和验证方法见 [docs/Tomcat集群部署文档.md](../docs/Tomcat集群部署文档.md)。

## 节点规划

| 节点 | IP | 组件 | 说明 |
|------|----|------|------|
| VM1 | 192.168.56.101 | Nginx + MySQL 8 | 统一入口与数据库 |
| VM2 | 192.168.56.102 | Tomcat 11 | `jvmRoute=tomcat1` |
| VM3 | 192.168.56.103 | Tomcat 11 | `jvmRoute=tomcat2` |

## 文件说明

| 文件 | 目标位置 | 用途 |
|------|----------|------|
| `tomcat/server.xml` | `/opt/tomcat/conf/server.xml` | Tomcat 11 主配置（Connector、JVM 路由、访问日志） |
| `tomcat/setenv.sh` | `/opt/tomcat/bin/setenv.sh` | JVM 参数、Spring profile、数据库环境变量 |
| `tomcat/tomcat.service` | `/etc/systemd/system/tomcat.service` | systemd 服务单元 |
| `tomcat/logrotate.d/tomcat` | `/etc/logrotate.d/tomcat` | catalina.out 日志轮转 |
| `nginx/campus-repair.conf` | `/etc/nginx/conf.d/campus-repair.conf` | 静态站点 + `/api` 双节点轮询 |
| `mysql/mysqld-campus.cnf` | `/etc/mysql/mysql.conf.d/campus.cnf` | MySQL 监听地址与基础参数 |
| `mysql/init-user.sql` | 在 VM1 上用 root 执行 | 建库、创建最小权限应用账号 |

## 使用前必须替换的占位符

| 占位符 | 出现位置 | 说明 |
|--------|----------|------|
| `ChangeMe_123` | `setenv.sh`、`application-prod.properties`、`init-user.sql` | 数据库应用账号密码，三处保持一致 |
| `192.168.56.101/102/103` | 所有配置 | 按实际 Host-Only 网段修改 |
| `jvmRoute="tomcat1"` | `server.xml` | VM3 改为 `tomcat2` |
| `JAVA_HOME` | `setenv.sh` | 以 `readlink -f "$(which java)"` 的实际路径为准 |

## 使用顺序

1. 三台 VM 完成系统、网络、JDK 25 准备。
2. VM1：MySQL → 导入 `docs/sql/init.sql` → Nginx。
3. VM2/VM3：Tomcat 11 → 部署 `ROOT.war`。
4. 按部署文档的验证矩阵逐项检查。

> 模板中的 IP、密码和路径是演示值，复制到服务器后请先 `diff` 再合并，不要直接覆盖现有生产配置。
