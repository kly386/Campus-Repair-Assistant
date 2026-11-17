import { authState, firstAccessiblePath } from '../auth/session';

const viewModules = {
  Dashboard: () => import('../views/Dashboard.vue'),
  Diagnosis: () => import('../views/Diagnosis.vue'),
  Orders: () => import('../views/Orders.vue'),
  Dispatch: () => import('../views/Dispatch.vue'),
  Workbench: () => import('../views/Workbench.vue'),
  Schedule: () => import('../views/Schedule.vue'),
  Files: () => import('../views/Files.vue'),
  Repair: () => import('../views/Repair.vue'),
  Knowledge: () => import('../views/Knowledge.vue'),
  Users: () => import('../views/Placeholder.vue'),
  Roles: () => import('../views/Placeholder.vue')
};

let installedRouteNames = [];
let routesInstalled = false;

function flattenMenus(menus, result = []) {
  for (const menu of menus || []) {
    if (menu.children?.length) {
      flattenMenus(menu.children, result);
      continue;
    }
    if (menu.path && menu.component !== 'Layout') {result.push(menu);}
  }
  return result;
}

function normalizeChildPath(path) {
  return String(path || '').replace(/^\/+/, '');
}

/**
 * 根据后端 menus 动态挂载 Layout 子路由。
 * component 字符串映射到本地懒加载组件；未知组件使用占位页，避免整棵菜单失效。
 */
export function installDynamicRoutes(router) {
  for (const name of installedRouteNames) {
    if (router.hasRoute(name)) {router.removeRoute(name);}
  }
  installedRouteNames = [];

  const menuRoutes = flattenMenus(authState.menus);
  for (const menu of menuRoutes) {
    const routeName = `menu:${menu.code}`;
    const loader = viewModules[menu.component] || (() => import('../views/Placeholder.vue'));
    router.addRoute('Layout', {
      path: normalizeChildPath(menu.path),
      name: routeName,
      component: loader,
      meta: {
        title: menu.name,
        icon: menu.icon,
        permission: menu.code,
        requiresAuth: true
      }
    });
    installedRouteNames.push(routeName);
  }
  routesInstalled = true;
  return firstAccessiblePath(authState.menus);
}

export function areDynamicRoutesInstalled() {
  return routesInstalled;
}

export function resetDynamicRoutes(router) {
  for (const name of installedRouteNames) {
    if (router.hasRoute(name)) {router.removeRoute(name);}
  }
  installedRouteNames = [];
  routesInstalled = false;
}