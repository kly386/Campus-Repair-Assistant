import http from './http';

/** 师傅端工作台 API（后端以登录态识别师傅本人） */
export function fetchMyOrders() {
  return http.get('/worker/my/orders').then((r) => r.data);
}

export function acceptOrder(id) {
  return http.post(`/worker/my/orders/${id}/accept`).then((r) => r.data);
}

export function departOrder(id) {
  return http.post(`/worker/my/orders/${id}/depart`).then((r) => r.data);
}

export function startRepair(id) {
  return http.post(`/worker/my/orders/${id}/start`).then((r) => r.data);
}

export function completeOrder(id, result) {
  return http.post(`/worker/my/orders/${id}/complete`, { result }).then((r) => r.data);
}

export function fetchMySchedule() {
  return http.get('/worker/my/schedule').then((r) => r.data);
}
