// 단독 실행용 Vite 설정 — 개발 서버에서 /api 요청을 백엔드(기본 8081)로 프록시하여 CORS 없이 동작시킨다.
// Standalone Vite config — dev server proxies /api requests to the backend (default 8081) so no CORS setup is needed.
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5170,
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL || 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
