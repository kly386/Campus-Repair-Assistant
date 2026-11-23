import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8081',
        changeOrigin: true
      },
      // WebSocket 实时诊断：ws:true 才会把 Upgrade 头转发给后端
      '/ws': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8081',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
