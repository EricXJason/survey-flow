/**
 * 測試對象: 填答頁的業務規則 —— 條件跳題 (選特定選項才出現延伸題)、跳題隱藏的必填題不檢查、已關閉問卷不可填答
 */

import { createSurvey, createUser } from './support/api';
import { expect, test } from './support/fixtures';
import { clickDialogButton, expectDialog } from './support/ui';

test.describe('填答規則', () => {
  test('條件跳題：選「是」才出現 Q2；改選「否」後 Q2 隱藏且不再要求必填', async ({ page, request }) => {
    const owner = await createUser(request);
    const survey = await createSurvey(request, owner.accessToken!, {
      questions: [
        {
          questionCode: 'Q1', type: 'SINGLE', questionText: '您是否吸菸？', isRequired: true, sortOrder: 1,
          options: [
            { optionText: '是', sortOrder: 1, triggerQuestionUid: 'Q2' },
            { optionText: '否', sortOrder: 2, triggerQuestionUid: null },
          ],
        },
        { questionCode: 'Q2', type: 'TEXT', questionText: '每天大約幾支？', isRequired: true, sortOrder: 2, options: [] },
      ],
    });
    const followUp = page.getByRole('heading', { name: '每天大約幾支？' });

    await page.goto(`/survey-runner?surveyId=${survey.id}&from=lobby`);
    await expect(page.getByRole('heading', { name: '您是否吸菸？' })).toBeVisible();
    await expect(followUp).toHaveCount(0);

    await page.getByText('是', { exact: true }).click();
    await expect(followUp).toBeVisible();
    await page.getByRole('button', { name: '確認並提交問卷結果' }).click();
    await expect((await expectDialog(page, '請修正以下欄位'))).toContainText('問題 2');
    await clickDialogButton(page, '了解');

    await page.getByText('否', { exact: true }).click();
    await expect(followUp).toHaveCount(0);
    await page.getByRole('button', { name: '確認並提交問卷結果' }).click();
    await expectDialog(page, '確認送出');
    await clickDialogButton(page, '確認送出');
    await expectDialog(page, '填答成功');
  });

  test('發起人關閉填答的問卷：顯示未開放填答，看不到題目', async ({ page, request }) => {
    const owner = await createUser(request);
    const survey = await createSurvey(request, owner.accessToken!, { isOpen: false, isPublicToLobby: false });

    await page.goto(`/survey-runner?share=${survey.shareToken}`);

    await expect(page.locator('.unavailable-status-pill')).toBeVisible();
    await expect(page.locator('.unavailable-desc')).not.toBeEmpty();
    await expect(page.getByRole('button', { name: '確認並提交問卷結果' })).toHaveCount(0);
  });

  test('僅限連結的問卷：改網址數字直接進入被擋下；憑分享連結可以填答並送出', async ({ page, request }) => {
    const owner = await createUser(request);
    const survey = await createSurvey(request, owner.accessToken!, { isPublicToLobby: false });

    await page.goto(`/survey-runner?surveyId=${survey.id}&from=lobby`);
    await expect(page.locator('.unavailable-desc')).toHaveText('問卷不存在或已下架');

    await page.goto(`/survey-runner?share=${survey.shareToken}`);
    await page.getByText('是', { exact: true }).click();
    await page.getByRole('button', { name: '確認並提交問卷結果' }).click();
    await expectDialog(page, '確認送出');
    await clickDialogButton(page, '確認送出');
    await expectDialog(page, '填答成功');
  });

  test('不存在的問卷編號：顯示後端回傳的「問卷不存在或已下架」，不顯示英文原始錯誤', async ({ page }) => {
    await page.goto('/survey-runner?surveyId=999999&from=lobby');

    await expect(page.locator('.unavailable-desc')).toHaveText('問卷不存在或已下架');
    await expect(page.locator('body')).not.toContainText('Http failure');
  });
});
