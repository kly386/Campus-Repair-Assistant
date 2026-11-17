import axios from 'axios';

const http = axios.create({ baseURL: '/api' });

/**
 * AI 智能解析：文字 + 拍照 → 类别/楼栋/房间/紧急度/置信度
 * formData: text(故障描述) + images(照片，可多个)
 */
export function parseRepair(formData) {
  return http.post('/orders/parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then((r) => r.data);
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
