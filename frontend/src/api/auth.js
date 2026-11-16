import http from './http';

/**
 * 登录鉴权 API（图形验证码/账密、短信验证码、微信扫码）
 */
export function getCaptcha() {
  return http.get('/auth/captcha').then((r) => r.data.data);
}

export function loginByPassword(payload) {
  return http.post('/auth/login', payload).then((r) => r.data);
}

export function sendSmsCode(phone) {
  return http.post('/auth/sms/send', { phone }).then((r) => r.data);
}

export function loginBySms(phone, code) {
  return http.post('/auth/sms/login', { phone, code }).then((r) => r.data);
}

/** 生成微信扫码二维码（模拟） */
export function wxQrcode() {
  return http.post('/auth/wx/qrcode').then((r) => r.data.data);
}

/** 轮询扫码状态：status = WAITING / SCANNED / CONFIRMED / EXPIRED */
export function wxState(uuid) {
  return http.get('/auth/wx/state', { params: { uuid } }).then((r) => r.data.data);
}

/** 模拟手机端「扫码」（答辩演示用） */
export function wxMockScan(uuid) {
  return http.post('/auth/wx/mock/scan', null, { params: { uuid } }).then((r) => r.data);
}

/** 模拟手机端「确认登录」（答辩演示用） */
export function wxMockConfirm(uuid) {
  return http.post('/auth/wx/mock/confirm', null, { params: { uuid } }).then((r) => r.data);
}

/** 当前登录用户信息 */
export function me() {
  return http.get('/user/me').then((r) => r.data.data);
}
