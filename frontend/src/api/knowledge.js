import http from './http';

/**
 * 知识库接口（对接闵娟 KnowledgeController 实际路径）
 * 学生口：按类别浏览(仅 APPROVED)/搜索/有帮助+1
 * 管理员口：admin/list、CRUD、review、rollback、history、软删
 */

export function listKnowledge(params) {
  return http.get('/knowledge/admin/list', { params }).then((r) => r.data);
}

export function searchKnowledge(q) {
  return http.get('/knowledge/search', { params: { q } }).then((r) => r.data);
}

export function browseCategory(categoryId) {
  return http.get(`/knowledge/category/${categoryId}`).then((r) => r.data);
}

export function markHelpful(id) {
  return http.post(`/knowledge/helpful/${id}`).then((r) => r.data);
}

export function createKnowledge(data) {
  return http.post('/knowledge', data).then((r) => r.data);
}

export function updateKnowledge(id, data) {
  return http.put(`/knowledge/${id}`, data).then((r) => r.data);
}

export function reviewKnowledge(id, status) {
  return http.put(`/knowledge/review/${id}`, null, { params: { status } }).then((r) => r.data);
}

export function rollbackKnowledge(historyId) {
  return http.post(`/knowledge/rollback/${historyId}`).then((r) => r.data);
}

export function deleteKnowledge(id) {
  return http.delete(`/knowledge/${id}`).then((r) => r.data);
}

export function listKnowledgeHistory(knowledgeId) {
  return http.get(`/knowledge/${knowledgeId}/history`).then((r) => r.data);
}
