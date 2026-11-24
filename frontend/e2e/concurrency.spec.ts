/**
 * 測試對象: 樂觀鎖在真實瀏覽器中的行為 —— 同一份問卷在兩個瀏覽器同時開啟設定，後送出者得到「已被其他人修改」，
 *           重新整理取得新版本號後即可儲存
 */

import { Page } from '@playwright/test';
import { createSurvey, createUser } from './support/api';
import { expect, newContextWithApi, test } from './support/fixtures';
import { clickDialogButton, expectDialog, loginViaUi } from './support/ui';

/**
 * 在問卷管理頁打開指定問卷的「時限與發布設定」視窗
 * @param page 瀏覽器頁面
 * @param title 問卷標題
 */
async function openSettings(page: Page, title: string): Promise<void> {
  await page.getByPlaceholder('搜尋問卷名稱、編號或分類...').fill(title);
  await page.getByRole('button', { name: `調整問卷時限與發布設定：${title}` }).click();
  await expect(page.getByRole('heading', { name: '問卷時限與發布設定' })).toBeVisible();
}

/**
 * 切換設定視窗中的開關
 * @param page 瀏覽器頁面
 * @param toggleTitle 開關標題
 */
async function toggle(page: Page, toggleTitle: string): Promise<void> {
  await page.locator('.toggle-card', { hasText: toggleTitle }).locator('input').click();
}

test('樂觀鎖：兩個分頁同時修改同一份問卷，後送出者被拒絕並提示重新整理', async ({ page, request, browser }) => {
  const owner = await createUser(request);
  const survey = await createSurvey(request, owner.accessToken!);
  const otherContext = await newContextWithApi(browser);
  const other = await otherContext.newPage();
  await loginViaUi(page, owner.username, owner.password);
  await loginViaUi(other, owner.username, owner.password);

  // 兩邊都先打開設定視窗 (兩邊讀到的版本號相同)
  await openSettings(page, survey.title);
  await openSettings(other, survey.title);

  // A 先儲存：成功，版本號 +1
  await toggle(page, '開放私人連結填寫');
  await page.getByRole('button', { name: '儲存設定' }).click();
  await expectDialog(page, '時限與發布狀態已更新');

  // B 用舊版本號儲存：409，顯示後端訊息
  await toggle(other, '公開至問卷大廳');
  await other.getByRole('button', { name: '儲存設定' }).click();
  const conflict = await expectDialog(other, '更新失敗');
  await expect(conflict).toContainText('此問卷已被其他人修改');
  await clickDialogButton(other, '確定');

  // B 重新整理後取得新版本號，即可成功儲存
  await other.reload();
  await openSettings(other, survey.title);
  await toggle(other, '公開至問卷大廳');
  await other.getByRole('button', { name: '儲存設定' }).click();
  await expectDialog(other, '時限與發布狀態已更新');
  await otherContext.close();
});
