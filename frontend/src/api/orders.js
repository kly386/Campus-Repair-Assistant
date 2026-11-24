import http from './http';

/**
 * AI 智能解析：文本 + 拍照 -> 类别/楼栋/房间/紧急度/置信度
 * formData: text(故障描述) + images(照片，可多个)
 */
export function parseRepair(formData) {
  return http.post('/orders/parse', formData).then((r) => r.data);
}

export function createOrder(data) {
  return http.post('/orders', data).then((r) => r.data);
}

export function getOrder(id) {
  return http.get(`/orders/${id}`).then((r) => r.data);
}

export function updateOrder(data) {
  return http.put('/orders', data).then((r) => r.data);
}

export function listOrdersByStudent(studentId) {
  return http.get(`/orders/student/${studentId}`).then((r) => r.data);
}

export function listOrdersByStatus(status) {
  return http.get(`/orders/status/${status}`).then((r) => r.data);
}
