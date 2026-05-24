import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

import path from 'path';

const backendTarget = process.env.VITE_BACKEND_TARGET || 'http://localhost:8080'
const normalizeModuleId = (id) => id.replace(/\\/g, '/')

// https://vite.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/videos': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id) {
          const moduleId = normalizeModuleId(id)
          if (!moduleId.includes('/node_modules/')) {
            return
          }
          if (
            moduleId.includes('/node_modules/vue/')
            || moduleId.includes('/node_modules/@vue/')
            || moduleId.includes('/node_modules/vue-router/')
            || moduleId.includes('/node_modules/pinia/')
          ) {
            return 'vendor-vue'
          }
          if (
            moduleId.includes('/node_modules/element-plus/')
            || moduleId.includes('/node_modules/@element-plus/')
          ) {
            return 'vendor-element-plus'
          }
          if (
            moduleId.includes('/node_modules/echarts/')
            || moduleId.includes('/node_modules/zrender/')
          ) {
            return 'vendor-echarts'
          }
          return 'vendor-common'
        },
      },
    },
  },
  plugins: [vue()],
})
