/**
 * 測試對象: 註冊 → 信箱驗證碼開通 → 自動登入 (驗證信由 Mailpit 接收，測試從 Mailpit 讀出真正寄出的驗證碼)
 */

import { createUser, randomNationalId, randomPhone, readVerificationCode, uniqueSuffix } from './support/api';
import { expect, test } from './support/fixtures';
import { clickDialogButton, expectDialog } from './support/ui';

test.describe('註冊與信箱開通', () => {
  test('填寫註冊表單 → 收到驗證信 → 輸入 6 碼開通 → 自動登入並進入大廳', async ({ page, request }) => {
    const suffix = uniqueSuffix();
    const username = `reg${suffix}`.slice(0, 12);
    const email = `reg${suffix}@e2e.test`;

    await page.goto('/register');
    await page.locator('[formControlName="username"]').fill(username);
    await page.locator('[formControlName="email"]').fill(email);
    await page.locator('[formControlName="password"]').fill('Password1!');
    await page.locator('[formControlName="confirmPassword"]').fill('Password1!');
    await page.locator('[formControlName="name"]').fill('王小明');
    await page.locator('[formControlName="nationalId"]').fill(randomNationalId());
    await page.locator('[formControlName="phone"]').fill(randomPhone());
    await page.getByRole('button', { name: '確認送出並接收驗證碼' }).click();

    await expectDialog(page, '註冊資料已建立');
    await clickDialogButton(page, '開始驗證');
    await page.getByPlaceholder('000000').fill(await readVerificationCode(request, email));
    await page.getByRole('button', { name: '驗證開通並直接登入' }).click();

    await expectDialog(page, '信箱驗證成功');
    await clickDialogButton(page, '進入問卷大廳');
    await expect(page).toHaveURL(/\/lobby$/);
    await expect(page.locator('.header-nav').getByText('問卷管理')).toBeVisible();
  });

  test('表單有誤時列出每一個錯誤欄位，不送出', async ({ page }) => {
    await page.goto('/register');
    await page.locator('[formControlName="username"]').fill('ab');
    await page.locator('[formControlName="nationalId"]').fill('A123456788');
    await page.getByRole('button', { name: '確認送出並接收驗證碼' }).click();

    const card = await expectDialog(page, '欄位驗證錯誤');
    await expect(card).toContainText('帳號未填寫或格式錯誤');
    await expect(card).toContainText('身分證字號未填寫或檢核碼不符');
    await expect(page).toHaveURL(/\/register$/);
  });

  test('尚未開通就登入：提示後導向開通頁 (信箱已預填)，輸入驗證碼完成開通', async ({ page, request }) => {
    const user = await createUser(request, { verify: false });

    await page.goto('/log-in');
    await page.locator('[formControlName="username"]').fill(user.username);
    await page.locator('[formControlName="password"]').fill(user.password);
    await page.getByRole('button', { name: '登入帳號' }).click();
    await expectDialog(page, '帳號尚未開通');
    await clickDialogButton(page, '前往開通頁');

    await expect(page).toHaveURL(/\/verify-email\?email=/);
    await expect(page.getByPlaceholder('例如：user@example.com')).toHaveValue(user.email);
    await page.getByPlaceholder('請輸入 6 碼數字啟用碼').fill(await readVerificationCode(request, user.email));
    await page.getByRole('button', { name: '開通帳號並登入' }).click();

    await expect(page.getByText('正常啟用中')).toBeVisible();
    await page.getByRole('button', { name: '前往問卷大廳' }).click();
    await expect(page.locator('.header-nav').getByText('問卷管理')).toBeVisible();
  });

  test('點擊信件中的直達連結 (email + code)：自動開通', async ({ page, request }) => {
    const user = await createUser(request, { verify: false });
    const code = await readVerificationCode(request, user.email);

    await page.goto(`/verify-email?email=${encodeURIComponent(user.email)}&code=${code}`);

    await expect(page.getByText('正常啟用中')).toBeVisible();
  });
});
