import { defineConfig } from '@playwright/test'

// Local fixture regression only: intercepted hosted URLs never contact the cloud site.
export default defineConfig({
  testDir: './tests',
  testMatch: 'demo-origin-ui.spec.ts',
  workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:15179',
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  reporter: 'list',
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 15179 --strictPort',
    url: 'http://127.0.0.1:15179',
    reuseExistingServer: false,
    // Invalid entries must not broaden the explicitly permitted HTTPS origin.
    env: { VITE_DEMO_ALLOWED_ORIGINS: 'http://spectrace.52zzx.top,https://*.52zzx.top,https://spectrace.52zzx.top' },
  },
})
