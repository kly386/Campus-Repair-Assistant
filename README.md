# AI-Training-Evaluation

大学生软件实训教学 AI 检查评价系统

## 项目简介

支持本地大模型部署，具备 PC Web 可视化界面，实现实训成果上传解析、智能核查、多维评价、报表导出一体化的 AI 评价系统。

## 核心功能

- **实训成果上传与解析**：支持 Word/PDF/图片等多格式上传，大模型自动解析文件内容
- **智能核查**：实训要求与效果校验、逻辑漏洞识别、步骤完整性核查
- **多维度评价**：自定义评价指标及权重，AI 客观评分 + 教师主观评分
- **报表生成**：实训评价报告、科目统计报表，Excel/PDF 导出，含可视化图表

## 技术栈

- **前端**：Vue 3 + Vite + Element Plus + ECharts
- **后端**：Spring Boot + MyBatis + MySQL
- **AI**：大语言模型 API 接入

## 项目结构

```
AI-Training-Evaluation/
├── frontend/     # Vue3 前端
├── backend/      # Spring Boot 后端
├── docs/         # 课程设计文档
└── README.md
```

## 快速开始

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

## 开发规范

- 每人负责一个功能模块（前后端都由本人完成）
- 在 `feature/功能名` 分支上开发
- 完成后发起 Pull Request 合并到 main

## 团队分工

| 成员 | 负责功能 |
|------|----------|
| 待分配 | 功能模块1 |
| 待分配 | 功能模块2 |
| 待分配 | 功能模块3 |
| 待分配 | 功能模块4 |
| 待分配 | 功能模块5 |
