<template>
  <div class="login-wrap">
    <div class="decor decor-one"></div>
    <div class="decor decor-two"></div>

    <el-card class="login-card" shadow="never">
      <div class="brand">
        <div class="brand-icon"><el-icon><Tools /></el-icon></div>
        <div>
          <h1>校园快修服务助手</h1>
          <p>统一身份认证 · 安全登录</p>
        </div>
      </div>

      <LockCountdown
        v-if="locked"
        :unlock-at="lockUntil"
        message="账号已锁定"
        @expired="clearLock"
      />

      <el-tabs v-model="tab" class="login-tabs" @tab-change="onTabChange">
        <el-tab-pane name="pwd">
          <template #label>
            <span class="tab-label"><el-icon><Lock /></el-icon>账号密码</span>
          </template>
          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-position="top"
            :disabled="locked"
            @submit.prevent="doPasswordLogin"
          >
            <el-form-item prop="username">
              <el-input v-model.trim="pwdForm.username" size="large" placeholder="请输入用户名" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="pwdForm.password"
                size="large"
                type="password"
                placeholder="请输入密码"
                show-password
                @keyup.enter="doPasswordLogin"
              >
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input
                  v-model.trim="pwdForm.captchaCode"
                  size="large"
                  maxlength="4"
                  placeholder="图形验证码"
                  @keyup.enter="doPasswordLogin"
                />
                <button class="captcha-button" type="button" title="点击刷新验证码" @click="refreshCaptcha">
                  <img v-if="captcha.image" :src="captcha.image" alt="图形验证码" />
                  <span v-else>加载中</span>
                </button>
              </div>
            </el-form-item>
            <el-button
              class="submit-button"
              type="primary"
              size="large"
              native-type="submit"
              :loading="submitting === 'pwd'"
              :disabled="locked"
            >
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane name="sms">
          <template #label>
            <span class="tab-label"><el-icon><Iphone /></el-icon>手机验证码</span>
          </template>
          <el-form label-position="top" :disabled="locked" @submit.prevent="doSmsLogin">
            <el-form-item>
              <el-input v-model.trim="smsForm.phone" size="large" maxlength="11" placeholder="请输入手机号" clearable>
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
              <button v-if="showDemoAccounts" type="button" class="demo-inline" @click="fillSmsDemo">
                <span>演示账号</span>
                <code>学生 {{ SMS_DEMO_PHONE }}</code>
                <span class="demo-inline-tip">点击填充手机号</span>
              </button>
            </el-form-item>
            <el-form-item>
              <div class="code-row">
                <el-input
                  v-model.trim="smsForm.code"
                  size="large"
                  maxlength="6"
                  placeholder="6 位短信验证码"
                  @keyup.enter="doSmsLogin"
                />
                <el-button
                  size="large"
                  :loading="smsSending"
                  :disabled="locked || smsCountdown > 0"
                  @click="doSendSms"
                >
                  {{ smsCountdown > 0 ? `${smsCountdown}s 后重发` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-alert
              v-if="mockSmsCode"
              class="mock-alert"
              :title="`本地模拟短信验证码：${mockSmsCode}`"
              type="info"
              :closable="false"
            />
            <el-button
              class="submit-button"
              type="primary"
              size="large"
              native-type="submit"
              :loading="submitting === 'sms'"
              :disabled="locked"
            >
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane name="email">
          <template #label>
            <span class="tab-label"><el-icon><Message /></el-icon>邮箱验证码</span>
          </template>
          <el-form label-position="top" :disabled="locked" @submit.prevent="doEmailLogin">
            <el-form-item>
              <el-input v-model.trim="emailForm.email" size="large" placeholder="请输入邮箱地址" clearable>
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
              <button v-if="showDemoAccounts" type="button" class="demo-inline" @click="fillEmailDemo">
                <span>演示账号</span>
                <code>学生 {{ EMAIL_DEMO_EMAIL }}</code>
                <span class="demo-inline-tip">点击填充邮箱地址</span>
              </button>
            </el-form-item>
            <el-form-item>
              <div class="code-row">
                <el-input
                  v-model.trim="emailForm.code"
                  size="large"
                  maxlength="6"
                  placeholder="6 位邮箱验证码"
                  @keyup.enter="doEmailLogin"
                />
                <el-button
                  size="large"
                  :loading="emailSending"
                  :disabled="locked || emailCountdown > 0"
                  @click="doSendEmail"
                >
                  {{ emailCountdown > 0 ? `${emailCountdown}s 后重发` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-alert
              v-if="mockEmailCode"
              class="mock-alert"
              :title="`本地模拟邮箱验证码：${mockEmailCode}`"
              type="info"
              :closable="false"
            />
            <el-button
              class="submit-button"
              type="primary"
              size="large"
              native-type="submit"
              :loading="submitting === 'email'"
              :disabled="locked"
            >
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane name="wx">
          <template #label>
            <span class="tab-label"><el-icon><ChatDotRound /></el-icon>微信扫码</span>
          </template>
          <div class="wx-panel">
            <div class="qr-frame">
              <el-skeleton v-if="wx.loading" animated>
                <template #template><el-skeleton-item variant="image" style="width: 220px; height: 220px" /></template>
              </el-skeleton>
              <img v-else-if="wx.qrcode" :src="wx.qrcode" alt="微信登录二维码" />
              <div v-else class="qr-empty">
                <el-icon><Refresh /></el-icon>
                <span>{{ wx.error || '点击下方按钮获取二维码' }}</span>
              </div>
              <div v-if="wx.status === 'EXPIRED'" class="qr-mask">二维码已过期，正在刷新…</div>
            </div>
            <p class="wx-status">{{ wxStatusText }}</p>
            <div class="wx-actions">
              <el-button :disabled="locked" :loading="wx.loading" @click="startWxLogin">
                刷新二维码
              </el-button>
              <template v-if="wx.mock">
                <el-button v-if="wx.status === 'WAITING'" plain @click="mockScan">模拟扫码</el-button>
                <el-button v-if="wx.status === 'SCANNED'" type="success" @click="mockConfirm">模拟确认登录</el-button>
              </template>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-alert
        v-if="errorMsg"
        class="error-alert"
        :title="errorMsg"
        type="error"
        :closable="false"
        show-icon
      />

      <div v-if="showDemoAccounts && tab === 'pwd'" class="demo-panel">
        <div class="demo-title">
          <span>演示账号</span>
          <small>点击自动填充账号密码</small>
        </div>
        <div class="demo-list">
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            class="demo-account"
            type="button"
            @click="fillDemoAccount(account)"
          >
            <span class="demo-role">{{ account.label }}</span>
            <code>{{ account.username }} / {{ account.password }}</code>
          </button>
        </div>
      </div>

      <div class="login-footer">登录即表示同意校园快修服务使用规范</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  ChatDotRound,
  Iphone,
  Key,
  Lock,
  Message,
  Refresh,
  Tools,
  User
} from '@element-plus/icons-vue';
import QRCode from 'qrcode';
import LockCountdown from '../components/LockCountdown.vue';
import { formatApiError, getApiErrorInfo } from '../api/http';
import {
  getCaptcha,
  loginByEmail,
  loginByPassword,
  loginBySms,
  sendEmailCode,
  sendSmsCode,
  wxMockConfirm,
  wxMockScan,
  wxQrcode,
  wxState
} from '../api/auth';
import { firstAccessiblePath, setSession } from '../auth/session';
import { rebuildRoutes } from '../router';

const LOCK_UNTIL_KEY = 'authLockUntil';
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const showDemoAccounts = import.meta.env.VITE_SHOW_DEMO_ACCOUNTS !== 'false';
// 手机/邮箱验证码登录的演示账号（DB 中对应的学生用户已存在）
const SMS_DEMO_PHONE = '15156987480';
const EMAIL_DEMO_EMAIL = '1148625258@qq.com';
const demoAccounts = [
  { label: '学生', username: 'student', password: 'student123' },
  { label: '维修师傅', username: 'worker', password: 'worker123' },
  { label: '管理员', username: 'admin', password: 'admin123' }
];


const router = useRouter();
const route = useRoute();
const tab = ref('pwd');
const submitting = ref('');
const errorMsg = ref('');

const storedLockUntil = Number(localStorage.getItem(LOCK_UNTIL_KEY) || 0);
const lockUntil = ref(storedLockUntil > Date.now() ? storedLockUntil : 0);
const locked = ref(lockUntil.value > 0);
if (!locked.value) localStorage.removeItem(LOCK_UNTIL_KEY);

const pwdFormRef = ref(null);
const pwdForm = reactive({ username: '', password: '', captchaId: '', captchaCode: '' });
const pwdRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }]
};
const captcha = reactive({ image: '', loading: false });

