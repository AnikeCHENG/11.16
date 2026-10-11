import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    proxy: {
      // Proxy API calls to backend to avoid CORS in development
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // 上传的图片由后端以静态资源提供（P1-12）。必须一起代理：
      // 否则 <img src="/uploads/2026/10/xxx.png"> 会打到 Vite 开发服务器上，
      // 一律 404 —— 后端明明正常，浏览器里却是"图片全裂"，很容易误判成上传失败。
      // 生产部署时同理：/uploads 需要由静态托管或反向代理转发到后端。
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
