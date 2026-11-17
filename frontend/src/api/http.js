import axios from 'axios';
import {
  clearSession,
  getAccessToken,
  getClientFingerprint,
  getRefreshToken,
  setSession
} from '../auth/session';

const http = axios.create({ baseURL: '/api', timeout: 20000 });
const refreshClient = axios.create({ baseURL: '/api', timeout: 20000 });

const LOGIN_PATHS = ['/auth/login', '/auth/sms/login', '/auth/email/login'];

let router;
let routeRebuilder;
let refreshPromise = null;
let redirecting = false;

export function bindRouter(value) {
  router = value;
}

/** 刷新 Token 后由 main/router 注入动态路由重建逻辑。 */
export function bindRouteRebuilder(rebuilder) {
  routeRebuilder = rebuilder;
}

function isLoginRequest(url = '') {
  return LOGIN_PATHS.some((path) => url.includes(path));
}

function isRefreshRequest(url = '') {
  return url.includes('/auth/refresh');
}

function withCommonHeaders(config) {
  config.headers = config.headers || {};
  const token = getAccessToken();
  if (token) {config.headers.Authorization = `Bearer ${token}`;}
  config.headers['X-Client-Fingerprint'] = getClientFingerprint();
  return config;
}

http.interceptors.request.use(withCommonHeaders);

async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {throw new Error('缺少刷新凭证');}
  const response = await refreshClient.post('/auth/refresh', { refreshToken }, {
    headers: { 'X-Client-Fingerprint': getClientFingerprint() }
  });
  if (response.data?.code !== 200 || !response.data?.data?.accessToken) {
    throw new Error(response.data?.message || '刷新登录状态失败');
  }
  setSession(response.data.data);
  if (routeRebuilder) {await routeRebuilder();}
  return response.data.data.accessToken;
}

function queueRefresh() {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function redirectToLogin() {
  if (redirecting) {return;}
  redirecting = true;
  clearSession();
  const current = router?.currentRoute?.value?.fullPath || '/';
  const target = current && current !== '/login'
    ? { path: '/login', query: { redirect: current } }
    : { path: '/login' };
  router?.replace(target).finally(() => {
    redirecting = false;
  });
}

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config || {};
    const status = error.response?.status;
    const canRefresh = status === 401
      && !original._retry
      && !isLoginRequest(original.url)
      && !isRefreshRequest(original.url)
      && getRefreshToken();

    if (canRefresh) {
      original._retry = true;
      try {
        const accessToken = await queueRefresh();
        original.headers = original.headers || {};
        original.headers.Authorization = `Bearer ${accessToken}`;
        original.headers['X-Client-Fingerprint'] = getClientFingerprint();
        return http(original);
      } catch (refreshError) {
        redirectToLogin();
        return Promise.reject(refreshError);
      }
    }

    if (status === 401 && (isRefreshRequest(original.url) || !isLoginRequest(original.url))) {
      redirectToLogin();
    }
    return Promise.reject(error);
  }
);

/** 提取后端统一业务错误信息，页面优先展示 message。 */
export function getApiErrorInfo(err) {
  const payload = err?.response?.data;
  const data = payload?.data || {};
  let message = payload?.message;
  if (!message && typeof payload === 'string' && payload.length < 300) {message = payload;}
  if (!message && err?.response) {message = `请求失败（HTTP ${err.response.status}）`;}
  if (!message) {message = '无法连接后端服务，请检查服务是否已启动';}
  return {
    message,
    errorCode: data.errorCode || '',
    channel: data.channel || '',
    retryable: Boolean(data.retryable),
    retryAfterSeconds: Number(data.retryAfterSeconds || 0),
    lockRemainingMinutes: Number(data.lockRemainingMinutes || 0)
  };
}

export function formatApiError(err) {
  return getApiErrorInfo(err).message;
}

export default http;
