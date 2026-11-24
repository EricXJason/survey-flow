/**
 * ============================================================================
 * 檔案名稱: forget-password.component.spec.ts
 * 測試對象: ForgetPasswordComponent (信箱檢核、寄送重設信、60 秒重寄倒數、離開頁面清除計時器)
 * ============================================================================
 */

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { createApiClientSpy, createDialogSpy, createRouterSpy, lastDialog, ok } from '../../../../testing/test-doubles';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService } from '../../../@services/dialog.service';
import { ForgetPasswordComponent } from './forget-password.component';

describe('ForgetPasswordComponent', () => {
  let component: any;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    api = createApiClientSpy();
    dialog = createDialogSpy();
    router = createRouterSpy();
    await TestBed.configureTestingModule({
      imports: [ForgetPasswordComponent],
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ForgetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('信箱格式錯誤：提示錯誤，不呼叫 API', () => {
    component.formBuilderGroup.setValue({ email: 'not-email' });

    component.checkForgotPasswordData();

    expect(lastDialog(dialog).title).toBe('資料驗證錯誤');
    expect(api.post).not.toHaveBeenCalled();
  });

  it('寄送成功：切到已寄出畫面並開始 60 秒倒數，倒數結束才能重寄', fakeAsync(() => {
    api.post.and.returnValue(ok());
    component.formBuilderGroup.setValue({ email: 'user@example.com' });

    component.checkForgotPasswordData();

    expect(api.post).toHaveBeenCalledWith('/api/public/auth/forget-password', { email: 'user@example.com' });
    expect(component.emailSent).toBeTrue();
    expect(component.resendCountdown).toBe(60);

    component.resendEmail();
    expect(api.post).toHaveBeenCalledTimes(1);

    tick(60_000);
    expect(component.resendCountdown).toBe(0);
    component.resendEmail();
    expect(api.post).toHaveBeenCalledTimes(2);
    component.ngOnDestroy();
  }));

  it('寄送失敗：顯示錯誤並可再次送出', () => {
    api.post.and.returnValue(throwError(() => ({ status: 503 })));
    component.formBuilderGroup.setValue({ email: 'user@example.com' });

    component.checkForgotPasswordData();

    expect(lastDialog(dialog).title).toBe('發送失敗');
    expect(component.isSubmitting).toBeFalse();
    expect(component.emailSent).toBeFalse();
  });

  it('信箱只有空白時不送出', () => {
    component.formBuilderGroup.setValue({ email: '   ' });

    component.sendForgotPasswordData();

    expect(api.post).not.toHaveBeenCalled();
  });

  it('前往重設頁時帶上信箱；離開頁面時清除倒數計時器', fakeAsync(() => {
    api.post.and.returnValue(ok());
    component.formBuilderGroup.setValue({ email: 'user@example.com' });
    component.checkForgotPasswordData();

    component.goToResetPasswordDirectly();
    component.goToLogInPage();
    component.ngOnDestroy();
    tick(5000);

    expect(router.navigate).toHaveBeenCalledWith(['/reset-password'], { queryParams: { email: 'user@example.com' } });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/log-in');
    expect(component.resendCountdown).toBe(60);
  }));
});
