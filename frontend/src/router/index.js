import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', redirect: '/repair' },
      { path: 'repair', name: 'Repair', component: () => import('../views/Repair.vue') },
      { path: 'diagnosis', name: 'Diagnosis', component: () => import('../views/Diagnosis.vue') },
      { path: 'orders', name: 'Orders', component: () => import('../views/Orders.vue') },
      { path: 'schedule', name: 'Schedule', component: () => import('../views/Schedule.vue') },
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
