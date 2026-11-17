import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests (Worksheet 03, Ex. 8). They run against an app that is already started
 * (backend + Vite), ideally on a database used only for tests: see README, "End-to-end tests".
 * Configuration comes from environment variables, never from the repository.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.MATRIZ_E2E_URL ?? 'http://localhost:5173',
    channel: process.env.MATRIZ_E2E_CHANNEL ?? 'chrome', // the installed Google Chrome; no browser download
    locale: 'pt-PT',
    trace: 'off',
  },
});
