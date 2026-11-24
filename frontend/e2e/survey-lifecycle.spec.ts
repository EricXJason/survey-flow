/**
 * 測試對象: 問卷完整生命週期 —— 會員在設計頁建立問卷 → 出現在管理清單 → 訪客從大廳找到並填答 → 擁有者在分析頁看到回收數
 */

import { createSurvey, createUser, uniqueSuffix } from './support/api';
import { expect, newContextWithApi, test } from './support/fixtures';
import { clickDialogButton, expectDialog, loginViaUi } from './support/ui';

test('建立問卷 → 訪客填答 → 擁有者查看統計', async ({ page, request, browser }) => {
  const owner = await createUser(request);
  const title = `E2E 滿意度調查 ${uniqueSuffix()}`;

  // 1. 擁有者在設計頁建立問卷：一題單選 (三個選項) + 一題選填簡答
  await loginViaUi(page, owner.username, owner.password);
  await page.goto('/survey-design');
  await page.locator('[formControlName="title"]').fill(title);
  await page.locator('[formControlName="category"]').selectOption('顧客滿意度');
  const first = page.locator('.question-card').nth(0);
  await first.locator('[formControlName="type"]').selectOption('radio');
  await first.locator('[formControlName="description"]').fill('您對本次服務的整體感受？');
  const options = first.locator('.option-row-card [formControlName="text"]');
  await options.nth(0).fill('滿意');
  await options.nth(1).fill('普通');
  await options.nth(2).fill('不滿意');
  await first.locator('[formControlName="isRequired"]').check();
  await page.getByRole('button', { name: '+ 新增題目' }).click();
  const second = page.locator('.question-card').nth(1);
  await second.locator('[formControlName="type"]').selectOption('text');
  await second.locator('[formControlName="description"]').fill('其他建議');
  await page.getByRole('button', { name: '儲存並發布問卷設計' }).click();

  await expectDialog(page, '確認發布問卷');
  await clickDialogButton(page, '立即發布');
  await expectDialog(page, '發布成功');
  await clickDialogButton(page, '返回問卷管理');
  await expect(page).toHaveURL(/\/survey-management$/);
  await page.getByPlaceholder('搜尋問卷名稱、編號或分類...').fill(title);
  await expect(page.getByRole('cell', { name: title, exact: true })).toBeVisible();

  // 2. 另一個瀏覽器 (訪客) 從大廳搜尋並填答
  const guestContext = await newContextWithApi(browser);
  const guest = await guestContext.newPage();
  await guest.goto('/lobby');
  await guest.getByPlaceholder('搜尋問卷名稱、編號或分類關鍵字...').fill(title);
  await guest.getByRole('button', { name: `進入填答問卷：${title}` }).click();
  await expect(guest).toHaveURL(/\/survey-runner\?/);
  await guest.getByText('滿意', { exact: true }).click();
  await guest.locator('textarea').fill('客服回覆很快');
  await guest.getByRole('button', { name: '確認並提交問卷結果' }).click();
  await expectDialog(guest, '確認送出');
  await clickDialogButton(guest, '確認送出');
  await expectDialog(guest, '填答成功');
  await clickDialogButton(guest, '探索問卷大廳');
  await expect(guest).toHaveURL(/\/lobby$/);
  await guestContext.close();

  // 3. 擁有者打開數據分析：回收 1 份，文字回覆出現在清單中
  await page.getByRole('button', { name: `查看問卷數據：${title}` }).click();
  await expect(page).toHaveURL(/view=analytics/);
  await expect(page.locator('.analytics-main-title')).toHaveText(title);
  await expect(page.locator('.kpi-card-total .akpi-value')).toHaveText('1');
  await expect(page.getByText('客服回覆很快')).toBeVisible();

  // 4. 瀏覽器「上一頁」回到清單 (分析頁狀態記錄在網址)
  await page.goBack();
  await expect(page.getByRole('cell', { name: title, exact: true })).toBeVisible();
});

test('必填題未作答：提交時列出未完成題號，不送出', async ({ page, request }) => {
  const owner = await createUser(request);
  const survey = await createSurvey(request, owner.accessToken!);

  await page.goto(`/survey-runner?surveyId=${survey.id}&from=lobby`);
  // [Why]: 等題目渲染完成才送出，否則問卷尚未載入時按下會得到不同的提示，造成偶發失敗
  await expect(page.getByText('是否推薦給朋友？')).toBeVisible();
  await page.getByRole('button', { name: '確認並提交問卷結果' }).click();

  const card = await expectDialog(page, '請修正以下欄位');
  await expect(card).toContainText('問題 1：此必填項目尚未完成');
});
