import { createRouter, createWebHistory } from 'vue-router';
import Layout from '../views/Layout.vue';
import Forbidden from '../views/Forbidden.vue';
import { me } from '../api/auth';
import {
  authState,
  clearSession,
  firstAccessiblePath,
  getAccessToken,
  hasPermission,
  updateProfile
} from '../auth/session';
import {
  areDynamicRoutesInstalled,
  installDynamicRoutes,
  resetDynamicRoutes
} from './dynamicRoutes';

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/', name: 'Layout', component: Layout, meta: { requiresAuth: true } },
  { path: '/403', name: 'Forbidden', component: Forbidden, meta: { requiresAuth: true } },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: Forbidden }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

let profilePromise = null;
let profileLoadedThisBoot = false;

async function ensureProfileAndRoutes() {
  if (!getAccessToken()) {return false;}
  // 每次应用启动都重新拉取资料，修复历史菜单缓存或后台权限名称变更。
  if (!profileLoadedThisBoot || !authState.permissions?.length || !authState.menus?.length) {
    profilePromise ||= me().then((profile) => {
      updateProfile(profile);
      profileLoadedThisBoot = true;
      return profile;
    }).finally(() => {
      profilePromise = null;
    });
    await profilePromise;
  }
  if (!areDynamicRoutesInstalled()) {installDynamicRoutes(router);}
  return true;
}

/** 登录成功或 Token 刷新后重建动态路由。 */
export async function rebuildRoutes() {
  resetDynamicRoutes(router);
  return installDynamicRoutes(router);
}

router.beforeEach(async (to) => {
  const token = getAccessToken();
  if (to.path === '/login') {
    if (!token) {return true;}
    try {
      await ensureProfileAndRoutes();
      return firstAccessiblePath() || '/403';
    } catch {
      return true;
    }
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  try {
    await ensureProfileAndRoutes();
  } catch {
    clearSession();
    resetDynamicRoutes(router);
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  if (to.path === '/') {return firstAccessiblePath() || '/403';}
  if (to.path === '/403') {return true;}
  if (to.matched.length === 0) {return firstAccessiblePath() || '/403';}
  if (to.meta.permission && !hasPermission(to.meta.permission)) {return '/403';}
  return true;
});

export default router;
