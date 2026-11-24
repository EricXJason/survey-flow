/**
 * ============================================================================
 * 檔案名稱: user-state.service.spec.ts
 * 測試對象: UserStateService (登入身分 Signals、Access Token 保存、sessionStorage 持久化與還原、登出重設)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { AuthResponseData } from '../@interfaces/account-model';
import { ACCESS_TOKEN_STORAGE_KEY, UserStateService } from './user-state.service';

/** 使用者狀態於 sessionStorage 之鍵名 (與服務內私有常數一致) */
const USER_STATE_KEY = 'SURVEY_USER_STATE_SESSION';

/**
 * 建立一筆後端登入回應測試資料
 * @param overrides 要覆寫之欄位
 * @returns AuthResponseData
 */
function authResponse(overrides: Partial<AuthResponseData> = {}): AuthResponseData {
  return {
    accessToken: 'access-1',
    userId: 7,
    username: 'tester01',
    fullName: '王小明',
    email: 'tester@example.com',
    role: 'ROLE_USER',
    ...overrides,
  } as AuthResponseData;
}

describe('UserStateService', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  afterEach(() => sessionStorage.clear());

  it('初始為訪客，未登入', () => {
    const service = TestBed.inject(UserStateService);

    expect(service.access()).toBe('guest');
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
  });

  it('applyAuthResponse：保存 Access Token、ROLE_USER 對應 member，並寫入 sessionStorage', () => {
    const service = TestBed.inject(UserStateService);

    const profile = service.applyAuthResponse(authResponse({ phone: '0912345678' }));

    expect(profile.id).toBe('7');
    expect(profile.access).toBe('member');
    expect(profile.phone).toBe('0912345678');
    expect(service.isLoggedIn()).toBeTrue();
    expect(service.getAccessToken()).toBe('access-1');
    expect(JSON.parse(sessionStorage.getItem(USER_STATE_KEY)!).userProfile.userName).toBe('tester01');
  });

  it('applyAuthResponse：ROLE_ADMIN 對應 admin；只有舊欄位 token 時也能保存；選填欄位缺少時為空字串', () => {
    const service = TestBed.inject(UserStateService);

    const profile = service.applyAuthResponse(authResponse({ role: 'ROLE_ADMIN', accessToken: undefined, token: 'legacy' }));

    expect(profile.access).toBe('admin');
    expect(profile.nationalId).toBe('');
    expect(profile.phone).toBe('');
    expect(service.getAccessToken()).toBe('legacy');
  });

  it('重新整理頁面 (重新建立服務) 時自 sessionStorage 還原登入狀態', () => {
    TestBed.inject(UserStateService).applyAuthResponse(authResponse());

    TestBed.resetTestingModule();
    const restored = TestBed.inject(UserStateService);

    expect(restored.userName()).toBe('tester01');
    expect(restored.isLoggedIn()).toBeTrue();
  });

  it('sessionStorage 內容損壞或缺少 userProfile 時維持訪客', () => {
    sessionStorage.setItem(USER_STATE_KEY, '{壞掉的 JSON');
    expect(TestBed.inject(UserStateService).isLoggedIn()).toBeFalse();

    TestBed.resetTestingModule();
    sessionStorage.setItem(USER_STATE_KEY, '{}');
    expect(TestBed.inject(UserStateService).isLoggedIn()).toBeFalse();
  });

  it('resetToGuest：Signals 歸零並清除使用者狀態與 Access Token', () => {
    const service = TestBed.inject(UserStateService);
    service.applyAuthResponse(authResponse());

    service.resetToGuest();

    expect(service.access()).toBe('guest');
    expect(service.isLoggedIn()).toBeFalse();
    expect(sessionStorage.getItem(USER_STATE_KEY)).toBeNull();
    expect(sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull();
  });

  it('sessionStorage 無法使用 (例如隱私模式配額為 0) 時不拋出例外', () => {
    const service = TestBed.inject(UserStateService);
    spyOn(Storage.prototype, 'setItem').and.throwError('QuotaExceededError');
    spyOn(Storage.prototype, 'getItem').and.throwError('SecurityError');
    spyOn(Storage.prototype, 'removeItem').and.throwError('SecurityError');

    expect(() => service.applyAuthResponse(authResponse())).not.toThrow();
    expect(service.getAccessToken()).toBeNull();
    expect(() => service.resetToGuest()).not.toThrow();
  });
});