const smsForm = reactive({ phone: '', code: '' });
const mockSmsCode = ref('');
const smsSending = ref(false);
const smsCountdown = ref(0);
let smsTimer = null;

const emailForm = reactive({ email: '', code: '' });
const mockEmailCode = ref('');
const emailSending = ref(false);
const emailCountdown = ref(0);
let emailTimer = null;

const wx = reactive({
  uuid: '',
  qrcode: '',
  content: '',
  status: '',
  mock: false,
  loading: false,
  error: '',
  expiresAt: 0
});
let wxPollTimer = null;
let wxExpiryTimer = null;
let wxRefreshTimer = null;

const wxStatusText = computed(() => {
  if (locked.value) return '账号锁定期间暂停扫码登录';
  return {
    WAITING: '请使用微信扫描二维码',
    SCANNED: '已扫码，请在手机端确认登录',
    CONFIRMED: '登录成功，正在进入系统…',
    EXPIRED: '二维码已过期，正在自动刷新',
    USED: '二维码已使用，请刷新'
  }[wx.status] || '扫码登录';
});

function applyLock(minutes) {
  const safeMinutes = Math.max(1, Number(minutes) || 0);
  lockUntil.value = Date.now() + safeMinutes * 60 * 1000;
  locked.value = true;
  localStorage.setItem(LOCK_UNTIL_KEY, String(lockUntil.value));
  stopWxPolling();
  wx.status = '';
  wx.qrcode = '';
  errorMsg.value = '';
}

