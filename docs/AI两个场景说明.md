# AI 两个场景说明

> 校园快修服务助手 —— 诊断 AI 与报修 AI 的职责边界
> 版本：v1.1
> 负责人：闵娟（诊断 AI）、伍梦圆（报修 AI）

---

## 1. 一句话区分

系统里有两个 AI 场景，**业务上分工、密钥上独立、协议上都是 OpenAI 兼容**：

| | 诊断 AI（自助诊断） | 报修 AI（智能报修） |
|---|---|---|
| 入口 | `/ws/diagnosis`（主）、`/api/knowledge/session/stream`（降级） | 报修页 `AI 智能解析` 按钮 |
| 任务 | 判断"能不能自己修好"，能修就给步骤，修不好就分流 | 把"一句话 + 照片"抽成工单结构化字段 |
| 输出 | 五段式自然语言：①初步判断 ②先确认什么 ③分步操作（只列动作）④还不行怎么办 ⑤安全提醒 | 故障类别、楼栋房间号、紧急程度、预约时间等字段 |
| 依据 | 只依据 `knowledge_base` 的内存向量 Top-3（grounded） | 学生填写的描述 + 上传照片 |
| 模型 / key | 对话：智谱 `glm-4-flashx-250414`（`ZHIPU_API_KEY`）；向量：硅基流动 `BAAI/bge-m3`（`SILICONFLOW_API_KEY`） | DashScope `qwen-vl-plus`，`AI_API_KEY`（伍梦圆负责，本侧不动） |
| 核心代码 | `DiagnosisEngine`（唯一业务逻辑） | 伍梦圆的 `AiClient` / 报修解析逻辑（不改动） |
| 负责人 | 闵娟 | 伍梦圆 |

**关键结论：两个场景各用各的 key；诊断侧换模型不影响报修侧，反之亦然。**

---

## 2. 两套独立配置（不要合并）

```properties
# 诊断侧对话（智谱）
zhipu.api-key=${ZHIPU_API_KEY:}
zhipu.base-url=${ZHIPU_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
zhipu.chat.model=${ZHIPU_CHAT_MODEL:glm-4-flashx-250414}

# 诊断侧向量化（硅基流动，BAAI/bge-m3 免费；不支持 dimensions）
siliconflow.api-key=${SILICONFLOW_API_KEY:}
siliconflow.base-url=${SILICONFLOW_BASE_URL:https://api.siliconflow.cn/v1}
siliconflow.embedding.model=${SILICONFLOW_EMBEDDING_MODEL:BAAI/bge-m3}

# 报修侧（DashScope，伍梦圆）
app.ai.base-url=${AI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
app.ai.api-key=${AI_API_KEY:}
app.ai.model=${AI_MODEL:qwen-vl-plus}
```

- 诊断对话走智谱 `/chat/completions?stream=true` → `ZhipuChatStreamService`；
- 诊断向量走硅基流动 `/embeddings` → `SiliconFlowEmbeddingServiceImpl` → `KnowledgeVectorCache`；
- 报修解析继续走原来的 `app.ai.*`，本侧不改一行。

`ZHIPU_API_KEY` / `SILICONFLOW_API_KEY` / `AI_API_KEY` 只通过本地 `.env` / 环境变量注入，
不进代码、不进 SQL、不进日志、不进提交记录。
仓库根的 `.env.example` 是不含密钥的模板，复制为 `.env` 后各自填值，填好的 `.env` 不要提交。
诊断侧 key 未配置时：Embedding 降级 MySQL LIKE，对话降级知识库原文，不会启动失败。

---

## 3. 诊断 AI：RAG 分流，不是聊天机器人

### 3.1 为什么必须 grounded

诊断 AI 的目的是**分流**，不是陪聊。如果模型凭通用知识给步骤，学生照着做可能引发触电、漏水等安全事故。
所以 `DiagnosisEngine` 的五段式提示词里有硬性约束：

1. 只能用检索到的知识条目原文，禁止用通用知识补步骤；
2. 分步操作只列动作，不生成"成功判据"或"↳ 怎么算有效"行；种子判据只在降级原文中展示；
3. 条目不足以回答时，直接说"不确定，建议报修"，不猜测；
4. 闲聊、情感、学习类问题礼貌拒答；
5. 涉及水电火气的关键词由 `DiagnosisRules` **规则拦截，根本不调用大模型**（固定文案）。

### 3.2 知识库与检索：不建新表

