/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Configuration comes from the .env file at the repository root (envDir), like the backend.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '');
  return {
    envDir: '..',
    plugins: [react()],
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      include: ['src/**/*.test.{ts,tsx}'], // e2e/ is for Playwright
    },
    server: {
      port: 5173,
      // In development the browser calls /api on the Vite server, which forwards to the backend
      // (same origin, so the session cookie and the CSRF cookie work without CORS).
      proxy: {
        '/api': {
          target: env.MATRIZ_BACKEND_URL || 'http://localhost:8080',
          changeOrigin: false,
        },
      },
    },
  };
});
