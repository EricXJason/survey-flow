/**
 * 責任: E2E 測試共用的網址常數 (後端 API、前端網站、Mailpit 收信 API)
 * 聯動: playwright.config.ts、scripts/start-e2e-backend.sh (連接埠必須一致) → 本檔 → 各 spec 與 support
 * 邊界: 連接埠刻意與日常開發 (8080 / 4200) 不同，可同時執行；可用同名環境變數覆寫
 */

/** 後端 API 埠 */
export const API_PORT = Number(process.env['E2E_API_PORT'] ?? 18080);
/** 前端開發伺服器埠 */
export const WEB_PORT = Number(process.env['E2E_WEB_PORT'] ?? 4300);
/** Mailpit HTTP API 埠 */
export const MAILPIT_PORT = Number(process.env['E2E_MAILPIT_PORT'] ?? 18025);

/** 後端 API 根網址 (前端透過 window.__API_URL__ 指向這裡) */
export const API_URL = `http://localhost:${API_PORT}`;
/** 前端網站根網址 */
export const WEB_URL = `http://localhost:${WEB_PORT}`;
/** Mailpit API 根網址 */
export const MAILPIT_URL = `http://localhost:${MAILPIT_PORT}`;

/** 種子資料中的帳號 (backend/src/main/resources/database/seed/01_reset_and_users.sql) */
export const SEED = {
  admin: { username: 'admin', password: 'Admin123!' },
  member: { username: 'testuser', password: 'User1234!' },
} as const;
