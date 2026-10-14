import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // O Spotify não aceita "localhost" no retorno do login, então o app roda em 127.0.0.1.
    host: '127.0.0.1',
    port: 5173, // a API libera CORS para esta porta
  },
})