function clearLock() {
  locked.value = false;
  lockUntil.value = 0;
  localStorage.removeItem(LOCK_UNTIL_KEY);
  if (errorMsg.value.includes('账号已锁定')) errorMsg.value = '';
}


function fillDemoAccount(account) {
  tab.value = 'pwd';
  pwdForm.username = account.username;
  pwdForm.password = account.password;
  pwdForm.captchaCode = '';
  errorMsg.value = '';
  ElMessage.info(`已填入${account.label}账号，请输入图形验证码后登录`);
}

function fillSmsDemo() {
  smsForm.phone = SMS_DEMO_PHONE;
  smsForm.code = '';
  mockSmsCode.value = '';
  errorMsg.value = '';
  ElMessage.info('已填入演示手机号，点击发送验证码后填写收到的验证码');
}

function fillEmailDemo() {
  emailForm.email = EMAIL_DEMO_EMAIL;
  emailForm.code = '';
  mockEmailCode.value = '';
  errorMsg.value = '';
  ElMessage.info('已填入演示邮箱，点击发送验证码后填写收到的验证码');
}
function lockMinutesFromError(error) {
  const data = error?.response?.data?.data;
  return Number(data?.lockRemainingMinutes || 0);
}

function handleLoginError(error, fallback = '操作失败') {
  const info = getApiErrorInfo(error);
  if (info.lockRemainingMinutes > 0) {
    applyLock(info.lockRemainingMinutes);
    return;
  }
  errorMsg.value = info.message || fallback;
}