- 唯一数据源仍是现有 `knowledge_base`（`APPROVED` 且未删除），审核页、热门、helpful 全都不动；
- 启动时 `KnowledgeVectorCache` 全量读表，调硅基流动 `BAAI/bge-m3` 把"关键词 + 标准问题"向量化，
  全部载入内存 `List`（copy-on-write，刷新时整体替换）；
- 检索时对用户问题做 Embedding，内存算余弦相似度取 Top-3，低于 `rag.min-similarity` 不进候选；
- Embedding 失败 / 缓存为空 / 向量无命中 → 自动降级 MySQL `keyword LIKE` + 口语同义词 + 关键词反查；
- 管理员改完知识条目可调 `POST /api/knowledge/cache/refresh` 手动刷新缓存，不用重启后端；
- 不做 PDF 解析，不引入额外向量库，不新建任何表。

### 3.3 防幻觉的四道闸

```
用户问题
  ↓ ① 关键词规则（DiagnosisRules）
  │   紧急词 / 非维修词 / 收尾语 / 试过无效 → 固定文案，不调模型
  ↓ ② 内存向量余弦 Top-3（min-similarity=0.6）
  │   无命中 → 知识库兜底文案，不调模型；Embedding 异常 → LIKE 降级
  ↓ ③ 低置信度（low-confidence=0.7）
  │   0.6~0.7 → 先声明"不太确定"再回答，并打低置信度标记
  ↓ ④ grounded 生成 + 引用溯源
       glm-4-flashx-250414 五段式输出，meta 事件把命中条目推给前端可展开核对
```

任一层拦下都不影响主流程；智谱调用遇限流（HTTP 429 / code 1305）会退避重试 2 次（1s、2s），
仍失败才降级为 Top-1 原文，学生看到的内容不会断。用户主动中止不重试。

### 3.4 成功判据：只留在种子里，不进实时生成

- 种子 `knowledge_base.answer` 末尾仍写"成功判据：…"，直接维护在 `docs/sql/knowledge-seed.sql`，
  `INSERT ... ON DUPLICATE KEY UPDATE` 保证重灌能更新；
- 实时流式回答的分步操作**只列动作**，不输出判据行（避免每步都跟一句"以现场现象为准"）；
- 判据只在**降级为知识库原文**时随原文展示；`attemptedSteps()` 仍过滤旧格式里的判据行，兼容历史会话。

---

## 4. 报修 AI：结构化抽取，保持原样

报修页的 `AiClient` 与解析逻辑**不做重构**，继续由伍梦圆负责。
诊断侧只通过 `done` 事件把已经判断好的结论交给报修页：

| 字段 | 来源 | 用途 |
|---|---|---|
| `categoryId` | 命中知识条目的 `category_id` | 预填故障类别 |
| `emergencyLevel` | `DiagnosisRules` 规则推导 | 预填紧急程度 |
| `matchedKbId` | Top-1 命中条目 ID | 追溯诊断依据 |
| `attemptedSteps` | 上一轮回答的"③分步操作" | 告诉学生哪些已经试过 |
| `diagnosisSessionId` | 本轮问答记录 | 串联漏斗数据 |
| `conversationId` | 前端生成 | 多轮上下文 |

**主动中止不发 `done`，因此不会带出任何报修信息。**

---

## 5. 漏斗接力：诊断 → 报修

### 5.1 一次接力的数据流

```
诊断页（WS done）
  → 写入 sessionStorage.repairHandoff（含一次性 nonce）
  → 跳 /repair?handoff=<nonce>（URL 只带 nonce，不带描述和答案）
  → 报修页读取后立即删除 sessionStorage 项
  → 预填故障描述 + 诊断字段
  → autoParsed 守卫下自动触发一次 parseRepair()
  → 学生确认/补充后提交工单
```

### 5.2 为什么 URL 只带 nonce

- 诊断结论可能包含具体故障描述和处置建议，放进 URL 会进浏览器历史、Nginx access log、分享链接；
- nonce 是一次性凭据，读取即删，刷新页面不会重复触发自动解析；
- 数据主体留在 `sessionStorage`，不落库、不外发。

### 5.3 多轮上下文

- 前端每次进入诊断页生成一个 `conversationId`，`crypto.randomUUID()` 不可用时降级为
  `Date.now() + Math.random()`（局域网 HTTP 非安全上下文必需）；
- 后端按 `conversation_id + user_id` 取最近 3 轮，注入模型 prompt；
- 学生点"没解决"后，下一轮不再重复旧步骤，直接指向报修。

---

## 6. 通道与中止：对外一套，对内两条

