/**
 * 責任: 以 HTTP 直接呼叫後端與 Mailpit，快速準備測試資料 (建立已開通會員、建立問卷、讀取驗證碼)
 * 聯動: env.ts → 本檔 → 各 spec (只在「準備資料」時使用；要驗證的流程一律透過瀏覽器操作)
 * 邊界: 每次產生唯一的帳號、信箱、手機與身分證字號，避免與種子資料或其他測試的唯一索引衝突
 */

import { APIRequestContext, expect } from '@playwright/test';
import { API_URL, MAILPIT_URL } from './env';

/** 測試建立之會員 */
export interface TestUser {
  username: string;
  password: string;
  email: string;
  fullName: string;
  /** 開通後取得之 Access Token (未開通時為 null) */
  accessToken: string | null;
}

/** 身分證字號首字母對應之數值 (內政部規則) */
const NATIONAL_ID_LETTER_CODES: Record<string, number> = {
  A: 10, B: 11, C: 12, D: 13, E: 14, F: 15, G: 16, H: 17, J: 18, K: 19, L: 20, M: 21, N: 22,
  P: 23, Q: 24, R: 25, S: 26, T: 27, U: 28, V: 29, X: 30, Y: 31, W: 32, Z: 33, I: 34, O: 35,
};

let sequence = 0;

/**
 * 產生本次執行唯一的英數字尾碼 (時間 + 遞增序號)，用於帳號與問卷標題
 * @returns 例如 "k3x9a2"
 */
export function uniqueSuffix(): string {
  sequence += 1;
  return `${Date.now().toString(36).slice(-5)}${sequence.toString(36)}`;
}

/**
 * 產生隨機數字字串
 * @param length 位數
 * @returns 數字字串
 */
function randomDigits(length: number): string {
  return Array.from({ length }, () => Math.floor(Math.random() * 10)).join('');
}

/**
 * 產生一組檢查碼正確的身分證字號 (後端與前端都會驗證檢查碼)
 * @returns 例如 "F123456702"
 */
export function randomNationalId(): string {
  const letters = Object.keys(NATIONAL_ID_LETTER_CODES);
  const letter = letters[Math.floor(Math.random() * letters.length)];
  const body = `${Math.random() < 0.5 ? 1 : 2}${randomDigits(7)}`;
  const code = NATIONAL_ID_LETTER_CODES[letter];
  const weights = [8, 7, 6, 5, 4, 3, 2, 1];
  const sum = Math.floor(code / 10) + (code % 10) * 9 + [...body].reduce((acc, d, i) => acc + Number(d) * weights[i], 0);
  return `${letter}${body}${(10 - (sum % 10)) % 10}`;
}

/**
 * 產生隨機的臺灣手機號碼
 * @returns 09 開頭 10 碼
 */
export function randomPhone(): string {
  return `09${randomDigits(8)}`;
}

/**
 * 從 Mailpit 讀出寄給指定信箱的最新 6 碼驗證碼 (信件內連結含 code=xxxxxx)；最多等 15 秒
 * @param request Playwright HTTP 用戶端
 * @param email 收件信箱
 * @returns 6 碼驗證碼
 */
export async function readVerificationCode(request: APIRequestContext, email: string): Promise<string> {
  let code = '';
  await expect(async () => {
    const search = await request.get(`${MAILPIT_URL}/api/v1/search`, { params: { query: `to:"${email}"` } });
    const { messages } = await search.json();
    expect(messages.length).toBeGreaterThan(0);
    const message = await (await request.get(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`)).json();
    const match = /code=(\d{6})/.exec(message.HTML);
    expect(match).not.toBeNull();
    code = match![1];
  }).toPass({ timeout: 15_000 });
  return code;
}

/**
 * 以 API 註冊一位新會員；預設同時讀取驗證信並完成開通
 * @param request Playwright HTTP 用戶端
 * @param options verify=false 時只註冊不開通 (測試「未開通登入」)
 * @returns 會員資料
 */
export async function createUser(request: APIRequestContext, options: { verify?: boolean } = {}): Promise<TestUser> {
  const suffix = uniqueSuffix();
  const user: TestUser = {
    username: `e2e${suffix}`.slice(0, 12),
    password: 'Password1!',
    email: `e2e${suffix}@e2e.test`,
    fullName: '端對端測試',
    accessToken: null,
  };
  const registered = await request.post(`${API_URL}/api/public/auth/register`, {
    data: {
      username: user.username, email: user.email, password: user.password, fullName: user.fullName,
      phone: randomPhone(), identityCard: randomNationalId(),
    },
  });
  expect(registered.ok(), await registered.text()).toBeTruthy();
  if (options.verify === false) {
    return user;
  }
  const code = await readVerificationCode(request, user.email);
  const verified = await request.post(`${API_URL}/api/public/auth/verify-email`, { data: { email: user.email, code } });
  expect(verified.ok(), await verified.text()).toBeTruthy();
  user.accessToken = (await verified.json()).data.accessToken;
  return user;
}

/** 建立問卷時可覆寫的欄位 (與後端 SurveyCreateRequest 同名) */
export interface SurveyOverrides {
  title?: string;
  isOpen?: boolean;
  isPublicToLobby?: boolean;
  questions?: unknown[];
}

/**
 * 以 API 建立問卷 (預設：公開、開放、不限期，一題必填單選「是 / 否」)
 * @param request Playwright HTTP 用戶端
 * @param accessToken 擁有者的 Access Token
 * @param overrides 要覆寫之欄位
 * @returns 問卷編號、標題與分享代碼 (專屬填答連結 /survey-runner?share=<代碼>)
 */
export async function createSurvey(
  request: APIRequestContext,
  accessToken: string,
  overrides: SurveyOverrides = {},
): Promise<{ id: number; title: string; shareToken: string }> {
  const title = overrides.title ?? `E2E 問卷 ${uniqueSuffix()}`;
  const response = await request.post(`${API_URL}/api/user/surveys`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      title,
      description: '',
      category: '顧客滿意度',
      isPublicToLobby: overrides.isPublicToLobby ?? true,
      isOpen: overrides.isOpen ?? true,
      startDate: null,
      endDate: null,
      basicInfoConfig: { collectBasicInfo: false },
      questions: overrides.questions ?? [{
        questionCode: 'Q1', type: 'SINGLE', questionText: '是否推薦給朋友？', isRequired: true, sortOrder: 1,
        options: [
          { optionText: '是', sortOrder: 1, triggerQuestionUid: null },
          { optionText: '否', sortOrder: 2, triggerQuestionUid: null },
        ],
      }],
    },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const { id, shareToken } = (await response.json()).data;
  return { id, title, shareToken };
}
