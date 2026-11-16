# MinIO 文件服务说明（S3 兼容开源对象存储 + 分片上传 + 小文件批量读写）

> 本模块是课程作业「文件存取」的实现：大文件交给 MinIO 原生 S3 分片上传，
> 小文件一次请求批量上传 / 打包 ZIP 下载，元数据批量落库配合读写分离。
> 2026-11-03 由阿里云 OSS 整体替换为 MinIO（自托管、零云费用、Clone 即用）。

## 1. 已就绪的本地资源（Docker）

| 项 | 值 |
|---|---|
| 容器 | `campus-minio`（镜像 `minio/minio`，数据卷 `campus-minio-data`） |
| S3 API 端点 | `http://localhost:9000`（发布在 `127.0.0.1`，不暴露局域网） |
| 控制台 | `http://localhost:9001`（浏览器可进，账号见下） |
| 账号 / 密码 | `admin` / `ChangeMe_123`（与容器 `MINIO_ROOT_USER/PASSWORD` 对齐） |
| Bucket | `campus-repair`（私有；下载一律走后端签名 URL） |
| 启动命令 | `docker start campus-minio`（数据在卷里，重启不丢文件） |

## 2. 配置与密钥

后端 `application.properties`（可全部用环境变量覆盖，不需要改码）：

```properties
file.minio.endpoint=${MINIO_ENDPOINT:http://localhost:9000}
file.minio.bucket=${MINIO_BUCKET:campus-repair}
file.minio.access-key=${MINIO_ACCESS_KEY:admin}
file.minio.secret-key=${MINIO_SECRET_KEY:ChangeMe_123}
file.minio.url-expire-seconds=1800
```

默认值与本地容器完全一致——**克隆仓库即可跑通，无需申请任何云账号**；
局域网演示时给 `MINIO_ENDPOINT` 填本机 IP 即可。SDK 选型：AWS S3 SDK v2
（`software.amazon.awssdk:s3`），MinIO 本身即 S3 兼容协议的实现，客户端必须
`forcePathStyle(true)`（MinIO 不支持虚拟主机式桶寻址，签名器同样要开
`pathStyleAccessEnabled`）。

## 3. 代码结构

| 文件 | 职责 |
|---|---|
| `config/MinioProperties` | MinIO 参数注入（endpoint/bucket/密钥） |
| `config/MinioConfig` | 装配 `S3Client` + `S3Presigner`；未配置完整时上下文照常启动，接口给出明确提示（注意 `@ConditionalOnProperty` 只判属性存在不判非空，故用自定义 Condition 真判空） |
| `entity/FileObject` | 文件元数据，表 `file_object`（JPA ddl-auto=update 自动建表） |
| `repository/FileObjectRepository` | 元数据访问层 |
| `service/FileStorageService(Impl)` | 核心逻辑：分片上传三段式、批量上传、签名 URL、ZIP 打包 |
| `controller/FileController` | HTTP 入口，前缀 `/api/files`（未改动） |

## 4. 大文件：分片上传三段式（含断点续传升级）

后端收到分片后立刻转发 MinIO（内存占用恒为单分片大小，上限 10MB/片）：

```bash
# ① 初始化，拿 uploadId
curl -X POST "http://localhost:8080/api/files/multipart/init?fileName=video.mp4&orderId=1"
# ② 逐片上传（chunkIndex 从 1 起，同一编号可重传覆盖；S3 要求非末片 >= 5MiB，前端切片正好 5MB）
curl -X POST "http://localhost:8080/api/files/multipart/{uploadId}/1" -F "chunk=@./video.part1"
# ③ 合并
curl -X POST "http://localhost:8080/api/files/multipart/{uploadId}/complete?orderId=1"
```

**升级点（相对组员 OSS 版）**：分片会话不再只存在进程内存——
`init` 时即向 `file_object` 表 INSERT 一行（`file_type='multipart'`、
`file_size IS NULL` 作未完成标记），`complete` 时 UPDATE 同一行补齐大小/工单。
收益：

1. **后端重启 / 双 Tomcat 集群不丢会话**：未命中内存缓存时按 uploadId 反查该行恢复（已实测：重启后直接传第 3 片成功）。
2. **上传中的半成品不会混进工单附件列表**（列表查询只看 order_id，且半成品行 order_id 为空）。
3. 同一 uploadId 同一分片编号重复上传自动覆盖，天然断点续传。

## 5. 小文件：批量上传 / ZIP 下载

```bash
# 批量上传：一次请求提交 N 个文件，元数据一条多行 VALUES 批量落库（写主库）
curl -X POST "http://localhost:8080/api/files/batch-upload?orderId=1" \
     -F "files=@1.jpg" -F "files=@2.jpg" -F "files=@3.jpg"

# 附件列表（走 @Slave 从库）
curl http://localhost:8080/api/files/order/1

# 单文件下载：先拿签名 URL（30 分钟有效），浏览器/前端直接 GET 它
curl http://localhost:8080/api/files/{fileId}/url

# 批量下载打包：多个文件流式打包 ZIP，不在内存攒整包
curl -o bundle.zip "http://localhost:8080/api/files/zip?ids=1,2,3"
```

读写分离呼应：`batch-upload` 插入走主库；`GET /order/{id}` 的 Service 方法
标注 `@Slave`，由既有主从数据源路由切到只读从库。

## 6. 启动与验证（全链路实测流水）

