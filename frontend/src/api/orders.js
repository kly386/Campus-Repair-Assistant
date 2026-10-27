import axios from 'axios';

const http = axios.create({ baseURL: '/api' });

export function createOrder(data) {
  return http.post('/orders', data).then((r) => r.data);
}

export function getOrder(id) {
  return http.get(`/orders/${id}`).then((r) => r.data);
}

export function updateOrder(data) {
  return http.put('/orders', data).then((r) => r.data);
}
