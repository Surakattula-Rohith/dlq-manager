import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Ports can be changed when the defaults are already taken, e.g.
// FRONTEND_PORT=5174 BACKEND_URL=http://localhost:8081 npm run dev
const frontendPort = Number(process.env.FRONTEND_PORT) || 5173
const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: frontendPort,
    // Fail loudly instead of silently moving to another port
    strictPort: true,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
    },
  },
})
