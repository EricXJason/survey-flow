/**
 * 測試對象: 登入、登出、路由守衛與角色導向、管理員之個資遮罩與稽核日誌 (真實瀏覽器 + 真實後端 + 種子帳號)
 */

import { SEED } from './support/env';
import { expect, test } from './support/fixtures';
import { clickDialogButton, expectDialog, loginViaUi } from './support/ui';

test.describe('登入與權限', () => {
  test('訪客進入問卷管理：提示請先登入並導向登入頁', async ({ page }) => {
    await page.goto('/survey-management');

    await expectDialog(page, '請先登入會員');
    await expect(page).toHaveURL(/\/log-in$/);
  });

  test('密碼錯誤：顯示後端回傳的「帳號或密碼錯誤」，停留在登入頁', async ({ page }) => {
    await page.goto('/log-in');
    await page.locator('[formControlName="username"]').fill(SEED.member.username);
    await page.locator('[formControlName="password"]').fill('WrongPass1!');
    await page.getByRole('button', { name: '登入帳號' }).click();

    const card = await expectDialog(page, '登入失敗');
    await expect(card.locator('.dialog-message')).toContainText('帳號或密碼錯誤');
    await expect(page).toHaveURL(/\/log-in$/);
  });

  test('會員登入後看得到問卷管理；登出後回首頁且無法再進入', async ({ page }) => {
    await loginViaUi(page, SEED.member.username, SEED.member.password);
    const nav = page.locator('.header-nav');
    await expect(nav.getByText('問卷管理')).toBeVisible();
    await expect(nav.getByText('會員管理')).toHaveCount(0);

    await page.locator('.header-actions .btn-logout').click();
    await expectDialog(page, '確認登出');
    await clickDialogButton(page, '登出');

    await expect(page).toHaveURL(/\/home$/);
    await expect(nav.getByText('問卷管理')).toHaveCount(0);
    await page.goto('/survey-management');
    await expect(page).toHaveURL(/\/log-in$/);
  });

  test('一般會員直接輸入會員管理網址：被導回問卷管理', async ({ page }) => {
    await loginViaUi(page, SEED.member.username, SEED.member.password);

    await page.goto('/user-management');

    await expect(page).toHaveURL(/\/survey-management$/);
  });

  test('管理員登入後導覽列多出會員管理，並可看到會員清單', async ({ page }) => {
    await loginViaUi(page, SEED.admin.username, SEED.admin.password);

    await page.locator('.header-nav').getByText('會員管理').click();

    await expect(page).toHaveURL(/\/user-management$/);
    await expect(page.getByRole('table', { name: '全站會員列表' })).toContainText(SEED.member.username);
  });

  test('管理員會員清單：手機號碼為遮罩值，不顯示完整號碼', async ({ page }) => {
    await loginViaUi(page, SEED.admin.username, SEED.admin.password);
    await page.goto('/user-management');

    const table = page.getByRole('table', { name: '全站會員列表' });
    await expect(table).toContainText(SEED.member.username);
    await expect(table.locator('.phone-text').filter({ hasText: /\d/ }).first()).toHaveText(/^\d{4}\*{3}\d{3}$/);
  });

  test('稽核日誌：登入紀錄可依事件篩選查到，且雜湊鏈驗證為完整', async ({ page }) => {
    await loginViaUi(page, SEED.admin.username, SEED.admin.password);
    await page.goto('/user-management');
    await page.getByRole('tab', { name: '稽核日誌' }).click();

    await page.getByLabel('事件類型').selectOption({ label: '登入成功' });
    await expect(page.getByRole('table', { name: '稽核日誌' })).toContainText(SEED.admin.username);

    await page.getByRole('button', { name: '驗證完整性' }).click();
    await expect(page.getByRole('status').filter({ hasText: '稽核日誌完整' })).toBeVisible();
  });
});
