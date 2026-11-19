import { defineConfig } from '@playwright/test'

/**
 * 端到端冒烟：对着正在运行的前端（5173）+ 后端（8080）跑，不自动起服务。
 *
 *   E2E_ADMIN_USER=admin1 E2E_ADMIN_PASSWORD=... npx playwright test
 *
 * 未提供账号时整组跳过，避免把口令写进仓库。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: globalThis.process?.env.E2E_BASE_URL || 'http://localhost:5173',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'mobile-375', use: { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true } },
    { name: 'desktop', use: { viewport: { width: 1440, height: 900 } } },
  ],
})