对外产品形态**只有 WebSocket 实时诊断**。SSE 是内部降级通道，不是第二套产品。

- **WS 主通道**：`/ws/diagnosis`，握手令牌放在 `Sec-WebSocket-Protocol` 子协议里，
  复用项目 JWT + 指纹校验，服务端必须回显子协议；
- **SSE 降级**：只有"WS 连不上"或"连上但 `done` 前断链"才用同一问题重发一次；
  收到 `done` 或 `aborted` 后都不重发；
- **同步 HTTP 兜底**：SSE 也不通时返回知识库原文；
- **传输抽象**：`DiagnosisEventSink` 只有 `emit`/`complete` 两个方法，
  `SseEventSink` 和 `WebSocketEventSink` 只转发事件，业务判断全部在 `DiagnosisEngine`；
- **事件统一**：`meta / delta / done / error / feedback_ack / aborted`，两条通道共用同一套前端解析逻辑。

### 6.1 用户主动中止

- WS 生成中显示"停止生成"按钮，点击上行 `{"type":"abort","conversationId":"..."}`；
  SSE 降级模式没有上行通路，**隐藏停止按钮**；
- 后端 `AbortRegistry` 用 `ConcurrentHashMap` 维护 `userId + conversationId → AbortHandle`
  （`volatile` 布尔）；`afterConnectionClosed` 按 userId 中止该用户全部运行中任务；
- 流式回调每收到一块 token 先查中止标记，命中即抛 `AbortException` 并关闭上游 SSE 连接，
  立即停止消耗 token；
- 下行发 `aborted`，**不发 `done`**；前端提示"已停止生成，当前回答不会保存，不会自动带出报修信息"，
  半截答案不显示、不回填；
- DB 中该轮会话打 `aborted=true` 且 `answer=NULL`，不触发报修接力。

---

## 7. 统计口径

`diagnosis_session.is_solved` 是可空三态：

| 值 | 含义 |
|---|---|
| `NULL` | 未反馈（默认值） |
| `0` | 学生点了"没解决" |
| `1` | 学生点了"解决了" |

- **解决率 = 已解决数 / 已反馈数**，分母不含未反馈会话；
- `noHit`（未命中数）**排除**用户主动中止；
- `aborted` 单独计数（点停止 / 连接断开），不和匹配失败、报错混一类；
- 新库由 `docs/sql/init.sql` 直接包含 `is_solved` 与 `aborted` 字段，无需再执行历史迁移脚本。

---

## 8. 配置项一览

| 配置 | 默认值 | 含义 |
|---|---|---|
| `ZHIPU_API_KEY` | 无 | 诊断对话智谱 key，只放本地 `.env`；未配置则对话降级 |
| `ZHIPU_BASE_URL` | `https://open.bigmodel.cn/api/paas/v4` | 智谱 OpenAI 兼容地址 |
| `ZHIPU_CHAT_MODEL` | `glm-4-flashx-250414` | 诊断流式对话模型（付费 0.1/0.1 元每百万 Token，无思考模式） |
| `SILICONFLOW_API_KEY` | 无 | 诊断向量化硅基流动 key，只放本地 `.env`；未配置则降级 LIKE |
| `SILICONFLOW_BASE_URL` | `https://api.siliconflow.cn/v1` | 硅基流动 OpenAI 兼容地址 |
| `SILICONFLOW_EMBEDDING_MODEL` | `BAAI/bge-m3` | 查询与知识条目的向量化模型（免费，1024 维，不支持 dimensions） |
| `rag.min-similarity` | `0.6` | 低于此值不进 Top-3 |
| `rag.low-confidence` | `0.7` | 0.6~0.7 之间命中但主动示弱 |
| `AI_API_KEY` / `AI_MODEL` | 无 / `qwen-vl-plus` | 报修 AI（伍梦圆），本侧不改 |

---

## 9. 维护提示

- 改诊断提示词、阈值、分层拦截、中止行为 → 只改 `DiagnosisEngine` / `DiagnosisRules` / `AbortRegistry`；
- 改向量化/检索策略 → 只改 `KnowledgeVectorCache` / `SiliconFlowEmbeddingServiceImpl`；
- 改种子答案或判据 → 直接改 `docs/sql/knowledge-seed.sql` 后重灌，
  再调 `POST /api/knowledge/cache/refresh`；
- 改报修字段抽取 → 找伍梦圆，不在诊断侧动；
- **不要为诊断新建知识表，不要碰 `frontend/src/views/Repair.vue` 的预填改动。**
