/**
 * 責任: 瀏覽器端到端 (E2E) 測試設定：自動啟動「MySQL + Mailpit + 後端 (18080) + 前端 (4300)」完整環境，
 *       以 Chromium 執行 e2e/*.spec.ts，結束後移除容器
 * 聯動: e2e/scripts/start-e2e-backend.sh (後端與容器)、ng serve (前端) → 本檔 → e2e/*.spec.ts
 * 邊界: 測試會寫入資料 (新會員、新問卷、填答)，因此依序執行 (workers: 1)，後端與資料庫每次都重新建立；
 *       前端開發伺服器無狀態，本機已在 4300 執行時直接沿用
 */

import { defineConfig, devices } from '@playwright/test';
import { API_URL, WEB_PORT, WEB_URL } from './e2e/support/env';

const isCi = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e',
  // [Why]: 各測試共用同一個資料庫，平行執行時登入鎖定、清單筆數等狀態會互相干擾
  fullyParallel: false,
  workers: 1,
  forbidOnly: isCi,
  retries: isCi ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: isCi ? [['list'], ['html', { open: 'never' }]] : [['list']],
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: WEB_URL,
    locale: 'zh-TW',
    timezoneId: 'Asia/Taipei',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'bash e2e/scripts/start-e2e-backend.sh',
      url: `${API_URL}/actuator/health`,
      // [Why]: 首次需下載容器映像並打包後端，給足 5 分鐘
      timeout: 300_000,
      // [Why]: 每次都重建資料庫；且 globalTeardown 會移除容器，沿用舊後端會連到已不存在的資料庫
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: `pnpm exec ng serve --port ${WEB_PORT}`,
      url: WEB_URL,
      timeout: 180_000,
      reuseExistingServer: !isCi,
    },
  ],
});
