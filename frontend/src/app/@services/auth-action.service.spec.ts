/**
 * ============================================================================
 * 檔案名稱: auth-action.service.spec.ts
 * 測試對象: AuthActionService (登入、登出、個人資料更新與修改密碼的 API 呼叫與對話框流程)
 * 測試方式: ApiClientService、DialogService、Router 以 Spy 取代；UserStateService 使用真實物件
 * ============================================================================
 */

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApiClientService, ApiResponse } from './api-client.service';
import { AuthActionService } from './auth-action.service';
import { DialogService } from './dialog.service';
import { UserStateService } from './user-state.service';

/**
 * 包成後端成功回應
 * @param data 回應資料
 * @returns ApiResponse 的 Observable
 */
function ok<T>(data: T) {
  return of({ status: 200, success: true, message: '', data, timestamp: '' } as ApiResponse<T>);
}

describe('AuthActionService', () => {
  let service: AuthActionService;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;
  let userState: UserStateService;

  beforeEach(() => {
    sessionStorage.clear();
    api = jasmine.createSpyObj('ApiClientService', ['post', 'put', 'resolveErrorMessage']);
    api.resolveErrorMessage.and.callFake((_err: unknown, fallback: string) => fallback);
    dialog = jasmine.createSpyObj('DialogService', ['showDialog']);
    dialog.showDialog.and.returnValue(of(true));
    router = jasmine.createSpyObj('Router', ['navigateByUrl', 'navigate']);
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthActionService);
    userState = TestBed.inject(UserStateService);
    spyOn(window, 'scrollTo');
  });

  afterEach(() => sessionStorage.clear());

  describe('login', () => {
    it('成功：保存登入身分、顯示歡迎訊息並導向問卷管理', fakeAsync(() => {
      api.post.and.returnValue(ok({ accessToken: 'tk', userId: 1, username: 'u1', fullName: '王小明', email: 'e', role: 'ROLE_USER' }));

      service.login({ username: 'u1', password: 'p' });
      tick(50);

      expect(api.post).toHaveBeenCalledWith('/api/public/auth/login', { username: 'u1', password: 'p' });
      expect(userState.isLoggedIn()).toBeTrue();
      expect(userState.getAccessToken()).toBe('tk');
      expect(dialog.showDialog.calls.mostRecent().args[0].message).toContain('王小明');
      expect(router.navigateByUrl).toHaveBeenCalledWith('/survey-management');
    }));

    it('回應不成功 (success=false) 時不登入也不跳轉', () => {
      api.post.and.returnValue(of({ success: false } as ApiResponse<unknown>));

      service.login({ username: 'u1', password: 'p' });

      expect(userState.isLoggedIn()).toBeFalse();
      expect(dialog.showDialog).not.toHaveBeenCalled();
    });

    it('失敗：顯示錯誤對話框 (訊息由 resolveErrorMessage 轉譯)', () => {
      api.post.and.returnValue(throwError(() => ({ status: 401 })));
      api.resolveErrorMessage.and.returnValue('帳號或密碼錯誤');

      service.login({ username: 'u1', password: 'bad' });

      expect(dialog.showDialog).toHaveBeenCalledWith(jasmine.objectContaining({ title: '登入失敗', message: '帳號或密碼錯誤', mode: 'errorMode' }));
      expect(router.navigateByUrl).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('帳號未開通 (403 ACCOUNT_NOT_VERIFIED)：提示後導向開通頁並預填信箱', () => {
      const error = { status: 403, error: { detail: '該帳號尚未完成電子郵件驗證', code: 'ACCOUNT_NOT_VERIFIED', email: 'a@x.com' } };
      api.post.and.returnValue(throwError(() => error));
      api.resolveErrorMessage.and.returnValue('該帳號尚未完成電子郵件驗證');

      service.login({ username: 'u1', password: 'p' });

      expect(dialog.showDialog).toHaveBeenCalledWith(jasmine.objectContaining({ title: '帳號尚未開通', confirmText: '前往開通頁' }));
      expect(router.navigate).toHaveBeenCalledWith(['/verify-email'], { queryParams: { email: 'a@x.com' } });
    });
  });

  describe('logout', () => {
    it('確認後通知後端、清除本機狀態並回首頁 (後端失敗也照樣登出)', fakeAsync(() => {
      userState.applyAuthResponse({ accessToken: 'tk', userId: 1, username: 'u1', fullName: 'U', email: 'e', role: 'ROLE_USER' });
      sessionStorage.setItem('SURVEY_REFRESH_TOKEN', 'legacy');
      api.post.and.returnValue(throwError(() => ({ status: 500 })));

      service.logout();
      tick(50);

      expect(api.post).toHaveBeenCalledWith('/api/user/auth/logout', {});
      expect(userState.isLoggedIn()).toBeFalse();
      expect(sessionStorage.getItem('SURVEY_REFRESH_TOKEN')).toBeNull();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/home');
    }));

    it('後端登出成功時同樣清除本機狀態', () => {
      api.post.and.returnValue(ok(null));

      service.logout();

      expect(router.navigateByUrl).toHaveBeenCalledWith('/home');
    });

    it('取消確認時什麼都不做', () => {
      dialog.showDialog.and.returnValue(of(false));

      service.logout();

      expect(api.post).not.toHaveBeenCalled();
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('updateProfile', () => {
    const profile = { name: '新名字', nationalId: 'A123456789', phone: '0912345678' };

    beforeEach(() => {
      userState.applyAuthResponse({ accessToken: 'tk', userId: 1, username: 'u1', fullName: '舊名字', email: 'e', role: 'ROLE_USER' });
    });

    it('只改資料：PUT /api/user/profile，更新本機身分並顯示成功', () => {
      api.put.and.returnValue(ok(null));

      service.updateProfile(profile);

      expect(api.put).toHaveBeenCalledOnceWith('/api/user/profile', { fullName: '新名字', phone: '0912345678', identityCard: 'A123456789' });
      expect(userState.userProfile().name).toBe('新名字');
      expect(dialog.showDialog.calls.mostRecent().args[0].title).toBe('個人資料更新成功');
    });

    it('同時改密碼：資料更新後再 PUT /api/user/password，成功顯示雙重成功訊息', () => {
      api.put.and.returnValue(ok(null));

      service.updateProfile({ ...profile, currentPassword: 'Old1!pass', password: 'New1!pass' });

      expect(api.put).toHaveBeenCalledWith('/api/user/password', { oldPassword: 'Old1!pass', newPassword: 'New1!pass' });
      expect(dialog.showDialog.calls.mostRecent().args[0].title).toBe('個人資料與密碼更新成功');
    });

    it('改密碼但沒填目前密碼時送出空字串 (由後端回報錯誤)', () => {
      api.put.and.returnValue(ok(null));

      service.updateProfile({ ...profile, password: 'New1!pass' });

      expect(api.put).toHaveBeenCalledWith('/api/user/password', { oldPassword: '', newPassword: 'New1!pass' });
    });

    it('密碼變更失敗：顯示密碼變更失敗 (資料仍已更新)', () => {
      api.put.withArgs('/api/user/profile', jasmine.anything()).and.returnValue(ok(null));
      api.put.withArgs('/api/user/password', jasmine.anything()).and.returnValue(throwError(() => ({ status: 400 })));

      service.updateProfile({ ...profile, currentPassword: 'wrong', password: 'New1!pass' });

      expect(userState.userProfile().name).toBe('新名字');
      expect(dialog.showDialog.calls.mostRecent().args[0].title).toBe('密碼變更失敗');
    });

    it('資料更新失敗：顯示更新失敗，本機身分不變', () => {
      api.put.and.returnValue(throwError(() => ({ status: 400 })));

      service.updateProfile(profile);

      expect(userState.userProfile().name).toBe('舊名字');
      expect(dialog.showDialog.calls.mostRecent().args[0].title).toBe('更新失敗');
    });
  });

  it('resolveErrorMessage 委派給 ApiClientService', () => {
    expect(service.resolveErrorMessage({ status: 0 }, '預設')).toBe('預設');
    expect(api.resolveErrorMessage).toHaveBeenCalledWith({ status: 0 }, '預設');
  });
});
