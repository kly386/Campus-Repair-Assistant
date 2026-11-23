# AI 两个场景说明

> 校园快修服务助手 —— 诊断 AI 与报修 AI 的职责边界
> 版本：v1.0
> 负责人：闵娟（诊断 AI）、伍梦圆（报修 AI）

---

## 1. 一句话区分

系统里有两个 AI 场景，**业务上分工、协议上统一**：

| | 诊断 AI（自助诊断） | 报修 AI（智能报修） |
|---|---|---|
| 入口 | `/ws/diagnosis`（主）、`/api/knowledge/session/stream`（降级） | 报修页 `AI 智能解析` 按钮 |
| 任务 | 判断"能不能自己修好"，能修就给步骤，修不好就分流 | 把"一句话 + 照片"抽成工单结构化字段 |
| 输出 | 五段式自然语言：①初步判断 ②先确认什么 ③分步操作（含成功判据）④还不行怎么办 ⑤安全提醒 | 故障类别、楼栋房间号、紧急程度、预约时间等字段 |
| 依据 | 只依据 `knowledge_base` 的 Top-3 命中条目（grounded） | 学生填写的描述 + 上传照片 |
| 核心代码 | `DiagnosisEngine`（唯一业务逻辑） | 伍梦圆的 `AiClient` / 报修解析逻辑（不改动） |
| 负责人 | 闵娟 | 伍梦圆 |

**关键结论：两个场景是同一套 OpenAI 兼容协议上的两次不同调用，不是两个模型、两套封装、两份密钥。**

---

## 2. 同一 OpenAI 兼容协议

两个场景共用同一份配置，不新增任何 API key：

```properties
dashscope.base-url=${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
dashscope.api-key=${DASHSCOPE_API_KEY:}
dashscope.chat.model=${DASHSCOPE_CHAT_MODEL:kimi-k3.1-pro}
dashscope.embedding.model=${DASHSCOPE_EMBEDDING_MODEL:text-embedding-v4}
```

- 诊断 AI 走 `/chat/completions?stream=true`，要逐字输出 → `DashScopeChatStreamService`
- 报修 AI 走同一协议的 JSON / Function Calling 能力 → 伍梦圆的 `AiClient`
- 文本向量化走同协议的 `/embeddings` → `EmbeddingService`

三者只是 **endpoint 与提示词不同**，密钥、base-url、SDK 约定完全一致。
`DASHSCOPE_API_KEY` 只通过 `.env` / 环境变量注入，不进代码、不进 SQL、不进提交记录。
默认对话模型是 `kimi-k3.1-pro`（已开启"免费额度用完即停"，额度用完直接拒绝调用，
不会再超量计费导致欠费）；
个人如需覆盖，在自己 `.env` 里设 `DASHSCOPE_CHAT_MODEL` 即可。仓库根的 `.env.example`
是不含密钥的模板，复制为 `.env` 后填自己的值，不要把填好的 `.env` 提交。

---

## 3. 诊断 AI：RAG 分流，不是聊天机器人

### 3.1 为什么必须 grounded

诊断 AI 的目的是**分流**，不是陪聊。如果模型凭通用知识给步骤，学生照着做可能引发触电、漏水等安全事故。
所以 `DiagnosisEngine` 的五段式提示词里有硬性约束：

1. 只能用检索到的知识条目原文，禁止用通用知识补步骤；
2. 步骤后必须带"成功判据"，判据只能从原文推断，原文没有就写"以现场现象为准"；
3. 条目不足以回答时，直接说"不确定，建议报修"，不猜测；
4. 闲聊、情感、学习类问题礼貌拒答；
5. 涉及水电火气的关键词由 `DiagnosisRules` **规则拦截**，根本不进模型。

### 3.2 成功判据采用 A+B 方案

- **A（主）**：50 条种子 `knowledge_base.answer` 末尾直接写"成功判据：…"，
  由 `scripts/generate-knowledge-seed.py` 生成，`INSERT ... ON DUPLICATE KEY UPDATE` 保证重灌能更新。
- **B（兜底）**：提示词禁止模型编造判据；种子没写就输出"以现场现象为准"。

模型只做归纳，不做数字发明。

### 3.3 防幻觉的四道闸

