<template>
  <div class="diagnosis">
    <div class="page-head">
      <div>
        <h2>自助诊断 · 实时维修助手</h2>
        <p class="subtitle">描述你的问题，助手会实时分析并给出可执行的排查步骤；修不好就直接转报修。</p>
      </div>
      <div class="head-right">
        <span class="conn-badge" :class="wsStatus">
          <i class="dot"></i>{{ connText }}
        </span>
        <div v-if="stats.feedback" class="stats-chip">
          <span>已服务 {{ stats.total }} 次</span>
          <el-divider direction="vertical" />
          <span>自助解决率 <b>{{ stats.solvedRate }}%</b></span>
          <template v-if="stats.aborted">
            <el-divider direction="vertical" />
            <span>主动中止 {{ stats.aborted }}</span>
          </template>
        </div>
      </div>
    </div>

    <el-card class="chat-card">
      <div ref="chatAreaRef" class="chat-area">
        <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
          <div v-if="msg.role === 'system'" v-html="msg.text"></div>
          <template v-else>
            <div v-if="!msg.aborted" class="msg-text" :class="{ streaming: msg.streaming }">{{ msg.text }}</div>

            <div v-if="msg.lowConfidence" class="notice warn">
              回答把握不大，仅供参考；不行就直接报修，别勉强自己处理。
            </div>
            <div v-if="msg.needsContext" class="notice info">
              这句像是在追问，但当前没有对应的上下文，请补充说明具体设备和现象。
            </div>
            <div v-if="msg.emergency" class="notice danger">
              涉及用水用电安全，请勿自行处理，建议立即报修并联系宿管。
            </div>
            <div v-if="msg.degraded" class="notice info">
              实时生成暂不可用，已自动切换为知识库原文，功能不受影响。
            </div>
            <div v-if="msg.aborted" class="notice info">
              已停止生成，当前回答不会保存，不会自动带出报修信息。
            </div>

            <!-- 引用溯源 -->
            <div v-if="!msg.aborted && msg.matches && msg.matches.length" class="ref-block">
              <div class="ref-toggle" @click="msg.showRefs = !msg.showRefs">
                <span>📚 参考了 {{ msg.matches.length }} 条知识{{ msg.showRefs ? '（收起）' : '（展开）' }}</span>
                <el-tag size="small" type="success">{{ msg.searchMode === 'VECTOR' ? '语义检索' : '关键词检索' }}</el-tag>
              </div>
              <div v-if="msg.showRefs" class="match-list">
                <div v-for="m in msg.matches" :key="m.id" class="match-card">
                  <div class="match-title">
                    {{ m.title }}
                    <el-tag v-if="m.similarity != null" size="small" type="success" class="sim-tag">
                      相似度 {{ (m.similarity * 100).toFixed(1) }}%
                    </el-tag>
                  </div>
                  <div class="match-a">{{ m.answer }}</div>
                </div>
              </div>
            </div>

            <!-- 追问建议 -->
            <div v-if="!msg.aborted && !msg.closed && msg.suggestions && msg.suggestions.length && msg.done" class="suggestions">
              <span class="suggest-label">你还可以问：</span>
              <button v-for="(s, si) in msg.suggestions" :key="si" class="suggest-chip" type="button" @click="ask(s)">
                {{ s }}
              </button>
            </div>

            <!-- 反馈闭环 + 转报修 -->
            <div v-if="!msg.aborted && !msg.closed && !msg.triedBefore && msg.done && !msg.feedbackDone" class="feedback-area">
              <span>上面的方法解决您的问题了吗？</span>
              <el-button size="small" type="success" @click="feedback(msg, true)">解决了</el-button>
              <el-button size="small" type="info" @click="feedback(msg, false)">没解决</el-button>
              <el-button size="small" type="warning" plain @click="goRepair(msg)">直接报修</el-button>
            </div>
            <div v-else-if="msg.feedbackDone && !msg.aborted" class="feedback-done">已收到您的反馈，谢谢</div>
          </template>
        </div>

        <div v-if="loading" class="message system typing">
          {{ stage === 'search' ? '正在检索知识库…' : '助手正在输入…' }}
        </div>
      </div>

      <div class="hot-questions" v-if="hotQuestions.length">
        <span class="hot-label">大家都在问：</span>
        <el-button
          v-for="(item, i) in hotQuestions"
          :key="i"
          size="small"
          text
          bg
          :disabled="loading"
          @click="ask(item.question)"
        >
          {{ item.question }}
        </el-button>
      </div>

      <div class="input-area">
        <el-input
          v-model="question"
          placeholder="例如：路由器亮红灯怎么办"
          maxlength="200"
          :disabled="loading"
          @keyup.enter="ask"
        />
        <el-button type="primary" :loading="loading" @click="ask">发送</el-button>
        <el-button
          v-if="loading && activeIsWs"
          type="danger"
          plain
          :disabled="abortRequested"
          @click="stopGeneration"
        >
          {{ abortRequested ? '正在停止…' : '停止生成' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import http from '../api/http';
import { getAccessToken, getClientFingerprint } from '../auth/session';

const router = useRouter();
const question = ref('');
const loading = ref(false);
const stage = ref('search');
const chatAreaRef = ref(null);
const hotQuestions = ref([]);
const stats = ref({ total: 0, feedback: 0, solvedRate: 0, aborted: 0 });

const ws = ref(null);
const wsStatus = ref('connecting'); // connecting | connected | fallback
const wsRetries = ref(0);
const conversationId = ref('');
let reconnectTimer = null;
let activeMsg = null;
let doneResolve = null;
let doneReject = null;
let feedbackResolve = null;
let intentionalClose = false;
// 停止按钮只在 WS 通道显示：SSE 是 fetch 流，发上行 abort 没有对应通路
const activeIsWs = ref(false);
const abortRequested = ref(false);
let abortTimer = null;

const connText = computed(() => ({
  connecting: '正在连接实时通道…',
  connected: '实时通道已连接',
  fallback: '实时通道不可用 · 兼容模式'
}[wsStatus.value]));

const messages = ref([
  { role: 'system', text: '您好！我是维修小助手，只解答宿舍设施维修问题。请描述您遇到的情况，我会实时分析并给出建议。' }
]);

function scrollBottom() {
  nextTick(() => {
    if (chatAreaRef.value) {
      chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight;
    }
  });
}

/** 局域网 HTTP 不是安全上下文时 crypto.randomUUID 可能不可用，必须有 fallback */
function makeConversationId() {
  return globalThis.crypto?.randomUUID?.()
    || `conv-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** 把字符串转成 WebSocket 子协议允许的 base64url（不含逗号、点、空格） */
function b64url(value) {
  const bytes = new TextEncoder().encode(value || '');
  let binary = '';
  bytes.forEach((b) => { binary += String.fromCharCode(b); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function buildWsProtocol() {
  return `campus-diag.${b64url(getAccessToken())}.${b64url(getClientFingerprint())}`;
}

function connectWebSocket() {
  const token = getAccessToken();
  if (!token) {
    wsStatus.value = 'fallback';
    return;
  }
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const socket = new WebSocket(`${scheme}://${location.host}/ws/diagnosis`, [buildWsProtocol()]);
  ws.value = socket;

  socket.onopen = () => {
    wsStatus.value = 'connected';
    wsRetries.value = 0;
  };

  socket.onmessage = (event) => {
    let envelope;
    try {
      envelope = JSON.parse(event.data);
    } catch {
      return;
    }
    handleWsEvent(envelope.type, envelope.data || {});
  };

  const dropped = () => {
    if (intentionalClose) return;
    if (doneResolve) {
      doneReject?.(new Error('实时连接中断'));
      clearPending();
    }
    wsStatus.value = 'fallback';
    // 后端暂时没起来时给两次重连机会，之后稳定走 SSE，不刷屏
    if (wsRetries.value < 2) {
      wsRetries.value += 1;
      reconnectTimer = setTimeout(connectWebSocket, 5000);
    }
  };
  // onerror 后面一定还会触发 onclose，重连逻辑只放在 onclose，避免重复连接
  socket.onerror = () => {
    wsStatus.value = 'fallback';
  };
  socket.onclose = dropped;
}

function clearPending() {
  doneResolve = null;
  doneReject = null;
}

function isWsReady() {
  return wsStatus.value === 'connected' && ws.value?.readyState === WebSocket.OPEN;
}

/** 统一处理下行事件：WS 与 SSE 共用同一套 UI 更新逻辑 */
function handleEvent(type, data, msg) {
  if (type === 'meta') {
    msg.sessionId = data.sessionId;
    msg.searchMode = data.searchMode;
    msg.matches = data.matches || [];
    msg.lowConfidence = !!data.lowConfidence;
    // triedBefore / contextFollowUp 都是真实多轮，不能再显示"没有上下文"的误导提示
    msg.needsContext = !!data.needsContext && !data.triedBefore && !data.contextFollowUp;
    msg.triedBefore = !!data.triedBefore;
    msg.contextFollowUp = !!data.contextFollowUp;
    msg.emergency = !!data.emergency;
    msg.closed = !!data.closed;
    if (!data.emergency && !data.closed && !data.offTopic && !data.needsContext && !data.triedBefore && !data.contextFollowUp) stage.value = 'generate';
    msg.streaming = true;
  } else if (type === 'delta') {
    msg.text += data.text || '';
    scrollBottom();
  } else if (type === 'done') {
    msg.text = data.answer || msg.text;
    msg.degraded = !!data.degraded;
    msg.closed = !!data.closed;
    msg.suggestions = data.suggestions || [];
    // 报修接力字段
    msg.categoryId = data.categoryId || null;
    msg.emergencyLevel = data.emergencyLevel || 'NORMAL';
    msg.matchedKbId = data.matchedKbId || null;
    msg.attemptedSteps = data.attemptedSteps || [];
    msg.diagnosisSessionId = data.diagnosisSessionId || data.sessionId || null;
    msg.conversationId = data.conversationId || conversationId.value;
    finishMessage(msg);
  } else if (type === 'aborted') {
    // 用户主动中止：半截答案不保留，不回填；done=true 只为阻止重发，
    // 反馈/追问/报修接力全部由 msg.aborted 在模板里屏蔽
    msg.aborted = true;
    msg.text = '';
    msg.streaming = false;
    msg.matches = [];
    msg.suggestions = [];
    msg.done = true;
    if (abortTimer) {
      clearTimeout(abortTimer);
      abortTimer = null;
    }
    scrollBottom();
  }
}

function handleWsEvent(type, data) {
  if (type === 'feedback_ack') {
    feedbackResolve?.(data);
    feedbackResolve = null;
    return;
  }
  const msg = activeMsg;
  if (!msg) return;
  if (type === 'meta' || type === 'delta' || type === 'done' || type === 'aborted') {
    handleEvent(type, data, msg);
  }
  if (type === 'done' || type === 'aborted') {
    doneResolve?.();
    clearPending();
    activeMsg = null;
  } else if (type === 'error') {
    doneReject?.(new Error(data.message || '实时生成失败'));
    clearPending();
  }
}

onMounted(async () => {
  conversationId.value = makeConversationId();
  connectWebSocket();
  try {
    const [hot, st] = await Promise.all([
      http.get('/knowledge/hot-questions'),
      http.get('/knowledge/diagnosis/stats')
    ]);
    if (hot.data?.code === 200) hotQuestions.value = hot.data.data || [];
    if (st.data?.code === 200) stats.value = st.data.data || stats.value;
  } catch {
    // 统计与热问失败不影响主流程
  }
});

onBeforeUnmount(() => {
  intentionalClose = true;
  if (reconnectTimer) clearTimeout(reconnectTimer);
  if (abortTimer) clearTimeout(abortTimer);
  if (ws.value) ws.value.close();
  ws.value = null;
});

/** 提问入口：WS 优先；连不上直接 SSE；连上但 done 前断链用 SSE 重发一次 */
async function ask(text) {
  const q = (typeof text === 'string' ? text : question.value).trim();
  if (!q || loading.value) return;

  messages.value.push({ role: 'user', text: q });
  question.value = '';
  loading.value = true;
  stage.value = 'search';
  activeIsWs.value = false;
  abortRequested.value = false;
  if (abortTimer) {
    clearTimeout(abortTimer);
    abortTimer = null;
  }
  scrollBottom();

  const msg = {
    role: 'assistant',
    aborted: false,
    question: q,
    text: '',
    streaming: false,
    done: false,
    feedbackDone: false,
    matches: [],
    suggestions: [],
    showRefs: false,
    lowConfidence: false,
    needsContext: false,
    triedBefore: false,
    contextFollowUp: false,
    emergency: false,
    closed: false,
    degraded: false,
    categoryId: null,
    emergencyLevel: 'NORMAL',
    matchedKbId: null,
    attemptedSteps: [],
    diagnosisSessionId: null,
    conversationId: conversationId.value
  };
  messages.value.push(msg);
  activeMsg = msg;

  let usedWs = false;
  try {
    if (isWsReady()) {
      usedWs = true;
      activeIsWs.value = true;
      const done = new Promise((resolve, reject) => {
        doneResolve = resolve;
        doneReject = reject;
      });
      ws.value.send(JSON.stringify({
        type: 'ask',
        question: q,
        conversationId: conversationId.value
      }));
      await done;
    } else {
      activeIsWs.value = false;
      await streamAsk(q, msg);
    }
  } catch (e) {
    if (usedWs && !msg.done && !msg.aborted) {
      // WS 已连上但 done 前断开：同一问题用 SSE 重发一次，清空已收到的半截文本
      msg.text = '';
      msg.streaming = false;
      msg.matches = [];
      try {
        await streamAsk(q, msg);
      } catch {
        fallbackSync(q, msg);
      }
    } else if (!usedWs) {
      fallbackSync(q, msg);
    }
  } finally {
    loading.value = false;
    msg.streaming = false;
    if (abortTimer) {
      clearTimeout(abortTimer);
      abortTimer = null;
    }
    if (activeMsg === msg) activeMsg = null;
    activeIsWs.value = false;
    clearPending();
    scrollBottom();
  }
}

/** 用户点"停止生成"：WS 上行 abort，后端断上游 SSE 并发 aborted 事件 */
function stopGeneration() {
  if (!activeMsg || abortRequested.value || !isWsReady()) return;
  abortRequested.value = true;
  const target = activeMsg;
  ws.value.send(JSON.stringify({
    type: 'abort',
    conversationId: conversationId.value
  }));
  // 服务端 aborted 丢失时本地兜底收尾，避免 loading 卡死。
  // 这里 resolve 而不是 reject，防止被 ask 的 catch 当成断链去重发 SSE。
  abortTimer = setTimeout(() => {
    if (activeMsg !== target || target.aborted) return;
    handleEvent('aborted', {}, target);
    doneResolve?.();
    clearPending();
    activeMsg = null;
  }, 3000);
}

/** fetch + ReadableStream 读取 SSE（降级通道），解析 event: / data: 块 */
async function streamAsk(q, msg) {
  const resp = await fetch('/api/knowledge/session/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getAccessToken()}`,
      'X-Client-Fingerprint': getClientFingerprint()
    },
    body: JSON.stringify({ question: q, conversationId: conversationId.value })
  });
  if (!resp.ok || !resp.body) throw new Error('流式接口不可用');

  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let finished = false;
  let aborted = false;

  const consume = (block) => {
    let event = 'message';
    const dataLines = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
    }
    if (!dataLines.length) return;
    let payload;
    try {
      payload = JSON.parse(dataLines.join('\n'));
    } catch {
      return;
    }
    handleEvent(event, payload, msg);
    if (event === 'done' || event === 'aborted') finished = true;
    if (event === 'aborted') aborted = true;
    if (event === 'error') throw new Error(payload.message || '流式生成失败');
  };

  while (!aborted) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
    let idx;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      consume(buffer.slice(0, idx));
      buffer = buffer.slice(idx + 2);
    }
  }
  if (buffer.trim()) consume(buffer);
  if (!finished) throw new Error('流式连接提前结束');
}

