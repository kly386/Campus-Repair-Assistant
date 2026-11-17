import { reactive } from 'vue';

const ACCESS_TOKEN_KEY = 'accessToken';
const LEGACY_TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';
const USER_KEY = 'user';
const PERMISSIONS_KEY = 'permissions:v2';
const MENUS_KEY = 'menus:v2';
const FINGERPRINT_KEY = 'clientFingerprint';

function readJson(key, fallback) {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

function readUser() {
  const user = readJson(USER_KEY, null);
  return user && typeof user === 'object' ? user : null;
}

export const authState = reactive({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY) || localStorage.getItem(LEGACY_TOKEN_KEY) || '',
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) || '',
  user: readUser(),
  permissions: readJson(PERMISSIONS_KEY, []),
  menus: readJson(MENUS_KEY, []),
  routesReady: false
});

function normalizePermissionList(permissions) {
  if (Array.isArray(permissions)) {return permissions.filter(Boolean);}
  if (permissions instanceof Set) {return [...permissions];}
  return [];
}

/**
 * 保存后端统一登录结果。
 * 兼容后端同时返回 token/accessToken 的过渡期协议。
 */
export function setSession(data = {}) {
  const accessToken = data.accessToken || data.token || authState.accessToken;
  const refreshToken = data.refreshToken || authState.refreshToken;
  const permissions = normalizePermissionList(data.permissions);
  const menus = Array.isArray(data.menus) ? data.menus : [];
  const user = {
    userId: data.userId ?? authState.user?.userId,
    username: data.username || authState.user?.username || '',
    realName: data.realName || authState.user?.realName || '',
    role: data.role || authState.user?.role || '',
    roles: Array.isArray(data.roles) ? data.roles : authState.user?.roles || [],
    phone: data.phone ?? authState.user?.phone,
    email: data.email ?? authState.user?.email
  };

  authState.accessToken = accessToken;
  authState.refreshToken = refreshToken;
  authState.user = user;
  authState.permissions = permissions;
  authState.menus = menus;
  authState.routesReady = false;

  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(LEGACY_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(permissions));
  localStorage.setItem(MENUS_KEY, JSON.stringify(menus));
}

/** 刷新 /user/me 后补全用户资料和权限，不覆盖 Token。 */
export function updateProfile(profile = {}) {
  setSession({
    accessToken: authState.accessToken,
    refreshToken: authState.refreshToken,
    userId: profile.userId,
    username: profile.username,
    realName: profile.realName,
    role: profile.role,
    roles: profile.roles,
    permissions: profile.permissions,
    menus: profile.menus,
    phone: profile.phone,
    email: profile.email
  });
}

export function clearSession() {
  authState.accessToken = '';
  authState.refreshToken = '';
  authState.user = null;
  authState.permissions = [];
  authState.menus = [];
  authState.routesReady = false;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(LEGACY_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(PERMISSIONS_KEY);
  localStorage.removeItem(MENUS_KEY);
  localStorage.removeItem('permissions');
  localStorage.removeItem('menus');
}

export function getAccessToken() {
  return authState.accessToken || localStorage.getItem(ACCESS_TOKEN_KEY) || '';
}

export function getRefreshToken() {
  return authState.refreshToken || localStorage.getItem(REFRESH_TOKEN_KEY) || '';
}

/** 稳定的客户端指纹，登录、刷新和所有业务请求必须保持一致。 */
export function getClientFingerprint() {
  let fingerprint = localStorage.getItem(FINGERPRINT_KEY);
  if (!fingerprint) {
    fingerprint = globalThis.crypto?.randomUUID?.()
      || `fp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(FINGERPRINT_KEY, fingerprint);
  }
  return fingerprint;
}

export function hasPermission(required, mode = 'any') {
  if (!required) {return true;}
  const requiredList = Array.isArray(required) ? required.filter(Boolean) : [required];
  if (requiredList.length === 0) {return true;}
  const granted = new Set(authState.permissions || []);
  if (granted.has('*:*')) {return true;}
  return mode === 'all'
    ? requiredList.every((permission) => granted.has(permission))
    : requiredList.some((permission) => granted.has(permission));
}

/** 递归找到菜单树中第一个可访问叶子路由。 */
export function firstAccessiblePath(menus = authState.menus) {
  for (const menu of menus || []) {
    if (menu.children?.length) {
      const childPath = firstAccessiblePath(menu.children);
      if (childPath) {return childPath;}
    }
    if (menu.path && menu.component !== 'Layout') {return menu.path;}
  }
  return '';
}
