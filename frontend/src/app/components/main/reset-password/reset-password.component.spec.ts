/**
 * ============================================================================
 * 檔案名稱: reset-password.component.spec.ts
 * 測試對象: ResetPasswordComponent (網址預填、欄位與兩次密碼檢核、送出重設、成功後回登入頁)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { createApiClientSpy, createDialogSpy, createRouterSpy, lastDialog, ok } from '../../../../testing/test-doubles';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService } from '../../../@services/dialog.service';
import { ResetPasswordComponent } from './reset-password.component';

describe('ResetPasswordComponent', () => {
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
      imports: [ResetPasswordComponent],
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { queryParams: of(queryParams) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = createApiClientSpy();
    dialog = createDialogSpy();
    router = createRouterSpy();
  });

  it('郵件連結帶入的信箱與驗證碼自動預填', async () => {
    await create({ email: 'a@x.com', code: '123456' });

    expect(component.formBuilderGroup.value).toEqual(jasmine.objectContaining({ email: 'a@x.com', code: '123456' }));
  });

  it('欄位未填：列出所有錯誤', async () => {
    await create();

    component.checkResetPasswordData();

    const message: string = lastDialog(dialog).message;
    ['電子信箱', '6 位數驗證碼', '新密碼須為', '確認密碼'].forEach((text) => expect(message).toContain(text));
    expect(api.post).not.toHaveBeenCalled();
  });

  it('兩次密碼不一致：提示不一致', async () => {
    await create({ email: 'a@x.com', code: '123456' });
    component.formBuilderGroup.patchValue({ newPassword: 'Password1!', confirmPassword: 'Password2!' });

    component.checkResetPasswordData();

    expect(lastDialog(dialog).message).toContain('確認密碼與新密碼不一致');
  });

  it('送出成功：呼叫重設 API，提示後回登入頁', async () => {
    await create();
    api.post.and.returnValue(ok());
    component.formBuilderGroup.setValue({ email: 'a@x.com', code: '123456', newPassword: 'Password1!', confirmPassword: 'Password1!' });

    component.checkResetPasswordData();

    expect(api.post).toHaveBeenCalledWith('/api/public/auth/reset-password', { email: 'a@x.com', code: '123456', newPassword: 'Password1!' });
    expect(lastDialog(dialog).title).toBe('密碼重設成功');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/log-in');
  });

  it('送出失敗 (驗證碼錯誤或過期)：顯示錯誤；密碼顯示切換', async () => {
    await create();
    api.post.and.returnValue(throwError(() => ({ status: 400 })));
    component.formBuilderGroup.setValue({ email: 'a@x.com', code: '123456', newPassword: 'Password1!', confirmPassword: 'Password1!' });

    component.sendResetPasswordData();
    component.toggleShowNewPassword();
    component.toggleShowConfirmPassword();
    component.goToLogInPage();

    expect(lastDialog(dialog).title).toBe('重設失敗');
    expect(component.isSubmitting).toBeFalse();
    expect(component.showNewPassword() && component.showConfirmPassword()).toBeTrue();
  });
});
