<template>
  <div class="diagnosis">
    <h2>自助诊断</h2>
    <el-card class="chat-card">
      <div ref="chatAreaRef" class="chat-area">
        <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
          <div v-if="msg.role === 'system'" v-html="msg.text"></div>
          <template v-else>
            <div class="msg-text">{{ msg.text }}</div>
            <!-- 提问后的命中条目卡片 -->
            <div v-if="msg.matches && msg.matches.length" class="match-list">
              <div v-for="m in msg.matches" :key="m.id" class="match-card">
                <div class="match-title">
                  {{ m.title }}
                  <el-tag v-if="m.similarity != null" size="small" type="success" class="sim-tag">
                    相似度 {{ (m.similarity * 100).toFixed(1) }}%
                  </el-tag>
                </div>
                <div class="match-q">❓ {{ m.question }}</div>
                <div class="match-a">{{ m.answer }}</div>
                <div class="match-foot">
                  <span class="helpful">👍 {{ m.helpful }} 人觉得有用</span>
                </div>
              </div>
              <!-- 反馈区：只有会话反馈过一次后消失 -->
              <div v-if="!msg.feedbackDone" class="feedback-area">
                <span>上面的方法解决您的问题了吗？</span>
                <el-button size="small" type="success" @click="feedback(msg, true)">解决了</el-button>
                <el-button size="small" type="info" @click="feedback(msg, false)">没解决</el-button>
              </div>
            </div>
          </template>
        </div>
        <div v-if="loading" class="message system typing">小助手正在检索知识库…</div>
      </div>
      <div class="input-area">
        <el-input
          v-model="question"
          placeholder="例如：路由器亮红灯怎么办"
          :disabled="loading"
          @keyup.enter="ask"
        />
        <el-button type="primary" :loading="loading" @click="ask">提问</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import http from '../api/http';

const question = ref('');
const loading = ref(false);
const chatAreaRef = ref(null);
const messages = ref([
  { role: 'system', text: '您好！我是维修小助手，请描述您遇到的问题，我会帮您判断是否可以自行解决。' }
]);

/** 滚动到底部 */
function scrollBottom() {
  nextTick(() => {
    if (chatAreaRef.value) {
      chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight;
    }
  });
}

/** 提问：POST /api/knowledge/session（RAG 向量检索 Top-3） */
async function ask() {
  const q = question.value.trim();
  if (!q || loading.value) return;
  messages.value.push({ role: 'user', text: q });
  question.value = '';
  loading.value = true;
  scrollBottom();
  try {
    const resp = await http.post('/knowledge/session', { question: q });
    const payload = resp.data;
    if (payload.code !== 200) {
      throw new Error(payload.message || '提问失败');
    }
    const data = payload.data;
    if (!data.matches || data.matches.length === 0) {
      messages.value.push({ role: 'system', text: data.answer });
    } else {
      messages.value.push({ role: 'assistant', text: data.answer, matches: data.matches, sessionId: data.sessionId, feedbackDone: false });
    }
  } catch (e) {
    messages.value.push({ role: 'system', text: '服务出了点小差，请稍后再试，或直接提交报修工单。' });
  } finally {
    loading.value = false;
    scrollBottom();
  }
}

/** 反馈：PUT /api/knowledge/session/{id}/solved?solved=，解决时命中条目 helpful+1 */
async function feedback(msg, solved) {
  try {
    await http.put(`/knowledge/session/${msg.sessionId}/solved`, null, { params: { solved } });
    msg.feedbackDone = true;
    const tip = solved
      ? '太好了！已记入知识库（命中条目 helpful +1），感谢反馈。'
      : '很抱歉没帮上您，建议直接提交报修工单，师傅会尽快处理。';
    messages.value.push({ role: 'system', text: tip });
    scrollBottom();
  } catch (e) {
    ElMessage.error('反馈失败，请重试');
  }
}
</script>

<style scoped>
.diagnosis h2 {
  margin-bottom: 20px;
}
.chat-card {
  max-width: 700px;
}
.chat-area {
  min-height: 300px;
  max-height: 480px;
  overflow-y: auto;
  padding: 10px;
  background: #f9f9f9;
  border-radius: 4px;
  margin-bottom: 15px;
}
.message {
  padding: 10px 15px;
  border-radius: 8px;
  margin-bottom: 10px;
  max-width: 80%;
  word-break: break-word;
}
.message.user {
  background: #dcedc8;
  margin-left: auto;
}
.message.assistant {
  background: #ffffff;
  border: 1px solid #e0e0e0;
}
.message.system {
  background: #e3f2fd;
  color: #1976d2;
  max-width: 95%;
}
.typing {
  animation: blink 1.2s infinite;
}
@keyframes blink {
  50% { opacity: 0.55; }
}
.match-list {
  margin-top: 8px;
}
.match-card {
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: #fafafa;
}
.match-title {
  font-weight: bold;
  margin-bottom: 4px;
}
.sim-tag {
  margin-left: 6px;
}
.match-q {
  color: #555;
  font-size: 13px;
  margin-bottom: 4px;
}
.match-a {
  font-size: 13px;
  line-height: 1.6;
}
.match-foot {
  margin-top: 4px;
}
.helpful {
  color: #999;
  font-size: 12px;
}
.feedback-area {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}
.input-area {
  display: flex;
  gap: 10px;
}
</style>
