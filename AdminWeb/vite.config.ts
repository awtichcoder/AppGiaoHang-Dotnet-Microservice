import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// The browser calls /api and /health on its own origin and Vite forwards them to the
// API Gateway, so the Gateway does not need CORS headers for local development.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_PROXY_TARGET || 'http://localhost:6200';
  const proxy = {
    '/api': { target, changeOrigin: true },
    '/health': { target, changeOrigin: true },
  };
  return {
    plugins: [react()],
    server: { port: 5173, host: '127.0.0.1', proxy },
    preview: { port: 4173, host: '127.0.0.1', proxy },
  };
});
