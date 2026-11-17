import http from './http';

/**
 * 调度 Agent API 封装
 * 后端接口直接返回原始 JSON（不经 ApiResponse 包裹），失败时为 {code,message,data} 结构
 */
export function fetchOrdersByStatus(status) {
  return http.get(`/orders/status/${status}`).then((r) => r.data);
}

/** 单张工单自动派单，返回 Assignment */
export function autoAssign(orderId) {
  return http.post(`/dispatch/orders/${orderId}/auto`).then((r) => r.data);
}

/** 批量自动派单，返回 [{orderId, success, workerId, message}] */
export function batchAutoAssign() {
  return http.post('/dispatch/batch').then((r) => r.data);
}

/** 改约（带冲突检测），body: { appointmentTime } */
export function reschedule(orderId, appointmentTime) {
  return http.put(`/dispatch/orders/${orderId}/reschedule`, { appointmentTime }).then((r) => r.data);
}

/** 派单决策审计记录 */
export function fetchAssignments(orderId) {
  return http.get(`/dispatch/orders/${orderId}/assignments`).then((r) => r.data);
}