/** SSE 也不通时的最后降级：同步问答 */
function fallbackSync(q, msg) {
  msg.text = '';
  msg.degraded = true;
  http.post('/knowledge/session', { question: q, conversationId: conversationId.value })
    .then((resp) => {
      if (resp.data?.code !== 200) throw new Error(resp.data?.message || '提问失败');
      const data = resp.data.data;
      msg.text = data.answer || '';
      msg.matches = data.matches || [];
      msg.sessionId = data.sessionId;
      msg.categoryId = data.categoryId || null;
      msg.emergencyLevel = data.emergencyLevel || 'NORMAL';
      msg.diagnosisSessionId = data.sessionId;
      msg.conversationId = conversationId.value;
    })
    .catch(() => {
      msg.text = '服务暂时不可用，请稍后再试，或直接提交报修工单。';
      msg.sessionId = null;
    })
    .finally(() => finishMessage(msg));
}

function finishMessage(msg) {
  msg.streaming = false;
  msg.done = true;
  scrollBottom();
}

/** 反馈：WS 通道走上行 feedback 消息并等 ack；降级时走 HTTP */
async function feedback(msg, solved) {
  if (!msg.sessionId) {
    ElMessage.warning('当前会话不可反馈，请重新提问');
    return;
  }
  try {
    if (isWsReady()) {
      const ack = new Promise((resolve) => {
        feedbackResolve = resolve;
      });
      ws.value.send(JSON.stringify({
        type: 'feedback',
        sessionId: msg.sessionId,
        solved
      }));
      const timeout = new Promise((resolve) => setTimeout(() => resolve(null), 4000));
      const result = await Promise.race([ack, timeout]);
      feedbackResolve = null;
      if (!result || result.ok === false) throw new Error('ack failed');
    } else {
      await http.put(`/knowledge/session/${msg.sessionId}/solved`, null, { params: { solved } });
    }
    msg.feedbackDone = true;
    messages.value.push({
      role: 'system',
      text: solved
        ? '太好了！已记入知识库，感谢反馈。'
        : '很抱歉没帮上您，已把诊断结论带到报修页，师傅会尽快处理。'
    });
    scrollBottom();
  } catch {
    ElMessage.error('反馈失败，请重试');
  }
}