async function finishLogin(data) {
  clearLock();
  setSession(data);
  await rebuildRoutes();
  stopWxPolling();
  ElMessage.success(`欢迎，${data.realName || data.username || '用户'}`);
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '';
  router.replace(redirect || firstAccessiblePath(data.menus) || '/403');
}

async function refreshCaptcha() {
  if (captcha.loading) return;
  captcha.loading = true;
  try {
    const result = await getCaptcha();
    captcha.image = result.image;
    pwdForm.captchaId = result.captchaId;
    pwdForm.captchaCode = '';
  } catch (error) {
    errorMsg.value = `验证码加载失败（${formatApiError(error)}）`;
  } finally {
    captcha.loading = false;
  }
}

async function doPasswordLogin() {
  if (locked.value || submitting.value) return;
  errorMsg.value = '';
  try {
    await pwdFormRef.value?.validate();
  } catch {
    return;
  }
  submitting.value = 'pwd';
  try {
    const response = await loginByPassword(pwdForm);
    await finishLogin(response.data);
  } catch (error) {
    handleLoginError(error, '登录失败');
    await refreshCaptcha();
  } finally {
    submitting.value = '';
  }
}

function startCountdown(type, seconds = 60) {
  const isSms = type === 'sms';
  const countdown = isSms ? smsCountdown : emailCountdown;
  countdown.value = Math.max(1, Number(seconds) || 60);
  const timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      clearInterval(timer);
      countdown.value = 0;
    }
  }, 1000);
  if (isSms) {
    clearInterval(smsTimer);
    smsTimer = timer;
  } else {
    clearInterval(emailTimer);
    emailTimer = timer;
  }
}

async function doSendSms() {
  if (locked.value || smsSending.value || smsCountdown.value > 0) return;
  errorMsg.value = '';
  if (!PHONE_PATTERN.test(smsForm.phone)) {
    ElMessage.warning('请输入有效的 11 位手机号');
    return;
  }
  smsSending.value = true;
  try {
    const response = await sendSmsCode(smsForm.phone);
    mockSmsCode.value = response.data?.mockCode || '';
    startCountdown('sms', 60);
    ElMessage.success('短信验证码已发送');
  } catch (error) {
    handleLoginError(error, '短信发送失败');
    const retryAfter = Number(error?.response?.data?.data?.retryAfterSeconds || 0);
    if (retryAfter > 0) startCountdown('sms', retryAfter);
  } finally {
    smsSending.value = false;
  }
}

async function doSmsLogin() {
  if (locked.value || submitting.value) return;
  errorMsg.value = '';
  if (!PHONE_PATTERN.test(smsForm.phone)) {
    ElMessage.warning('请输入有效的 11 位手机号');
    return;
  }
  if (!smsForm.code) {
    ElMessage.warning('请输入短信验证码');
    return;
  }
  submitting.value = 'sms';
  try {
    const response = await loginBySms(smsForm.phone, smsForm.code);
    await finishLogin(response.data);
  } catch (error) {
    handleLoginError(error, '登录失败');
  } finally {
    submitting.value = '';
  }
}

async function doSendEmail() {
  if (locked.value || emailSending.value || emailCountdown.value > 0) return;
  errorMsg.value = '';
  if (!EMAIL_PATTERN.test(emailForm.email)) {
    ElMessage.warning('请输入有效的邮箱地址');
    return;
  }
  emailSending.value = true;
  try {
    const response = await sendEmailCode(emailForm.email);
    mockEmailCode.value = response.data?.mockCode || '';
    startCountdown('email', 60);
    ElMessage.success('邮箱验证码已发送');
  } catch (error) {
    handleLoginError(error, '邮件发送失败');
    const retryAfter = Number(error?.response?.data?.data?.retryAfterSeconds || 0);
    if (retryAfter > 0) startCountdown('email', retryAfter);
  } finally {
    emailSending.value = false;
  }
}

