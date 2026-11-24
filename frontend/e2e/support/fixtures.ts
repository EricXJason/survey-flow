/**
 * 責任: 擴充 Playwright 的 test：每個瀏覽器分頁載入前注入 window.__API_URL__，讓前端呼叫 E2E 專用後端
 * 聯動: ApiClientService.resolveApiBaseUrl() 讀取 window.__API_URL__ → 本檔 → 所有 spec 由此匯入 test / expect
 * 邊界: 也套用到 spec 中以 browser.newContext() 另開的瀏覽器 (透過 newContextWithApi)
 */

import { Browser, BrowserContext, test as base } from '@playwright/test';
import { API_URL, WEB_URL } from './env';

/**
 * 讓瀏覽器內容 (Context) 的每一頁都把 API 指向 E2E 後端
 * @param context 瀏覽器內容
 */
async function pointToE2eApi(context: BrowserContext): Promise<void> {
  await context.addInitScript((apiUrl) => {
    (window as unknown as { __API_URL__: string }).__API_URL__ = apiUrl;
  }, API_URL);
}

/**
 * 另開一個獨立的瀏覽器內容 (等同另一位使用者或另一個瀏覽器)，同樣指向 E2E 後端
 * @param browser 瀏覽器
 * @returns 新的瀏覽器內容
 */
export async function newContextWithApi(browser: Browser): Promise<BrowserContext> {
  const context = await browser.newContext({ baseURL: WEB_URL });
  await pointToE2eApi(context);
  return context;
}

export const test = base.extend({
  /**
   * 覆寫預設 context：建立後立即注入 API 位址
   * @param context Playwright 建立的瀏覽器內容
   * @param use 交給測試使用
   */
  context: async ({ context }, use) => {
    await pointToE2eApi(context);
    await use(context);
  },
});

export { expect } from '@playwright/test';