/** 转报修：把诊断结论放进 sessionStorage，URL 只带一次性 nonce */
function goRepair(msg) {
  const nonce = `handoff-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  sessionStorage.setItem('repairHandoff', JSON.stringify({
    question: msg.question,
    answer: msg.text,
    categoryId: msg.categoryId,
    emergencyLevel: msg.emergencyLevel,
    matchedKbId: msg.matchedKbId,
    attemptedSteps: msg.attemptedSteps || [],
    diagnosisSessionId: msg.diagnosisSessionId,
    conversationId: msg.conversationId || conversationId.value,
    nonce
  }));
  router.push({ path: '/repair', query: { handoff: nonce } });
}
</script>

<style scoped>
.diagnosis h2 {
  margin: 0 0 4px;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  gap: 16px;
}
.subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.conn-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 16px;
  padding: 6px 12px;
  font-size: 12px;
  white-space: nowrap;
}
.conn-badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}
.conn-badge.connected {
  background: #f0f9eb;
  color: #67c23a;
}
.conn-badge.connecting {
  background: #fdf6ec;
  color: #e6a23c;
}
.conn-badge.fallback {
  background: #f4f4f5;
  color: #909399;
}
.stats-chip {
  flex-shrink: 0;
  background: #ecf5ff;
  color: #409eff;
  border-radius: 16px;
  padding: 7px 14px;
  font-size: 13px;
}
.chat-card {
  max-width: 820px;
}
.chat-area {
  min-height: 340px;
  max-height: 500px;
  overflow-y: auto;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 6px;
  margin-bottom: 12px;
}
.message {
  padding: 10px 14px;
  border-radius: 10px;
  margin-bottom: 12px;
  max-width: 82%;
  word-break: break-word;
  line-height: 1.7;
}
.message.user {
  background: #dcedc8;
  margin-left: auto;
}
.message.assistant {
  background: #fff;
  border: 1px solid #e4e7ed;
  max-width: 92%;
}
.message.system {
  background: #e3f2fd;
  color: #1976d2;
  max-width: 96%;
  font-size: 13px;
}
.msg-text {
  white-space: pre-wrap;
}
.msg-text.streaming::after {
  content: '▍';
  animation: blink 1s infinite;
  color: #409eff;
}
@keyframes blink {
  50% { opacity: 0.2; }
}
.typing {
  animation: fade 1.4s infinite;
}
@keyframes fade {
  50% { opacity: 0.5; }
}
.notice {
  margin-top: 8px;
  padding: 7px 10px;
  border-radius: 5px;
  font-size: 12px;
  line-height: 1.6;
}
.notice.warn {
  background: #fdf6ec;
  color: #e6a23c;
}
.notice.danger {
  background: #fef0f0;
  color: #f56c6c;
  font-weight: bold;
}
.notice.info {
  background: #f4f4f5;
  color: #909399;
}
.ref-block {
  margin-top: 8px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 6px;
}
.ref-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
}
.match-list {
  margin-top: 8px;
}
.match-card {
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 6px;
  background: #fafafa;
}
.match-title {
  font-weight: bold;
  font-size: 13px;
  margin-bottom: 4px;
}
.sim-tag {
  margin-left: 6px;
}
.match-a {
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
}
.suggestions {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.suggest-label {
  font-size: 12px;
  color: #909399;
}
.suggest-chip {
  padding: 3px 10px;
  border: 1px solid #409eff;
  border-radius: 12px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}
.suggest-chip:hover {
  background: #d9ecff;
}
.feedback-area {
  margin-top: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}
.feedback-done {
  margin-top: 8px;
  font-size: 12px;
  color: #67c23a;
}
.hot-questions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}
.hot-label {
  font-size: 13px;
  color: #909399;
}
.input-area {
  display: flex;
  gap: 10px;
}
</style>
