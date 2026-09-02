import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Baseline profile: a single root .env (see .env.example) is shared by all
// services, so Vite reads VITE_-prefixed vars from the repo root, not frontend/.
// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  envDir: '../',
  server: {
    port: 5173,
  },
})
