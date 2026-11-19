import { defineConfig, devices } from '@playwright/test';

const deployedURL = process.env.PLAYWRIGHT_BASE_URL;
const baseURL = deployedURL || 'http://127.0.0.1:4173';

export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: deployedURL ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173 --strictPort',
    url: baseURL,
    reuseExistingServer: false,
  },
});