async function doEmailLogin() {
  if (locked.value || submitting.value) return;
  errorMsg.value = '';
  if (!EMAIL_PATTERN.test(emailForm.email)) {
    ElMessage.warning('请输入有效的邮箱地址');
    return;
  }
  if (!emailForm.code) {
    ElMessage.warning('请输入邮箱验证码');
    return;
  }
  submitting.value = 'email';
  try {
    const response = await loginByEmail(emailForm.email, emailForm.code);
    await finishLogin(response.data);
  } catch (error) {
    handleLoginError(error, '登录失败');
  } finally {
    submitting.value = '';
  }
}

function stopWxPolling() {
  clearInterval(wxPollTimer);
  clearTimeout(wxExpiryTimer);
  clearTimeout(wxRefreshTimer);
  wxPollTimer = null;
  wxExpiryTimer = null;
  wxRefreshTimer = null;
}

function scheduleWxExpiry() {
  clearTimeout(wxExpiryTimer);
  const wait = Math.max(1000, wx.expiresAt - Date.now());
  wxExpiryTimer = setTimeout(handleWxExpired, wait);
}

async function startWxLogin() {
  if (locked.value) return;
  stopWxPolling();
  errorMsg.value = '';
  wx.loading = true;
  wx.error = '';
  wx.qrcode = '';
  wx.uuid = '';
  try {
    const data = await wxQrcode();
    wx.uuid = data.uuid;
    wx.mock = Boolean(data.mock);
    wx.content = data.url || data.qrcodeContent || '';
    wx.qrcode = await QRCode.toDataURL(wx.content, {
      width: 220,
      margin: 1,
      errorCorrectionLevel: 'M',
      color: { dark: '#26435d', light: '#ffffff' }
    });
    wx.status = 'WAITING';
    wx.expiresAt = Date.now() + Number(data.expiresIn || data.expiresInSeconds || 180) * 1000;
    scheduleWxExpiry();
    wxPollTimer = setInterval(pollWxStatus, 2000);
  } catch (error) {
    wx.error = '二维码获取失败，请稍后重试';
    handleLoginError(error, '二维码获取失败');
  } finally {
    wx.loading = false;
  }
}

async function pollWxStatus() {
  if (!wx.uuid || locked.value) return;
  if (Date.now() >= wx.expiresAt) {
    handleWxExpired();
    return;
  }
  try {
    const state = await wxState(wx.uuid);
    wx.status = state.status;
    if (state.status === 'CONFIRMED' && (state.accessToken || state.token)) {
      stopWxPolling();
      await finishLogin(state);
      return;
    }
    if (state.status === 'EXPIRED' || state.status === 'USED') handleWxExpired();
  } catch (error) {
    if (lockMinutesFromError(error) > 0) {
      handleLoginError(error, '扫码登录失败');
      stopWxPolling();
    }
  }
}

function handleWxExpired() {
  if (tab.value !== 'wx' || locked.value) return;
  stopWxPolling();
  wx.status = 'EXPIRED';
  wx.qrcode = '';
  wx.error = '二维码已过期，正在自动刷新';
  wxRefreshTimer = setTimeout(() => {
    if (tab.value === 'wx' && !locked.value) startWxLogin();
  }, 1200);
}

async function mockScan() {
  if (!wx.uuid || locked.value) return;
  try {
    await wxMockScan(wx.uuid);
    wx.status = 'SCANNED';
  } catch (error) {
    handleLoginError(error, '模拟扫码失败');
  }
}

async function mockConfirm() {
  if (!wx.uuid || locked.value) return;
  try {
    await wxMockConfirm(wx.uuid);
    ElMessage.success('已模拟确认，正在登录');
  } catch (error) {
    handleLoginError(error, '模拟确认失败');
  }
}

function onTabChange(name) {
  if (name === 'wx' && !locked.value) {
    startWxLogin();
  } else {
    stopWxPolling();
  }
}

watch(locked, (value) => {
  if (value) stopWxPolling();
});

onMounted(refreshCaptcha);
onUnmounted(() => {
  clearInterval(smsTimer);
  clearInterval(emailTimer);
  stopWxPolling();
});
</script>

