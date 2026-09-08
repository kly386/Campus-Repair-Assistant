import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'evaluation', name: 'Evaluation', component: () => import('../views/Evaluation.vue') },
      { path: 'report', name: 'Report', component: () => import('../views/Report.vue') }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