1. 启动 Docker Desktop → `docker start campus-minio mysql-master mysql-slave`
   （或 `cd deploy\docker && docker compose up -d` 再起 MinIO）。
2. 启动后端：`powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1`
   （脚本自动把 `.env` 注入环境变量；MinIO 账号密码在配置里有默认值，`.env` 不需要条目）。
3. 实测已全部通过的探针：
   - `GET /api/system/node` → 200
   - 5 个 1KB 文件批量上传 → 200，返回 5 条元数据
   - init → 5MB + 100B 两分片 → complete → `file_object` 行 file_size=5242980 精确
   - **后端重启后对旧 uploadId 继续传片 → 200（会话落库恢复）**
   - 签名 URL → `http://localhost:9000/campus-repair/...`，GET 200 且字节数一致
   - ZIP 打包（`?ids=32,33`）→ 200，ZIP 内两条目正确
   - `GET /api/files/order/7`（@Slave）→ 200
4. 想肉眼看文件：开 `http://localhost:9001`（控制台）→ Object Browser → `campus-repair`。

## 7. 已知约束（演示级）

- 单分片上限 10MB（`MAX_CHUNK_SIZE`），S3 协议分片编号 1~10000。
- S3 要求**除最后一片外每片 ≥ 5MiB**；前端 `Files.vue` 的 `CHUNK_SIZE = 5MB` 正好贴线合规，改动切片大小时注意勿小于该值。
- `file_object.md5` 字段预留完整性校验；小文件可由前端计算后随表单提交。
- Bucket 保持**私有**，切勿在控制台改成公开读写；`9000/9001` 端口映射绑定 `127.0.0.1`，勿改 `0.0.0.0`（Docker Desktop 会把端口放行给整个局域网）。
- MinIO 健康探针：`curl http://localhost:9000/minio/health/live`（期望 200）。

## 8. 社区版停服说明与组员运行指南（重要）

### 8.1 背景时间线

本项目使用的 MinIO 社区版已由官方停止维护，选型时按"本地演示自托管"策略锁定冻结版本使用：

| 时间 | 事件 |
|---|---|
| 2025-06 | 社区版管理 Console 功能被裁剪 |
| 2025-10 | 停止发布社区版预编译二进制与 Docker 镜像 |
| 2025-12-03 | GitHub 进入“维护模式” |
| 2026-02-12 | 标记“不再维护” |
| 2026-04-25 | 仓库归档（read-only） |
| 2026-09-11 | Docker Hub 的 `minio/minio`、`minio/mc` 整库移除 |

本仓库冻结版本：**`RELEASE.2025-09-07T16-13-09Z`**（已 `docker save` 为
`deploy/docker/images/minio-campus.tar`，约 59MB；该目录已加入 `.gitignore`，
通过组内云盘/群文件分发）。服务端对应能力全部实测通过（见第 6 节流水），
课程演示场景不再依赖任何在线拉取。

### 8.2 组员拿到镜像的两条路径（任选其一）

```bash
# 路径 A：从组内共享的 tar 恢复（推荐，与本项目实测版本完全一致）
docker load -i minio-campus.tar

# 路径 B：从 quay.io 拉取冻结 tag（官方已停更，仅存归档版）
docker pull quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z
```

### 8.3 组员新机从零跑通（5 步）

```bash
# ① 装 Docker Desktop + JDK 25（Temurin，设 JAVA_HOME），git clone 本仓库

# ② 从 tar 恢复 MinIO 镜像（见 8.2），然后一条命令起容器：
docker run -d --name campus-minio --restart unless-stopped \
  -p 127.0.0.1:9000:9000 -p 127.0.0.1:9001:9001 \
  -v campus-minio-data:/data \
  -e MINIO_ROOT_USER=admin -e MINIO_ROOT_PASSWORD=ChangeMe_123 \
  minio/minio:RELEASE.2025-09-07T16-13-09Z \
  server /data --console-address ":9001"

# ③ MySQL 主从：cd deploy\docker && docker compose up -d（等 healthy）

# ④ 仓库根目录自建 .env（不入仓库）：
#    DB_USERNAME=root
#    DB_PASSWORD=ChangeMe_123
#    （MinIO 账号密码在配置里有默认值，无需条目）

# ⑤ 启动后端：powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
```

**桶无需手工创建**：后端在首个文件请求时自动 `HeadBucket` 检查，
桶 `campus-repair` 缺失会自动 `createBucket`（新环境首次运行日志可见
"MinIO 桶不存在，已自动创建"，属预期行为）。

### 8.4 安全禁改项（冻结版没有补丁通道，靠配置收面）

1. **端口映射必须保持 `127.0.0.1:` 前缀**——去掉会经 Docker Desktop 放行到整个局域网（MinIO 已无安全更新，等于裸奔）。
2. 桶保持**私有**，下载一律走后端签名 URL。
3. `campus-minio-data` 卷就是全部文件数据：删除卷 = 删除所有文件，重装 Docker 前先 `docker run --rm -v campus-minio-data:/data -v ${PWD}:/bak alpine tar czf /bak/minio-data.tgz /data` 备份。

### 8.5 若未来需要无缝换引擎（备选，课程期间不必做）

后端走标准 S3 协议（AWS SDK v2，`forcePathStyle`），换任何 S3 兼容实现
只需改 `MINIO_ENDPOINT`/`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` 三个环境变量、
后端零代码改动。MinIO 社区分支 `pgsty/silo`（env/API/数据格式全兼容）是
社区延续方向之一，数据卷可原样接管。
