import axios from 'axios';

/**
 * 统一 axios 实例：登录态管理（token 注入 / 401 处理）
 */
const http = axios.create({ baseURL: '/api' });

// 请求拦截：自动携带 token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：401 清会话并跳登录页
http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (router?.currentRoute?.value.path !== '/login') {
        router.push('/login');
      }
    }
    return Promise.reject(err);
  }
);

/**
 * 格式化接口错误为可读文案（供页面显示）
 * 包含：HTTP 状态码、后端返回的 message、请求地址，便于定位原因
 */
export function formatApiError(err) {
  const detail = [];
  if (err.response) {
    detail.push(`HTTP ${err.response.status}`);
    const msg = err.response.data?.message;
    if (msg) detail.push(msg);
    else if (typeof err.response.data === 'string' && err.response.data.length < 200) {
      detail.push(err.response.data);
    }
    if (err.config?.url) detail.push(`接口: ${err.config.url}`);
  } else if (err.code) {
    // 网络层错误：ECONNREFUSED / ERR_CONNECTION_REFUSED 等
    detail.push(err.code || err.message);
    detail.push('无法连接后端服务');
  } else if (err.message) {
    detail.push(err.message);
  }
  return detail.join(' ｜ ');
}

// 避免循环依赖：仅在使用时引入 router
let router;
export function bindRouter(r) {
  router = r;
}

export default http;
