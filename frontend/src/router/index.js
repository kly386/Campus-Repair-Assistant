import { createRouter, createWebHistory } from 'vue-router';
import Layout from '../views/Layout.vue';

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', redirect: '/repair' },
      { path: 'repair', name: 'Repair', component: () => import('../views/Repair.vue') },
      { path: 'diagnosis', name: 'Diagnosis', component: () => import('../views/Diagnosis.vue') },
      { path: 'orders', name: 'Orders', component: () => import('../views/Orders.vue') },
      { path: 'schedule', name: 'Schedule', component: () => import('../views/Schedule.vue') },
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'files', name: 'Files', component: () => import('../views/Files.vue') }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 路由守卫：未登录一律跳登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token');
  const userJson = localStorage.getItem('user');
  if (to.path !== '/login' && (!token || !userJson)) {
    next('/login');
  } else if (to.path === '/login' && token) {
    next('/repair');
  } else {
    next();
  }
});

export default router;
