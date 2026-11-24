/**
 * 責任: 全部 E2E 測試結束後移除 MySQL 與 Mailpit 容器，不留下背景容器佔用連接埠
 * 聯動: playwright.config.ts (globalTeardown) → 本檔；容器由 scripts/start-e2e-backend.sh 建立
 * 邊界: 容器不存在時靜默略過；不會刪除名稱以外的任何容器
 */

import { execSync } from 'node:child_process';

/**
 * 強制移除 E2E 專用容器 (MySQL、Mailpit)
 */
export default function removeE2eContainers(): void {
  execSync('docker rm -f surveyflow-e2e-mysql surveyflow-e2e-mailpit', { stdio: 'ignore' });
}
