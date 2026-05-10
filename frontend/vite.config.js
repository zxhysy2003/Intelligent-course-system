import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

import path from 'path';

const backendTarget = process.env.VITE_BACKEND_TARGET || 'http://localhost:8080'

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
  plugins: [vue()],
})
