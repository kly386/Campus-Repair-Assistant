/**
 * 自助诊断冒烟测试：登录 → WS 问答 → 反馈 → SSE 降级 → 握手鉴权负例
 *
 * 用法（后端已启动时）：
 *   node scripts/smoke-diagnosis.mjs
 *   SMOKE_BASE_URL=http://localhost:8082 node scripts/smoke-diagnosis.mjs  # 换端口
 *
 * 不写入任何文件、不打印密钥。只使用演示用的微信扫码 mock 登录（无需图形验证码）。
 */

const BASE = (process.env.SMOKE_BASE_URL || 'http://localhost:8081').replace(/\/$/, '');
const FP = `smoke-fp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const SERVER = process.env.SMOKE_SERVER || 'default';

let passed = 0;
let failed = 0;
let lastSessionId = null;

function ok(name, detail = '') {
  passed += 1;
  console.log(`  PASS  ${name}${detail ? ' — ' + detail : ''}`);
}

function fail(name, detail = '') {
  failed += 1;
  console.error(`  FAIL  ${name}${detail ? ' — ' + detail : ''}`);
}

function assert(condition, name, detail = '') {
  if (condition) ok(name, detail);
  else fail(name, detail);
}

const COMMON_HEADERS = {
  'Content-Type': 'application/json',
  'X-Client-Fingerprint': FP,
  'User-Agent': 'campus-diagnosis-smoke/1.0'
};

async function api(path, options = {}) {
  const resp = await fetch(BASE + path, {
    ...options,
    headers: { ...COMMON_HEADERS, ...(options.headers || {}) }
  });
  const text = await resp.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`${path} 返回非 JSON: ${text.slice(0, 200)}`);
  }
  if (!resp.ok || json.code !== 200) {
    throw new Error(`${path} 失败: HTTP ${resp.status}, code=${json.code}, msg=${json.message}`);
  }
  return json.data;
}

function b64url(value) {
  return Buffer.from(value || '', 'utf8').toString('base64url');
}

/** 用微信 mock 登录拿 accessToken；指纹全程保持一致，否则 WS 握手会被拒 */
async function login() {
  const qr = await api('/api/auth/wx/qrcode', { method: 'POST' });
  const uuid = qr.uuid;
  if (!uuid) throw new Error('wx/qrcode 未返回 uuid');

  await api(`/api/auth/wx/mock/scan?uuid=${encodeURIComponent(uuid)}`, { method: 'POST' });
  await api(`/api/auth/wx/mock/confirm?uuid=${encodeURIComponent(uuid)}`, { method: 'POST' });
  const state = await api(`/api/auth/wx/state?uuid=${encodeURIComponent(uuid)}`);
  if (!state.accessToken) throw new Error('wx/state 未返回 accessToken');
  return state.accessToken;
}

function connectWs(token) {
  const protocol = `campus-diag.${b64url(token)}.${b64url(FP)}`;
  const url = BASE.replace(/^http/, 'ws') + '/ws/diagnosis';
  const socket = new WebSocket(url, [protocol]);
  return { socket, protocol };
}

function onceWs(socket, event, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`等待 WS ${event} 超时`)), timeoutMs);
    const handler = (ev) => {
      clearTimeout(timer);
      socket.removeEventListener(event, handler);
      resolve(ev);
    };
    socket.addEventListener(event, handler);
  });
}

/** 打开 WS、发 ask、收集事件直到 done */
async function wsAsk(socket, question, conversationId) {
  const events = [];
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WS 问答超时（未收到 done）')), 60000);
    const handler = (ev) => {
      let envelope;
      try {
        envelope = JSON.parse(ev.data);
      } catch {
        return;
      }
      events.push(envelope);
      if (envelope.type === 'meta') lastSessionId = envelope.data?.sessionId ?? null;
      if (envelope.type === 'error') {
        clearTimeout(timer);
        socket.removeEventListener('message', handler);
        reject(new Error(envelope.data?.message || '服务端返回 error'));
      } else if (envelope.type === 'done') {
        clearTimeout(timer);
        socket.removeEventListener('message', handler);
        resolve(events);
      }
    };
    socket.addEventListener('message', handler);
    socket.send(JSON.stringify({ type: 'ask', question, conversationId }));
  });
}

/** SSE 降级：解析 event:/data: 块，返回事件数组 */
async function sseAsk(token, question, conversationId) {
  const resp = await fetch(BASE + '/api/knowledge/session/stream', {
    method: 'POST',
    headers: {
      ...COMMON_HEADERS,
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ question, conversationId })
  });
  if (!resp.ok || !resp.body) throw new Error(`SSE 请求失败: HTTP ${resp.status}`);

  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  const events = [];
  let buffer = '';
  let finished = false;

  const consume = (block) => {
    let event = 'message';
    const lines = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) lines.push(line.slice(5).trim());
    }
    if (!lines.length) return;
    let data;
    try {
      data = JSON.parse(lines.join('\n'));
    } catch {
      return;
    }
    events.push({ type: event, data });
    if (event === 'done') finished = true;
    if (event === 'error') throw new Error(data.message || 'SSE error');
  };

  while (true) {
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
  if (!finished) throw new Error('SSE 未收到 done');
  return events;
}

async function main() {
  console.log(`== 自助诊断冒烟测试 @ ${BASE} ==`);

  // 1) 登录
  let token;
  try {
    token = await login();
    ok('演示账号 mock 登录', 'accessToken 已获取');
  } catch (e) {
    fail('演示账号 mock 登录', e.message);
    return;
  }

  // 2) WS 握手 + 子协议回显
  const { socket, protocol } = connectWs(token);
  try {
    await onceWs(socket, 'open');
    assert(socket.protocol === protocol, 'WS 子协议回显', socket.protocol === protocol ? '一致' : socket.protocol);
    ok('WS 握手成功');
  } catch (e) {
    fail('WS 握手成功', e.message);
    try { socket.close(); } catch {}
  }

  // 3) 口语化描述必须按维修问题受理，不能甩闲聊拒答模板
  try {
    const events = await wsAsk(socket, '洗澡水一会烫一会凉，忽上忽下的', `natural-${Date.now()}`);
    const meta = events.find((e) => e.type === 'meta');
    const done = events.find((e) => e.type === 'done');
    const answer = done?.data?.answer || '';
    assert(meta?.data?.offTopic === false, '口语描述不再误判为闲聊',
      `offTopic=${meta?.data?.offTopic}`);
    assert(!answer.includes('只负责解答宿舍设施维修问题'), '回复不是闲聊拒答', answer.slice(0, 60));
    assert(!answer.includes('暂时没有找到与您问题相关的知识条目'), '口语描述能命中知识条目',
      answer.slice(0, 60));
    assert(!answer.includes('不加热') && !answer.includes('加热管'), '没有错配到"不加热"条目',
      answer.slice(0, 60));
  } catch (e) {
    fail('口语描述受理', e.message);
  }

  // 3b) 短句也要能反查到种子关键词，不能只靠整句 LIKE
  try {
    const events = await wsAsk(socket, '宿舍跳闸了怎么办', `short-${Date.now()}`);
    const done = events.find((e) => e.type === 'done');
    const answer = done?.data?.answer || '';
    assert(!answer.includes('暂时没有找到与您问题相关的知识条目'), '短句能命中知识条目',
      answer.slice(0, 60));
  } catch (e) {
    fail('短句关键词反查', e.message);
  }

  // 3c) 词库覆盖：准收尾语/口语维修词/试过无效说法，都不许甩闲聊拒答
  // 每条独立 conversationId，避免互相污染上下文判定；
  // "试过无效"类需要先有历史，所以带 seed 先铺一轮维修问答。
  const vocabCases = [
    { q: '好的我试试', seed: '洗衣机不转了怎么办', check: (m, a) => m?.data?.closed === true && !a.includes('只负责解答') && !a.includes('上面'), name: '准收尾"好的我试试"不甩闲聊' },
    { q: '我试试看', seed: '洗衣机不转了怎么办', check: (m, a) => m?.data?.closed === true && !a.includes('只负责解答'), name: '准收尾"我试试看"不甩闲聊' },
    { q: '寝室灯不亮了', check: (m, a) => m?.data?.offTopic === false && !a.includes('只负责解答'), name: '口语维修词"寝室/灯"受理' },
    { q: '地漏反味怎么办', check: (m, a) => m?.data?.offTopic === false && !a.includes('只负责解答'), name: '口语维修词"地漏/反味"受理' },
    { q: '弄了还是不行', seed: '洗衣机不转了怎么办', check: (m, a) => m?.data?.triedBefore === true && a.includes('不再重复'), name: '试过无效说法"弄了还是不行"' },
    { q: '搞了没用', seed: '门锁打不开怎么办', check: (m, a) => m?.data?.triedBefore === true && a.includes('报修'), name: '试过无效说法"搞了没用"' }
  ];
  for (const c of vocabCases) {
    try {
      const cid = `vocab-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      if (c.seed) await wsAsk(socket, c.seed, cid);
      const events = await wsAsk(socket, c.q, cid);
      const meta = events.find((e) => e.type === 'meta');
      const done = events.find((e) => e.type === 'done');
      assert(c.check(meta, done?.data?.answer || ''), c.name, (done?.data?.answer || '').slice(0, 50));
    } catch (e) {
      fail(c.name, e.message);
    }
  }

  // 4) WS 问答 + done 接力字段
  const conversationId = `smoke-${Date.now()}`;
  let firstAskHasSteps = false;
  try {
    // 用种子里的标准问法，向量服务不可用时 LIKE 也能命中，冒烟不依赖外部 AI 配置
    const events = await wsAsk(socket, '洗澡时热水器水温忽冷忽热怎么办？', conversationId);
    const meta = events.find((e) => e.type === 'meta');
    const deltas = events.filter((e) => e.type === 'delta');
    const done = events.find((e) => e.type === 'done');
    // AI 欠费时会降级成知识库原文，没有③；此时不该要求抽到步骤
    firstAskHasSteps = (done?.data?.answer || '').includes('③');
    assert(!!meta, 'WS meta 事件');
    assert(deltas.length > 0, 'WS delta 逐字事件', `${deltas.length} 个`);
    assert(!!done, 'WS done 事件');
    if (done) {
      const d = done.data;
      const fields = ['sessionId', 'diagnosisSessionId', 'conversationId', 'categoryId',
        'emergencyLevel', 'matchedKbId', 'attemptedSteps'];
      const missing = fields.filter((f) => !(f in d));
      assert(missing.length === 0, 'done 含报修接力字段',
        missing.length ? `缺 ${missing.join(',')}` : fields.join(','));
      assert(typeof d.answer === 'string' && d.answer.length > 0, 'done 含完整答案', `${d.answer?.length} 字`);
      assert(d.conversationId === conversationId, 'conversationId 透传');
    }
  } catch (e) {
    fail('WS 问答', e.message);
  }

  // 5) 自然语言反馈"试过无效"：不点按钮也要认，不能回"没有上下文"
  try {
    const events = await wsAsk(socket, '试过了还是不行，还是一会儿烫一会凉', conversationId);
    const meta = events.find((e) => e.type === 'meta');
    const done = events.find((e) => e.type === 'done');
    assert(meta?.data?.triedBefore === true, '自然语言反馈被识别为 triedBefore',
      `triedBefore=${meta?.data?.triedBefore}`);
    assert(meta?.data?.needsContext === false, '不再误报"没有上下文"',
      `needsContext=${meta?.data?.needsContext}`);
    assert(meta?.data?.lowConfidence === false, '试过无效不误报低置信度',
      `lowConfidence=${meta?.data?.lowConfidence}`);
    const answer = done?.data?.answer || '';
    assert(answer.includes('不再重复') && answer.includes('报修'), '回复承认试过并指向报修',
      answer.slice(0, 80));
    assert(!answer.includes('没有保留对应的上下文'), '回复不含误导文案');
  } catch (e) {
    fail('自然语言反馈"试过无效"', e.message);
  }

  // 6) 上一轮已指向报修后的追问：不能当闲聊，也不能说没上下文
  try {
    const events = await wsAsk(socket, '好吧还有其他方法吗？', conversationId);
    const meta = events.find((e) => e.type === 'meta');
    const done = events.find((e) => e.type === 'done');
    const answer = done?.data?.answer || '';
    assert(meta?.data?.contextFollowUp === true, '同对话追问被识别',
      `contextFollowUp=${meta?.data?.contextFollowUp}`);
    assert(meta?.data?.offTopic === false, '追问不再误判为闲聊',
      `offTopic=${meta?.data?.offTopic}`);
    assert(answer.includes('报修'), '追问仍指向报修', answer.slice(0, 80));
    assert(!answer.includes('只负责解答宿舍设施维修问题'), '回复不是闲聊拒答');
    assert(!answer.includes('没有保留对应的上下文'), '回复不是"没上下文"套话');
    const steps = done?.data?.attemptedSteps || [];
    const noCriteria = steps.every((s) => !s.includes('成功判据') && !s.includes('怎么算有效'));
    // 只有上一轮真出了③五段回答才要求抽到步骤；降级原文/无种子时只校验不混判据
    if (firstAskHasSteps) {
      assert(steps.length > 0 && noCriteria,
        '试过的步骤已剔除判据原文', JSON.stringify(steps).slice(0, 120));
    } else {
      assert(noCriteria, '试过的步骤未混入判据（无③五段回答）', JSON.stringify(steps).slice(0, 120));
    }
  } catch (e) {
    fail('报修后的追问', e.message);
  }

  // 7) 同对话收尾语：不能再被当闲聊拒答，也不显示反馈/追问
  try {
    const events = await wsAsk(socket, '好吧', conversationId);
    const meta = events.find((e) => e.type === 'meta');
    const done = events.find((e) => e.type === 'done');
    const answer = done?.data?.answer || '';
    assert(meta?.data?.closed === true, '收尾语被识别为 closed',
      `closed=${meta?.data?.closed}`);
    assert(meta?.data?.offTopic === false, '收尾语不再误判为闲聊',
      `offTopic=${meta?.data?.offTopic}`);
    assert(answer.includes('有问题随时再来'), '收尾语短回', answer.slice(0, 80));
    assert(!answer.includes('只负责解答宿舍设施维修问题'), '回复不是闲聊拒答');
    assert((done?.data?.suggestions || []).length === 0, '收尾语不再推追问建议');
  } catch (e) {
    fail('同对话收尾语', e.message);
  }

  // 8) feedback + ack
  try {
    const ack = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('feedback_ack 超时')), 8000);
      const handler = (ev) => {
        let env;
        try { env = JSON.parse(ev.data); } catch { return; }
        if (env.type === 'feedback_ack') {
          clearTimeout(timer);
          socket.removeEventListener('message', handler);
          resolve(env.data);
        }
      };
      socket.addEventListener('message', handler);
    });
    if (!lastSessionId) throw new Error('上一轮 meta 未返回 sessionId');
    socket.send(JSON.stringify({
      type: 'feedback',
      sessionId: lastSessionId,
      solved: false
    }));
    const data = await ack;
    assert(data.ok === true, 'WS feedback_ack 通路', `sessionId=${lastSessionId}`);
  } catch (e) {
    fail('WS feedback_ack 通路', e.message);
  }

  try { socket.close(); } catch {}

  // 9) SSE 降级
  try {
    const events = await sseAsk(token, '宿舍跳闸了怎么办', `sse-${Date.now()}`);
    const meta = events.find((e) => e.type === 'meta');
    const done = events.find((e) => e.type === 'done');
    assert(!!meta, 'SSE meta 事件');
    assert(events.filter((e) => e.type === 'delta').length > 0, 'SSE delta 事件');
    assert(!!done && !!done.data.answer, 'SSE done 事件');
    if (done) {
      assert('emergencyLevel' in done.data, 'SSE done 同样带接力字段');
    }
  } catch (e) {
    fail('SSE 降级', e.message);
  }

  // 10) 负例：无子协议握手必须被拒
  try {
    const bad = new WebSocket(BASE.replace(/^http/, 'ws') + '/ws/diagnosis');
    const result = await new Promise((resolve) => {
      const timer = setTimeout(() => resolve('timeout'), 5000);
      bad.addEventListener('open', () => { clearTimeout(timer); resolve('opened'); });
      bad.addEventListener('error', () => { clearTimeout(timer); resolve('rejected'); });
      bad.addEventListener('close', () => { clearTimeout(timer); resolve('closed'); });
    });
    assert(result === 'rejected' || result === 'closed', '无子协议握手被拒绝', result);
    try { bad.close(); } catch {}
  } catch (e) {
    fail('无子协议握手被拒绝', e.message);
  }

  console.log(`\n== 结果: ${passed} PASS / ${failed} FAIL ==`);
  if (failed > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
