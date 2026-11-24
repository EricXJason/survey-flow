/**
 * ============================================================================
 * 檔案名稱: verify-email.component.spec.ts
 * 測試對象: VerifyEmailComponent (郵件連結自動開通、手動輸入驗證碼、補寄驗證信與倒數、導覽)
 * ============================================================================
 */

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { createApiClientSpy, createDialogSpy, createRouterSpy, lastDialog, ok } from '../../../../testing/test-doubles';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService } from '../../../@services/dialog.service';
import { UserStateService } from '../../../@services/user-state.service';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent', () => {
  let component: any;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;

  /**
   * 以指定網址參數建立元件
   * @param queryParams 網址查詢參數
   */
  async function create(queryParams: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { queryParams: of(queryParams) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    sessionStorage.clear();
    api = createApiClientSpy();
    dialog = createDialogSpy();
    router = createRouterSpy();
  });

  afterEach(() => sessionStorage.clear());

  it('郵件連結帶入信箱與 6 碼驗證碼：自動開通並登入', async () => {
    api.post.and.returnValue(ok({ accessToken: 'tk', userId: 1, username: 'u1', fullName: 'U', email: 'a@x.com', role: 'ROLE_USER' }));

    await create({ email: ' a@x.com ', code: '123456' });

    expect(api.post).toHaveBeenCalledWith('/api/public/auth/verify-email', { email: 'a@x.com', code: '123456' });
    expect(component.isVerifiedSuccess).toBeTrue();
    expect(TestBed.inject(UserStateService).isLoggedIn()).toBeTrue();
  });

  it('只有信箱時只預填，不自動開通；開通成功但沒有 Token 時不登入', async () => {
    await create({ email: 'a@x.com', code: '12' });
    expect(api.post).not.toHaveBeenCalled();
    expect(component.targetEmail).toBe('a@x.com');

    api.post.and.returnValue(ok(null));
    component.verificationCode = '654321';
    component.handleManualVerify();

    expect(component.isVerifiedSuccess).toBeTrue();
    expect(TestBed.inject(UserStateService).isLoggedIn()).toBeFalse();
  });

  it('手動開通：信箱或驗證碼不完整時顯示欄位錯誤', async () => {
    await create();

    component.handleManualVerify();
    expect(component.errorMessage).toBe('請輸入待驗證的電子信箱');

    component.targetEmail = 'a@x.com';
    component.verificationCode = '123';
    component.handleManualVerify();
    expect(component.errorMessage).toBe('請輸入完整的 6 位數啟用驗證碼');
  });

  it('開通失敗：顯示錯誤訊息；開通中重複點擊不會重送', async () => {
    await create();
    api.post.and.returnValue(throwError(() => ({ status: 400 })));
    component.isVerifying = true;
    component.executeVerification('a@x.com', '123456');
    expect(api.post).not.toHaveBeenCalled();

    component.isVerifying = false;
    component.executeVerification('a@x.com', '123456');

    expect(component.errorMessage).toContain('開通驗證失敗');
    expect(component.isVerifiedSuccess).toBeFalse();
  });

  it('補寄驗證信：沒填信箱時提示；成功後倒數 60 秒內不能再寄', fakeAsync(async () => {
    await create();
    component.handleResendVerification();
    expect(lastDialog(dialog).title).toBe('請輸入電子信箱');

    api.post.and.returnValue(ok());
    component.targetEmail = 'a@x.com';
    component.handleResendVerification();
    component.handleResendVerification();

    expect(api.post).toHaveBeenCalledOnceWith('/api/public/auth/resend-verification', { email: 'a@x.com' });
    expect(lastDialog(dialog).title).toBe('開通信已寄出');
    tick(60_000);
    expect(component.resendCountdown).toBe(0);
    component.ngOnDestroy();
  }));

  it('補寄失敗顯示錯誤；導覽按鈕', async () => {
    await create({ email: 'a@x.com' });
    api.post.and.returnValue(throwError(() => ({ status: 429 })));

    component.handleResendVerification();
    component.goToLogIn();
    component.goToLobby();
    component.goToManagement();
    component.ngOnDestroy();

    expect(lastDialog(dialog).title).toBe('重新發送失敗');
    expect(component.isResending).toBeFalse();
    expect(router.navigateByUrl.calls.allArgs()).toEqual([['/log-in'], ['/lobby'], ['/survey-management']]);
  });
});
