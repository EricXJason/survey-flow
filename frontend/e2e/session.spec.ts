/**
 * 測試對象: 登入狀態的資安行為 —— Access Token 過期後自動以 HttpOnly Refresh Cookie 換發、
 *           管理員凍結帳號後該會員無法登入、恢復後可再登入
 * 前提: E2E 後端的 Access Token 有效期為 20 秒 (scripts/start-e2e-backend.sh)
 */

import { createSurvey, createUser } from './support/api';
import { API_URL, SEED } from './support/env';
import { expect, test } from './support/fixtures';
import { clickDialogButton, expectDialog, loginViaUi } from './support/ui';

test.describe('登入狀態與帳號管理', () => {
  test('Access Token 過期：前端自動換發並重送請求，使用者不需重新登入', async ({ page, request, context }) => {
    const owner = await createUser(request);
    const survey = await createSurvey(request, owner.accessToken!);
    await loginViaUi(page, owner.username, owner.password);

    // Refresh Token 只存在 HttpOnly Cookie，JavaScript 讀不到
    const cookies = await context.cookies(API_URL);
    const refreshCookie = cookies.find((cookie) => cookie.httpOnly);
    expect(refreshCookie, '應有 HttpOnly 之 Refresh Token Cookie').toBeDefined();
    expect(await page.evaluate(() => document.cookie)).not.toContain(refreshCookie!.value);

    // [Why]: 等待超過 Access Token 有效期 (20 秒)，下一個請求必定先得到 401
    await page.waitForTimeout(21_000);
    const refreshed = page.waitForResponse((response) => response.url().endsWith('/api/public/auth/refresh'));
    await page.reload();

    expect((await refreshed).status()).toBe(200);
    await page.getByPlaceholder('搜尋問卷名稱、編號或分類...').fill(survey.title);
    await expect(page.getByRole('cell', { name: survey.title, exact: true })).toBeVisible();
    await expect(page).toHaveURL(/\/survey-management$/);
  });

  test('管理員凍結會員：確認後狀態改為已凍結，該會員無法登入；恢復後可再登入', async ({ page, request }) => {
    const member = await createUser(request);
    await loginViaUi(page, SEED.admin.username, SEED.admin.password);
    await page.goto('/user-management');
    await page.getByPlaceholder('搜尋會員姓名、電子郵件、手機號碼或 ID...').fill(member.username);
    const row = page.getByRole('row', { name: new RegExp(member.username) });

    await row.getByRole('switch', { name: '點擊以凍結此帳號' }).click();
    await expectDialog(page, '確認凍結會員帳號');
    await clickDialogButton(page, '確定凍結');
    await expect(row).toContainText('已凍結');

    const login = await request.post(`${API_URL}/api/public/auth/login`, { data: { username: member.username, password: member.password } });
    expect(login.status()).toBe(400);
    expect((await login.json()).detail).toContain('非啟用狀態');

    await row.getByRole('switch', { name: '點擊以恢復此帳號' }).click();
    await expectDialog(page, '確認恢復會員帳號');
    await clickDialogButton(page, '確定恢復');
    await expect(row).toContainText('正常啟用');

    const relogin = await request.post(`${API_URL}/api/public/auth/login`, { data: { username: member.username, password: member.password } });
    expect(relogin.status()).toBe(200);
  });
});