```
用户问题
  ↓ ① 关键词规则（DiagnosisRules）
  │   紧急词 → 只给安全提示，不调模型
  │   非维修词 → 礼貌拒答，不调模型
  ↓ ② 向量检索（min-similarity=0.6）
  │   无命中 → 知识库兜底文案，不调模型
  ↓ ③ 低置信度（low-confidence=0.7）
  │   0.6~0.7 → 先声明"不太确定"再回答，并打低置信度标记
  ↓ ④ grounded 生成 + 引用溯源
      五段式输出，meta 事件把命中条目推给前端可展开核对
```

任一层拦下都不影响主流程；模型调用失败时自动降级为 Top-1 原文，学生看到的内容不会断。

---

## 4. 报修 AI：结构化抽取，保持原样

报修页的 `AiClient` 与解析逻辑**不做重构**，继续由伍梦圆负责。
诊断侧只通过 `done` 事件把已经判断好的结论交给报修页：

| 字段 | 来源 | 用途 |
|------|------|------|
| `categoryId` | 命中知识条目的 `category_id` | 预填故障类别 |
| `emergencyLevel` | `DiagnosisRules` 规则推导 | 预填紧急程度 |
| `matchedKbId` | Top-1 命中条目 ID | 追溯诊断依据 |
| `attemptedSteps` | 上一轮回答的"③分步操作" | 告诉学生哪些已经试过 |
| `diagnosisSessionId` | 本轮问答记录 | 串联漏斗数据 |
| `conversationId` | 前端生成 | 多轮上下文 |

报修 AI 拿到这些字段后**照常做自己的结构化抽取**，不复用诊断提示词，也不反过来改诊断逻辑。

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

## 6. 通道设计：对外一套，对内两条

对外产品形态**只有 WebSocket 实时诊断**。SSE 是内部降级通道，不是第二套产品。

- **WS 主通道**：`/ws/diagnosis`，握手令牌放在 `Sec-WebSocket-Protocol` 子协议里，
  复用项目 JWT + 指纹校验，服务端必须回显子协议；
- **SSE 降级**：只有"WS 连不上"或"连上但 `done` 前断链"才用同一问题重发一次；
  收到 `done` 后断链不重发；
- **传输抽象**：`DiagnosisEventSink` 只有 `emit`/`complete` 两个方法，
  `SseEventSink` 和 `WebSocketEventSink` 只转发事件，业务判断全部在 `DiagnosisEngine`；
- **事件统一**：`meta / delta / done / error / feedback_ack`，两条通道共用同一套前端解析逻辑。

---

## 7. 解决率口径

`diagnosis_session.is_solved` 改为可空三态：

| 值 | 含义 |
|----|------|
| `NULL` | 未反馈（默认值） |
| `0` | 学生点了"没解决" |
| `1` | 学生点了"解决了" |

**解决率 = 已解决数 / 已反馈数**，分母不含未反馈会话，避免新会话把指标稀释。
旧库通过 `docs/sql/diagnosis-conversation-migration.sql` 迁移，历史 `0` 统一按"未反馈"处理。

---

## 8. 配置项一览

| 配置 | 默认值 | 含义 |
|------|--------|------|
| `rag.min-similarity` | `0.6` | 低于此值不进 Top-3 命中 |
| `rag.low-confidence` | `0.7` | 0.6~0.7 之间命中但主动示弱 |
| `DASHSCOPE_API_KEY` | 无 | 两个 AI 场景共用，只放 `.env` |
| `DASHSCOPE_CHAT_MODEL` | `kimi-k3.1-pro` | 诊断 AI 流式对话模型，个人可用 `.env` 覆盖 |
| `DASHSCOPE_EMBEDDING_MODEL` | `text-embedding-v4` | 查询向量化模型，失败自动降级 LIKE + 口语同义词 |
| `DASHSCOPE_BASE_URL` | 阿里云兼容模式 | 两个 AI 场景共用 |

---

## 9. 维护提示

- 改诊断提示词、阈值、分层拦截 → 只改 `DiagnosisEngine` / `DiagnosisRules`；
- 改种子答案或判据 → 改 `scripts/generate-knowledge-seed.py` 后重新生成并重灌；
- 改报修字段抽取 → 找伍梦圆，不在诊断侧动；
- 新增 AI 场景 → 继续复用同一 OpenAI 兼容协议，不要新建第二套密钥封装。
