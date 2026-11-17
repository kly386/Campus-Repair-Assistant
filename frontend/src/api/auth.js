import http from './http';

/** 图形验证码。 */
export function getCaptcha() {
  return http.get('/auth/captcha').then((response) => response.data.data);
}

export function loginByPassword(payload) {
  return http.post('/auth/login', payload).then((response) => response.data);
}

export function sendVerificationCode(type, target) {
  return http.post('/auth/code/send', { type, target }).then((response) => response.data);
}

export function sendSmsCode(phone) {
  return sendVerificationCode('PHONE', phone);
}

export function loginBySms(phone, code) {
  return http.post('/auth/sms/login', { phone, code }).then((response) => response.data);
}

export function sendEmailCode(email) {
  return sendVerificationCode('EMAIL', email);
}

export function loginByEmail(email, code) {
  return http.post('/auth/email/login', { email, code }).then((response) => response.data);
}

/** 生成微信扫码会话。 */
export function wxQrcode() {
  return http.post('/auth/wx/qrcode').then((response) => response.data.data);
}

/** 轮询扫码状态：WAITING / SCANNED / CONFIRMED / EXPIRED / USED。 */
export function wxState(uuid) {
  return http.get('/auth/wx/state', { params: { uuid } }).then((response) => response.data.data);
}

/** 本地演示：模拟手机端扫码。 */
export function wxMockScan(uuid) {
  return http.post('/auth/wx/mock/scan', null, { params: { uuid } }).then((response) => response.data);
}

/** 本地演示：模拟手机端确认。 */
export function wxMockConfirm(uuid) {
  return http.post('/auth/wx/mock/confirm', null, { params: { uuid } }).then((response) => response.data);
}

/** 当前用户资料、角色、权限和菜单。 */
export function me() {
  return http.get('/user/me').then((response) => response.data.data);
}

export function logout() {
  return http.post('/auth/logout').then((response) => response.data);
}

export function getMyPermissions() {
  return http.get('/user/permissions').then((response) => response.data.data);
}

export function getMyMenus() {
  return http.get('/user/menus').then((response) => response.data.data);
}
