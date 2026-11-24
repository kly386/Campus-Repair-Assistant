import http from './http';

/** 用户 / 角色 / 权限管理 API（后端统一 ApiResponse 包裹） */

export function listUsers(params = {}) {
  return http.get('/admin/users', { params }).then((r) => r.data.data);
}

export function updateUser(id, payload) {
  return http.put(`/admin/users/${id}`, payload).then((r) => r.data.data);
}

export function assignUserRoles(id, roleIds) {
  return http.put(`/admin/users/${id}/roles`, { roleIds }).then((r) => r.data);
}

export function lockUser(id, minutes = 10) {
  return http.post(`/admin/users/${id}/lock`, { minutes }).then((r) => r.data);
}

export function unlockUser(id) {
  return http.post(`/admin/users/${id}/unlock`).then((r) => r.data);
}

export function listRoles() {
  return http.get('/admin/roles').then((r) => r.data.data);
}

export function listPermissions() {
  return http.get('/admin/permissions').then((r) => r.data.data);
}

export function getRolePermissionIds(roleId) {
  return http.get(`/admin/roles/${roleId}/permissions`).then((r) => r.data.data);
}

export function assignRolePermissions(roleId, permissionIds) {
  return http.put(`/admin/roles/${roleId}/permissions`, { permissionIds }).then((r) => r.data);
}