<style scoped>
.login-wrap {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 16%, rgba(126, 190, 255, .34), transparent 28%),
    radial-gradient(circle at 84% 82%, rgba(116, 221, 210, .24), transparent 28%),
    linear-gradient(135deg, #eef7ff 0%, #f7fbff 48%, #edf8f8 100%);
}
.decor { position: absolute; border-radius: 50%; filter: blur(2px); pointer-events: none; }
.decor-one { width: 280px; height: 280px; left: -90px; bottom: -100px; background: rgba(83, 159, 226, .13); }
.decor-two { width: 220px; height: 220px; right: -70px; top: -70px; border: 28px solid rgba(91, 193, 184, .12); }
.login-card {
  position: relative;
  z-index: 1;
  width: 450px;
  max-width: 100%;
  border: 1px solid rgba(255, 255, 255, .86);
  border-radius: 22px;
  background: rgba(255, 255, 255, .93);
  box-shadow: 0 24px 60px rgba(58, 111, 158, .16);
  backdrop-filter: blur(12px);
}
.brand { display: flex; align-items: center; gap: 13px; margin-bottom: 18px; }
.brand-icon {
  width: 48px; height: 48px; border-radius: 14px; display: grid; place-items: center;
  color: #fff; font-size: 25px; background: linear-gradient(135deg, #4c9be8, #63c5c0);
  box-shadow: 0 8px 18px rgba(76, 155, 232, .28);
}
.brand h1 { margin: 0; color: #24445f; font-size: 22px; letter-spacing: .5px; }
.brand p { margin: 5px 0 0; color: #8a9caf; font-size: 13px; }
.login-tabs :deep(.el-tabs__item) { padding: 0 14px; color: #70859a; }
.login-tabs :deep(.el-tabs__item.is-active) { color: #3d8fda; font-weight: 600; }
.login-tabs :deep(.el-tabs__active-bar) { background: #4c9be8; height: 3px; border-radius: 3px; }
.login-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: #e8f0f7; }
.tab-label { display: inline-flex; align-items: center; gap: 5px; }
.login-tabs :deep(.el-form-item) { margin-bottom: 18px; }
.login-tabs :deep(.el-input__wrapper) { border-radius: 10px; box-shadow: 0 0 0 1px #dbe8f3 inset; }
.login-tabs :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #4c9be8 inset; }
.captcha-row, .code-row { display: flex; gap: 10px; width: 100%; }
.captcha-row .el-input, .code-row .el-input { flex: 1; }
.captcha-button {
  width: 124px; height: 40px; padding: 0; border: 1px solid #dbe8f3; border-radius: 10px;
  background: #f7fbff; color: #91a1b2; cursor: pointer; overflow: hidden;
}
.captcha-button img { display: block; width: 100%; height: 100%; object-fit: cover; }
.code-row .el-button { min-width: 120px; border-radius: 10px; }
.submit-button {
  width: 100%; height: 43px; border: none; border-radius: 11px; font-size: 15px; letter-spacing: 3px;
  background: linear-gradient(90deg, #4d9ee8, #55b9c3); box-shadow: 0 9px 20px rgba(74, 158, 220, .24);
}
.mock-alert { margin-bottom: 14px; border-radius: 9px; }
.error-alert { margin-top: 14px; border-radius: 9px; }
.login-footer { margin-top: 18px; text-align: center; color: #a0adba; font-size: 12px; }
.wx-panel { display: flex; flex-direction: column; align-items: center; padding: 4px 0 8px; }
.qr-frame {
  position: relative; width: 240px; height: 240px; padding: 10px; border-radius: 16px;
  border: 1px solid #dceaf5; background: #fff; display: grid; place-items: center;
  box-shadow: inset 0 0 0 8px #f8fbff;
}
.qr-frame img { width: 220px; height: 220px; display: block; }
.qr-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: #8ea2b4; font-size: 13px; }
.qr-empty .el-icon { font-size: 34px; color: #75aee0; }
.qr-mask {
  position: absolute; inset: 10px; display: grid; place-items: center; border-radius: 10px;
  background: rgba(247, 251, 255, .93); color: #5f7890; font-size: 14px; font-weight: 600;
}
.wx-status { margin: 15px 0 10px; color: #60798f; font-size: 14px; }
.wx-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
@media (max-width: 520px) {
  .login-wrap { padding: 14px; }
  .login-card { border-radius: 18px; }
  .login-tabs :deep(.el-tabs__item) { padding: 0 8px; font-size: 13px; }
  .brand h1 { font-size: 19px; }
  .code-row { flex-direction: column; }
  .code-row .el-button { width: 100%; }
}
/* 放大登录面板并保证四个 Tab 始终完整可见 */
.login-card {
  width: 570px;
}
.login-card :deep(.el-card__body) {
  padding: 30px 34px 26px;
}
.brand {
  margin-bottom: 22px;
}
.brand-icon {
  width: 54px;
  height: 54px;
  font-size: 28px;
}
.brand h1 {
  font-size: 26px;
}
.brand p {
  font-size: 14px;
}
.login-tabs :deep(.el-tabs__nav-wrap) {
  overflow: visible;
}
.login-tabs :deep(.el-tabs__nav-scroll) {
  overflow: visible;
}
.login-tabs :deep(.el-tabs__nav) {
  width: 100% !important;
  display: flex;
  transform: none !important;
}
.login-tabs :deep(.el-tabs__item) {
  flex: 1 1 25%;
  min-width: 0;
  height: 48px;
  padding: 0 6px;
  justify-content: center;
  font-size: 14px;
}
.login-tabs :deep(.el-tabs__nav-prev),
.login-tabs :deep(.el-tabs__nav-next) {
  display: none;
}
.tab-label {
  white-space: nowrap;
}
.login-tabs :deep(.el-input--large .el-input__wrapper) {
  min-height: 46px;
  font-size: 15px;
}
.login-tabs :deep(.el-button--large) {
  min-height: 42px;
  font-size: 14px;
}
.submit-button {
  height: 48px;
  font-size: 16px;
}
.qr-frame {
  width: 260px;
  height: 260px;
}
.qr-frame img {
  width: 240px;
  height: 240px;
}
.demo-panel {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #dcecf8;
  border-radius: 13px;
  background: linear-gradient(135deg, #f6fbff, #f0fafa);
}
.demo-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #365873;
  font-size: 13px;
  font-weight: 700;
}
.demo-title small {
  color: #8ca2b5;
  font-size: 11px;
  font-weight: 400;
}
.demo-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.demo-account {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 9px 10px;
  border: 1px solid #d8eaf7;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color .2s, box-shadow .2s, transform .2s;
}
.demo-account:hover {
  border-color: #6bb0e8;
  box-shadow: 0 5px 14px rgba(70, 143, 202, .14);
  transform: translateY(-1px);
}
.demo-role {
  color: #337fbe;
  font-size: 12px;
  font-weight: 700;
}
.demo-account code {
  color: #6b7f91;
  font-size: 10px;
  white-space: nowrap;
}
.demo-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px dashed #bcdcf1;
  border-radius: 8px;
  background: #f6fbff;
  color: #337fbe;
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color .2s, background .2s;
}
.demo-inline:hover {
  border-color: #6bb0e8;
  background: #eef8ff;
}
.demo-inline code {
  color: #365873;
  font-size: 12px;
  font-weight: 700;
}
.demo-inline-tip {
  margin-left: auto;
  color: #8ca2b5;
  font-size: 11px;
}
@media (max-width: 640px) {
  .login-card :deep(.el-card__body) {
    padding: 22px 18px 20px;
  }
  .login-tabs :deep(.el-tabs__item) {
    height: 44px;
    padding: 0 2px;
    font-size: 12px;
  }
  .tab-label {
    gap: 2px;
  }
  .demo-list {
    grid-template-columns: 1fr;
  }
}

</style>
