import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development the dev server proxies /api to the backend, matching what
// nginx does in the built image. The app therefore always calls same-origin
// /api paths and never needs to know the backend's address or rely on CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    outDir: 'build',
    sourcemap: true,
  },
})
