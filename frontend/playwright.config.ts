import { defineConfig } from '@playwright/test'
const externalBaseUrl = process.env.PLAYWRIGHT_BASE_URL
export default defineConfig({
  testDir: './tests',
  workers: 1,
  use: {
    baseURL: externalBaseUrl || 'http://127.0.0.1:5173',
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}),
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
  },
  reporter: 'list',
  webServer: externalBaseUrl ? undefined : { command: 'npm run dev -- --host 127.0.0.1 --strictPort', url: 'http://127.0.0.1:5173', reuseExistingServer: true },
})
