import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:4173', trace: 'retain-on-failure' },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1024, height: 768 } },
    },
  ],
  webServer: {
    command: 'pnpm build --mode test && pnpm preview --mode test --host 127.0.0.1',
    port: 4173,
    reuseExistingServer: !process.env.CI,
    env: {
      VITE_SQL_API_BASE: '',
      VITE_AUTH_API_BASE: '',
      VITE_AI_API_BASE: '',
      VITE_AUTH_PRODUCT_TYPE: 'chinaBank',
      VITE_AUTH_BRIDGE_ALLOWED_ORIGINS: 'http://legacy.example.test',
      VITE_LEGACY_PORTAL_URL: 'http://legacy.example.test/bind',
      VITE_ENABLE_API_MOCK: 'true',
    },
  },
})
