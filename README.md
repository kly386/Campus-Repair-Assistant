# Campus-Repair-Assistant

校园快修服务助手

## 项目简介

校园快修的统一入口 + 智能调度中枢——学生一句话 + 一张照片完成报修，系统自动理解故障、分流自助解决或派单、全程跟踪，并把每次维修沉淀为知识资产。

## 核心功能

- **智能报修**：文字 + 拍照描述故障，多模态理解后自动补全楼栋、预约时间、紧急度等字段，生成结构化工单
- **自助诊断**：基于维修知识库（维修手册 + 历史工单）的 RAG 问答，如"路由器亮红灯怎么办"，能自修就分流
- **调度 Agent**：按故障类别、位置、师傅排班与时间窗自动匹配派单，冲突检测，支持改约
- **进度跟踪**：状态机全流程通知（提交/接单/上门/待验收/完成），支持取消、改约
- **评价与知识沉淀**：完成后的评分与维修结论，清洗后回流知识库，持续提升诊断准确率
- **运营看板**：维修量、平均响应/完成时长、满意度、高频故障 Top 榜

## 技术栈

- **前端**：Vue 3 + Vite + Element Plus + ECharts
- **后端**：Spring Boot + JPA + MySQL
- **AI**：大语言模型 API 接入（多模态理解 + RAG 问答）

## 开发环境与软件版本

| 软件 | 版本 |
|------|------|
| Java | 17+（pom 指定 17，Lombok 1.18.42 兼容更高 JDK） |
| Maven | 3.9.x（或用后端自带 mvnw / IDEA Maven） |
| Spring Boot | 3.3.4 |
| Node.js | 18+ |
| npm | 9+ |
| Vue | 3.5.10 |
| Vite | 5.4.8 |
| Element Plus | 2.8.4 |
| ECharts | 5.5.1 |
| MySQL | 8.x |
| Tomcat | 11.0.x（集群部署） |
| Nginx | 1.24.x（集群部署） |

## 项目结构

```
Campus-Repair-Assistant/
├── frontend/          # Vue3 前端（学生端 + 师傅端 + 管理看板）
├── backend/           # Spring Boot 后端（分层：controller/service/repository/entity/dto/config）
├── deploy/            # Tomcat / Nginx / MySQL 集群配置模板
├── docs/              # 课程设计文档
│   ├── sql/           # 数据库脚本（setup-all.sql 一键初始化；其余为拆分维护文件）
│   ├── api/           # API 接口文档
│   ├── 索引.md        # 文档目录与提交清单
│   ├── 需求分析.md    # 系统功能分析说明书
│   ├── 数据项设计.md  # 核心数据项定义（15 张业务表）
│   ├── 技术难点分析.md# 技术难点与方案
│   ├── 接口设计.md    # 系统接口设计文档
│   ├── 详细设计.md    # 系统详细设计
│   ├── 部署文档.md    # 系统安装部署文档（含Docker）
│   ├── Tomcat集群部署文档.md # Nginx + 双 Tomcat 11 + MySQL 集群部署
│   ├── 操作手册.md    # 系统操作说明书
│   └── 开发规范.md    # 小组开发规范
└── README.md
```

## 角色说明

| 角色 | 功能 |
|------|------|
| 学生 | 报修 / 跟踪进度 / 评价 |
| 维修师傅 | 接单 / 处理 / 提交结果 |
| 管理员 | 排班 / 运营看板 / 规则配置 |

## 快速开始

### 数据库（新机器只执行一次）

新机器不需要逐个导入 SQL，直接执行一键脚本：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < docs/sql/setup-all.sql
```

PowerShell 也可以：

```powershell
Get-Content -Raw -Encoding UTF8 docs/sql/setup-all.sql | mysql -uroot -p --default-character-set=utf8mb4
```

脚本会自动建库、建表，导入权限/演示数据与 100 条自助诊断知识种子；不用再手动执行其他 SQL。

> 维护说明：`setup-all.sql` 由 `scripts/build-setup-all.py` 根据 `docs/sql/` 下的拆分文件生成。以后改了某个拆分 SQL，记得运行 `python scripts/build-setup-all.py` 重新生成，不要直接改 `setup-all.sql`。

### 前端
```bash
cd frontend
npm install
npm run dev
```

### 后端
```bash
cd backend
mvn spring-boot:run
```

> 数据库密码本地通过 `DB_PASSWORD` 覆盖，默认仅开发用；推荐写入被 `.gitignore` 排除的 `backend/src/main/resources/application-local.properties`。诊断侧另需 `ZHIPU_API_KEY` / `SILICONFLOW_API_KEY`，同样只放本地配置。后端默认端口 8081。Docker 部署时在根目录建 `.env` 设 `MYSQL_ROOT_PASSWORD` 即可统一覆盖 MySQL 与后端。

### Tomcat 集群部署

后端已支持 WAR 打包（`mvn clean package` 产出 `target/ROOT.war`），可部署到外部 Tomcat 11。本机双 Tomcat 集群方案（Nginx + 双端口 8080/8081 + Docker MySQL 主从，零虚拟机）的实例规划、Tomcat 参数、一键安装、滚动更新发布与运维检查见 [docs/Tomcat集群部署文档.md](docs/Tomcat集群部署文档.md)。

## 开发规范

- 每人负责一个功能模块（前后端都由本人完成）
- 在 `feature/功能名` 分支上开发
- 完成后发起 Pull Request 合并到 main

## 团队分工

> 分工原则：每人负责一个功能模块（前后端均由本人完成）；组长承担调度模块并统筹全项目。

| 成员 | 负责功能 | 备注 |
|------|----------|------|
| 开璐瑶（组长） | 调度 Agent（自动派单、冲突检测、改约） | 兼整体联调 / Docker 部署 / 文档 / 答辩；师傅端接单工作台壳 |
| 闵娟 | 自助诊断（RAG 问答、维修知识库） | RAG 检索链路主程（Chroma / Embedding / 阈值调优）+ 知识库后台 CRUD（掌管 `knowledge_base` 数据契约） |
| 伍梦圆 | 智能报修（文字+拍照多模态理解、工单生成） | 兼 AI 公共封装（模型代理/提示词模板/密钥用量，闵娟复用）与知识库审核页（PENDING→APPROVED，挂管理端） |
| 杨思彤 | 进度跟踪 + 评价体系（状态机、评价与知识回流） | 兼通知消息；知识回流只产生候选，对接审核页 |
| 钟心茹 | 运营看板 + 用户体系（三类角色、排班、统计图表） | JWT 认证模板最先交付、全组共用；兼故障类别管理、学生端壳+登录页、管理端壳 |

### 职责边界

- 前端端壳：学生端壳 + 登录页、管理端壳归钟心茹；师傅端接单工作台壳归开璐瑶；各成员在自己模块内补路由与页面。
- 管理端：导航/框架/权限归钟心茹；知识库审核页归伍梦圆（页面挂管理端，审核通过调用闵娟的知识写接口）。
- 排班数据：CRUD 归钟心茹，调度只读（不写排班）。
- 知识回流：杨思彤只生成候选；审核（PENDING → APPROVED）由伍梦圆，入库/维护由闵娟（`knowledge_base` 契约与字段变更先改 `docs/数据项设计.md`）。
- AI 模型代理：伍梦圆统一封装一份，多模态与 RAG 共用。
