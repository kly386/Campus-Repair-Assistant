# deploy 配置模板

本目录存放「Nginx + 本机双 Tomcat 11 实例 + Docker MySQL 主从」部署所需的全部配置模板。

- 集群部署（Tomcat 应用服务器配置、基本参数、双端口集群、接入 Nginx）：
  [部署文档](../docs/Tomcat集群部署文档.md)
- 数据库读写分离（GTID 主从复制 + 应用层数据源路由）：
  [MySQL读写分离部署文档](../docs/MySQL读写分离部署文档.md)

## 部署形态

```text
局域网用户（组员 / 教师机）
        │  http://<项目服务器 IP>/
        ▼
Nginx (Windows 本机) :80
   ├─ /        → 前端静态文件 frontend\dist
   └─ /api/**  → 轮询 ↓（被动健康检查 + 幂等请求自动切换）
        ├─ Tomcat 实例1  127.0.0.1:8080  jvmRoute=tomcat1
        └─ Tomcat 实例2  127.0.0.1:8081  jvmRoute=tomcat2
             └─ localhost:3306 主库（写） / localhost:3307 从库（@Slave 读）
                 即 Docker Desktop 容器 mysql-master / mysql-slave
```

零虚拟机：应用层与数据库层全部运行在 Windows 本机，双 Tomcat 通过
`CATALINA_HOME / CATALINA_BASE` 分离实现“一份安装、两个实例、两个端口”。

## 目录说明

| 文件 / 目录 | 目标位置 | 用途 |
|------|----------|------|
| `cluster/conf/server-node1.xml` | `<实例1>\conf\server.xml` | HTTP 8080 / Shutdown 8005 / `jvmRoute=tomcat1` |
| `cluster/conf/server-node2.xml` | `<实例2>\conf\server.xml` | HTTP 8081 / Shutdown 8006 / `jvmRoute=tomcat2` |
| `cluster/conf/setenv.template.bat` | `<实例N>\bin\setenv.bat` | JVM 参数 + Spring 生产环境变量（setup 脚本按此生成，替换 `__JAVA_HOME__`） |
| `cluster/nginx/nginx.conf` | `<Nginx 安装目录>\conf\nginx.conf` | 静态站点 + `/api` 双实例轮询 |
| `cluster/setup-cluster.ps1` | 在本目录执行 | 一键生成两实例目录、写入 setenv、复制构建产物、生成本机路径配置 |
| `cluster/start-tomcat1.bat` / `stop-tomcat1.bat` | 双击或命令行执行 | 实例 1 独立启停 |
| `cluster/start-tomcat2.bat` / `stop-tomcat2.bat` | 双击或命令行执行 | 实例 2 独立启停 |
| `cluster/cluster.local.example.bat` | 由 setup 生成 `cluster.local.bat` | 本机根目录与 Tomcat 安装路径（**含个人路径，不入库**） |
| `docker/docker-compose.yml` | 本目录执行 `docker compose up -d` | MySQL 主从双容器（主 3306 / 从 3307） |
| `docker/master/01-users.sql` | 主库容器初始化脚本 | 建应用账号 `campus` 与复制账号 `repl` |
| `docker/slave/01-users.sql` | 从库容器初始化脚本 | 单独建应用账号 `campus`（用户表不随业务库复制） |

> 旧的三虚拟机方案模板（`tomcat/`、`nginx/`、`mysql/`）已移除；
> 如需历史版本可在 git 历史中找回（如 `git show 45531b1:deploy/tomcat/server.xml`）。

## 使用前必须替换的占位符

| 占位符 | 出现位置 | 说明 |
|--------|----------|------|
| `ChangeMe_123` | `setenv.template.bat`（生成后）、`docker/*/01-users.sql` | 数据库应用账号密码，两处保持一致并用真实值替换 |
| `__JAVA_HOME__` | `setenv.template.bat`（仅模板） | setup 脚本生成 setenv.bat 时自动替换 |
| `D:/campus/frontend-dist` | `cluster/nginx/nginx.conf` 的 `root` | 改为本机前端构建产物目录 |
| `DB_HOST / DB_PORT / DB_SLAVE_*` | `setenv.template.bat` | 默认 localhost:3306 / 3307，与 Docker 容器端口映射一致 |

## 使用顺序

1. 一次性准备：JDK 25（设置 `JAVA_HOME`）、Tomcat 11 压缩包解压到本机。
2. 数据库：`cd deploy/docker && docker compose up -d`，按读写分离文档建立复制。
3. 集群安装：`cd deploy/cluster && powershell -ExecutionPolicy Bypass -File .\setup-cluster.ps1`。
4. 构建发布：backend `mvnw.cmd clean package -DskipTests` → 复制 `ROOT.war` 到两实例 `webapps\`；frontend `npm run build` → `dist` 内容放到 Nginx root 目录。
5. 启动：`start-tomcat1.bat`、`start-tomcat2.bat`、Nginx；按部署文档的上线检查清单逐项确认。

> 所有模板的密码占位、路径均为初始值，组员首次部署只需改 setenv 生成的实例文件与 nginx root，
> 不要把真实密码或本机路径提交回仓库。
