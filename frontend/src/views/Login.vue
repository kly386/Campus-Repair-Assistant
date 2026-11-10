<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">校园快修服务助手</h2>
      <el-tabs v-model="tab">
        <!-- 1. 账密 + 图形验证码 -->
        <el-tab-pane label="账号密码" name="pwd">
          <el-form @submit.prevent="doPwdLogin">
            <el-form-item>
              <el-input v-model="pwdForm.username" placeholder="用户名" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="pwdForm.password" type="password" placeholder="密码" show-password />
            </el-form-item>
            <el-form-item>
              <div class="captcha-row">
                <el-input v-model="pwdForm.captchaCode" placeholder="验证码" maxlength="4" />
                <img v-if="captcha.image" :src="captcha.image" class="captcha-img"
                     title="点击刷新" @click="refreshCaptcha" />
              </div>
            </el-form-item>
            <el-button type="primary" :loading="loading" :disabled="loading" style="width:100%"
                       native-type="submit">登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <!-- 2. 手机短信验证码 -->
        <el-tab-pane label="手机验证码" name="sms">
          <el-form @submit.prevent="doSmsLogin">
            <el-form-item>
              <el-input v-model="smsForm.phone" placeholder="手机号" maxlength="11" />
            </el-form-item>
            <el-form-item>
              <div class="captcha-row">
                <el-input v-model="smsForm.code" placeholder="短信验证码" maxlength="6" />
                <el-button :disabled="smsCountdown > 0" @click="doSendSms">
                  {{ smsCountdown > 0 ? `${smsCountdown}s后重发` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-alert v-if="mockCode" :title="`【模拟短信】验证码：${mockCode}`" type="info"
                      :closable="false" style="margin-bottom:12px" />
            <el-button type="primary" :loading="loading" style="width:100%"
                       native-type="submit">登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <!-- 3. 微信扫码（模拟） -->
        <el-tab-pane label="微信扫码" name="wx">
          <div class="wx-box">
            <template v-if="!wx.uuid">
              <el-button type="success" :loading="wxLoading" @click="startWxLogin">获取二维码</el-button>
            </template>
            <template v-else>
              <div class="wx-qr">{{ wx.qrText }}</div>
              <div class="wx-tip">{{ wxStatusText }}</div>
              <div v-if="wx.status === 'SCANNED'" class="wx-actions">
                <el-button size="small" type="success" @click="mockConfirm">模拟手机端确认登录</el-button>
              </div>
              <div v-if="wx.status === 'WAITING'" class="wx-actions">
                <el-button size="small" plain @click="mockScan">模拟手机端扫码</el-button>
              </div>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false"
                style="margin-top:12px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getCaptcha, loginByPassword, sendSmsCode, loginBySms,
  wxQrcode, wxState, wxMockScan, wxMockConfirm } from '../api/auth';
import { formatApiError } from '../api/http';

const router = useRouter();
const tab = ref('pwd');
const loading = ref(false);
const errorMsg = ref('');

/** 登录成功统一跳转 */
function onLoginSuccess(data) {
  localStorage.setItem('token', data.token);
  localStorage.setItem('user', JSON.stringify({
    username: data.username, realName: data.realName, role: data.role
  }));
  ElMessage.success(`欢迎，${data.realName}`);
  router.push('/repair');
}

// ---------- 1. 账密 ----------
const pwdForm = reactive({ username: '', password: '', captchaId: '', captchaCode: '' });
const captcha = reactive({ image: '' });

async function refreshCaptcha() {
  const c = await getCaptcha();
  captcha.image = c.image;
  pwdForm.captchaId = c.captchaId;
  pwdForm.captchaCode = '';
}
refreshCaptcha();

async function doPwdLogin() {
  errorMsg.value = '';
  loading.value = true;
  try {
    const resp = await loginByPassword(pwdForm);
    if (resp.code === 200) onLoginSuccess(resp.data);
    else { errorMsg.value = resp.message; refreshCaptcha(); }
  } catch (e) {
    setError('登录失败', formatApiError(e));
    refreshCaptcha();
  } finally {
    loading.value = false;
  }
}

// ---------- 2. 短信验证码 ----------
const smsForm = reactive({ phone: '', code: '' });
const mockCode = ref('');
const smsCountdown = ref(0);
let smsTimer = null;

async function doSendSms() {
  errorMsg.value = '';
  if (!/^1\d{10}$/.test(smsForm.phone)) {
    ElMessage.warning('请输入有效的 11 位手机号');
    return;
  }
  try {
    const resp = await sendSmsCode(smsForm.phone);
    if (resp.code === 200) {
      mockCode.value = resp.data?.mockCode || '';
      ElMessage.success('验证码已发送');
      smsCountdown.value = 60;
      smsTimer = setInterval(() => {
        if (--smsCountdown.value <= 0) clearInterval(smsTimer);
      }, 1000);
    } else {
      errorMsg.value = resp.message;
    }
  } catch (e) {
    console.error('发送验证码失败', e);
    setError('发送失败', formatApiError(e));
  }
}

async function doSmsLogin() {
  errorMsg.value = '';
  loading.value = true;
  try {
    const resp = await loginBySms(smsForm.phone, smsForm.code);
    if (resp.code === 200) onLoginSuccess(resp.data);
    else errorMsg.value = resp.message;
  } catch (e) {
    setError('登录失败', formatApiError(e));
  } finally {
    loading.value = false;
  }
}

// ---------- 3. 微信扫码（模拟） ----------
const wx = reactive({ uuid: '', qrText: '', status: '' });
const wxLoading = ref(false);
let pollTimer = null;

const wxStatusText = computed(() => ({
  WAITING: '等待扫码…', SCANNED: '已扫码，请在手机端确认',
  CONFIRMED: '登录成功', EXPIRED: '二维码已过期，请重新获取'
}[wx.status] || ''));

async function startWxLogin() {
  wxLoading.value = true;
  try {
    const data = await wxQrcode();
    wx.uuid = data.uuid;
    wx.qrText = data.qrcodeContent;
    wx.status = 'WAITING';
    // 3s 轮询扫码状态，CONFIRMED/EXPIRED 时停止
    pollTimer = setInterval(async () => {
      try {
        const s = await wxState(wx.uuid);
        wx.status = s.status;
        if (s.status === 'CONFIRMED' && s.token) {
          clearInterval(pollTimer);
          onLoginSuccess(s);
        } else if (s.status === 'EXPIRED') {
          clearInterval(pollTimer);
          wx.uuid = ''; // 引导重新获取二维码
        }
      } catch (e) {
        console.error('轮询扫码状态失败', e);
        clearInterval(pollTimer);
      }
    }, 3000);
  } catch (e) {
    console.error('获取二维码失败', e);
    setError('获取二维码失败', formatApiError(e));
  } finally {
    wxLoading.value = false; // 无论成败都复位，避免按钮永远转圈
  }
}

/** 统一拼接错误展示："标题（原因）" */
function setError(title, detail) {
  errorMsg.value = `${title}（${detail}）`;
}

async function mockScan() {
  await wxMockScan(wx.uuid);
}
async function mockConfirm() {
  await wxMockConfirm(wx.uuid);
}

onUnmounted(() => {
  clearInterval(smsTimer);
  clearInterval(pollTimer);
});
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f0fe, #f5f7fa);
}
.login-card {
  width: 380px;
}
.title {
  text-align: center;
  margin: 0 0 16px;
  color: #303133;
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-img {
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
}
.wx-box {
  text-align: center;
  padding: 12px 0;
}
.wx-qr {
  font-family: monospace;
  font-size: 12px;
  background: #f5f7fa;
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  padding: 26px 14px;
  color: #606266;
}
.wx-tip { margin-top: 10px; color: #909399; font-size: 14px; }
.wx-actions { margin-top: 10px; }
</style>
