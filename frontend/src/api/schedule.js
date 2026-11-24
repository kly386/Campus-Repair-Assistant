import http from './http';

/** 排班管理 API（后端统一 ApiResponse 包裹，这里直接取 data） */

export function listSchedules(params = {}) {
  return http.get('/schedule', { params }).then((r) => r.data.data);
}

export function listScheduleWorkers() {
  return http.get('/schedule/workers').then((r) => r.data.data);
}

export function createSchedule(payload) {
  return http.post('/schedule', payload).then((r) => r.data.data);
}

export function updateSchedule(id, payload) {
  return http.put(`/schedule/${id}`, payload).then((r) => r.data.data);
}

export function deleteSchedule(id) {
  return http.delete(`/schedule/${id}`).then((r) => r.data.data);
}
