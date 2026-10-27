# OSS 文件服务说明（阿里云 OSS + 分片上传 + 小文件批量读写）

> 本模块是课程作业「文件存取」的实现：大文件交给阿里云 OSS 原生分片上传，
> 小文件一次请求批量上传 / 打包 ZIP 下载，元数据批量落库配合读写分离。

## 1. 已就绪的云端资源

| 项 | 值 |
|---|---|
| 云端控制台 | https://oss.console.aliyun.com |
| Endpoint | `https://oss-cn-beijing.aliyuncs.com`（华北2-北京） |
| Bucket | `campus-repair-2024110411`，权限 **私有**（必须私有，下载走签名 URL） |
| 密钥位置 | 仓库根目录 `.env`（已被 `.gitignore` 忽略，永不提交） |

## 2. 代码结构

| 文件 | 职责 |
|---|---|
| `config/OssProperties` | OSS 参数注入（endpoint/bucket/密钥，密钥只读过环境变量） |
| `config/OssConfig` | 装配全局 OSS 客户端；未配密钥时上下文照常启动，接口给出明确提示 |
| `entity/FileObject` | 文件元数据，表 `file_object`（JPA ddl-auto=update 自动建表） |
| `repository/FileObjectRepository` | 元数据访问层 |
| `service/FileStorageService(Impl)` | 核心逻辑：分片上传三段式、批量上传、签名 URL、ZIP 打包 |
| `controller/FileController` | HTTP 入口，前缀 `/api/files` |

## 3. 大文件：分片上传三段式

后端收到分片后立刻转发 OSS（内存占用恒为单分片大小，上限 10MB/片）：

```bash
# ① 初始化，拿 uploadId
curl -X POST "http://localhost:8080/api/files/multipart/init?fileName=video.mp4&orderId=1"
# ② 逐片上传（chunkIndex 从 1 起，同一编号可重传覆盖，天然断点续传）
curl -X POST "http://localhost:8080/api/files/multipart/{uploadId}/1" -F "chunk=@./video.part1"
# ③ 合并
curl -X POST "http://localhost:8080/api/files/multipart/{uploadId}/complete?orderId=1"
```

## 4. 小文件：批量上传 / ZIP 下载

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

## 5. 启动与验证

1. 启动 Docker Desktop 后：`cd deploy\docker && docker compose up -d`（MySQL 主从）
2. 启动后端：`powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1`
   （脚本自动把 `.env` 注入环境变量）
3. 验证 OSS：上传后去 OSS 控制台对应目录即可看到文件。

## 6. 已知约束（演示级）

- 分片会话 `uploadId -> objectKey` 保存在本进程内存，重启即失；若要在
  双 Tomcat 集群下使用，需外置会话（如将上传初始化记录落表再取）。
- `file_object.md5` 字段预留完整性校验；小文件可由前端计算后随表单提交。
- Bucket 为私有，切勿在控制台手工改成公开读写，防盗刷。
