import { hasPermission } from '../auth/session';

function applyPermission(el, binding) {
  const mode = binding.modifiers.all ? 'all' : 'any';
  const allowed = hasPermission(binding.value, mode);
  if (!el.dataset.permissionDisplay) {
    el.dataset.permissionDisplay = el.style.display || '__empty__';
  }
  el.style.display = allowed
    ? (el.dataset.permissionDisplay === '__empty__' ? '' : el.dataset.permissionDisplay)
    : 'none';
  el.setAttribute('aria-hidden', allowed ? 'false' : 'true');
}

/**
 * v-permission 指令。
 *
 * 用法：
 *   v-permission="'order:create'"
 *   v-permission="['system:user:lock', 'system:user:unlock']"
 *   v-permission.all="['order:read', 'order:update']"
 *
 * 后端仍会做接口级校验，本指令只负责前端展示层控制。
 */
export const permissionDirective = {
  mounted: applyPermission,
  updated: applyPermission
};

export default permissionDirective;