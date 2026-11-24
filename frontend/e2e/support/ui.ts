/**
 * 責任: 瀏覽器操作的共用步驟 (登入、對話框按鈕、讀取對話框內容)，讓 spec 只描述業務流程
 * 聯動: 前端 DialogComponent / LogInComponent 之 DOM 結構 → 本檔 → 各 spec
 * 邊界: 選擇器以使用者看得到的文字與 formControlName 為主，不依賴 CSS 樣式細節
 */

import { Locator, Page, expect } from '@playwright/test';

/**
 * 取得目前顯示中的全站對話框
 * @param page 瀏覽器頁面
 * @returns 對話框
 */
export function dialog(page: Page): Locator {
  return page.locator('.dialog-card');
}

/**
 * 等待對話框出現並檢查標題
 * @param page 瀏覽器頁面
 * @param title 預期標題
 * @returns 對話框
 */
export async function expectDialog(page: Page, title: string): Promise<Locator> {
  const card = dialog(page);
  await expect(card.locator('#dialog-title')).toHaveText(title);
  return card;
}

/**
 * 在對話框中按下指定文字的按鈕
 * @param page 瀏覽器頁面
 * @param buttonText 按鈕文字
 */
export async function clickDialogButton(page: Page, buttonText: string): Promise<void> {
  await dialog(page).getByRole('button', { name: buttonText, exact: true }).click();
}

/**
 * 透過登入頁登入；成功後關閉歡迎訊息並等待進入問卷管理頁
 * @param page 瀏覽器頁面
 * @param username 帳號
 * @param password 密碼
 */
export async function loginViaUi(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/log-in');
  await page.locator('[formControlName="username"]').fill(username);
  await page.locator('[formControlName="password"]').fill(password);
  await page.getByRole('button', { name: '登入帳號' }).click();
  await expectDialog(page, '登入成功');
  await clickDialogButton(page, '前往問卷管理');
  await expect(page).toHaveURL(/\/survey-management/);
}
