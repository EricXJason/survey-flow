/**
 * ============================================================================
 * 檔案名稱: register.component.spec.ts
 * 測試對象: RegisterComponent (欄位檢核、送出註冊、輸入 6 碼開通、補寄驗證碼與倒數、返回修改)
 * ============================================================================
 */

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { createApiClientSpy, createDialogSpy, lastDialog, ok } from '../../../../testing/test-doubles';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService } from '../../../@services/dialog.service';
import { UserStateService } from '../../../@services/user-state.service';
import { RegisterComponent } from './register.component';

/** 一份全部合法的註冊表單 */
const VALID_FORM = {
  username: 'tester01',
  password: 'Password1!',
  confirmPassword: 'Password1!',
  name: '王小明',
  nationalId: 'A123456789',
  email: 'tester@example.com',
  phone: '0912345678',
};

describe('RegisterComponent', () => {
  let component: any;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    api = createApiClientSpy();
    dialog = createDialogSpy();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
      ],
    }).compileComponents();
    // [Why]: 範本使用 routerLink，需要真正的 Router；只監看 navigateByUrl 不實際換頁
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    component.ngOnDestroy();
    sessionStorage.clear();
  });

  it('全部未填：列出每一個欄位的錯誤', () => {
    component.checkRegisterData();

    const message: string = lastDialog(dialog).message;
    ['帳號', '密碼格式', '確認密碼', '中文姓名', '身分證', '電子信箱', '手機號碼'].forEach((text) => expect(message).toContain(text));
    expect(api.post).not.toHaveBeenCalled();
  });

  it('兩次密碼不一致：提示不相符', () => {
    component.formBuilderGroup.setValue({ ...VALID_FORM, confirmPassword: 'Password2!' });

    component.checkRegisterData();

    expect(lastDialog(dialog).message).toContain('確認密碼與密碼不相符');
  });

  it('送出成功：欄位名稱轉成後端格式，切到開通畫面並開始倒數', () => {
    api.post.and.returnValue(ok());
    component.formBuilderGroup.setValue(VALID_FORM);

    component.checkRegisterData();

    expect(api.post).toHaveBeenCalledWith('/api/public/auth/register', {
      username: 'tester01', email: 'tester@example.com', password: 'Password1!', fullName: '王小明', phone: '0912345678', identityCard: 'A123456789',
    });
    expect(component.isVerifyingEmail).toBeTrue();
    expect(component.registeredEmail).toBe('tester@example.com');
    expect(component.resendCountdown).toBe(60);
  });

  it('送出失敗 (例如帳號重複)：顯示錯誤並停在表單', () => {
    api.post.and.returnValue(throwError(() => ({ status: 400 })));
    component.formBuilderGroup.setValue(VALID_FORM);

    component.checkRegisterData();

    expect(lastDialog(dialog).title).toBe('註冊失敗');
    expect(component.isVerifyingEmail).toBeFalse();
  });

  it('開通：驗證碼不足 6 碼不送出；成功後自動登入並導向大廳', () => {
    component.registeredEmail = 'tester@example.com';
    component.verificationCode = '123';
    component.submitEmailVerification();
    expect(api.post).not.toHaveBeenCalled();

    api.post.and.returnValue(ok({ accessToken: 'tk', userId: 1, username: 'tester01', fullName: '王小明', email: 'e', role: 'ROLE_USER' }));
    component.verificationCode = ' 123456 ';
    component.submitEmailVerification();

    expect(api.post).toHaveBeenCalledWith('/api/public/auth/verify-email', { email: 'tester@example.com', code: '123456' });
    expect(TestBed.inject(UserStateService).isLoggedIn()).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/lobby');
  });

  it('開通成功但回應沒有 Token：不登入但仍導向大廳；開通失敗顯示錯誤', () => {
    component.verificationCode = '123456';
    api.post.and.returnValue(ok(null));
    component.submitEmailVerification();
    expect(TestBed.inject(UserStateService).isLoggedIn()).toBeFalse();

    api.post.and.returnValue(throwError(() => ({ status: 400 })));
    component.submitEmailVerification();
    expect(lastDialog(dialog).title).toBe('驗證失敗');
    expect(component.isSubmittingCode).toBeFalse();
  });

  it('補寄驗證碼：倒數中不能補寄；倒數結束後補寄成功會重新倒數；失敗顯示錯誤', fakeAsync(() => {
    api.post.and.returnValue(ok());
    component.registeredEmail = 'tester@example.com';
    component.resendCountdown = 5;
    component.resendVerificationCode();
    expect(api.post).not.toHaveBeenCalled();

    component.resendCountdown = 0;
    component.resendVerificationCode();
    expect(api.post).toHaveBeenCalledWith('/api/public/auth/resend-verification', { email: 'tester@example.com' });
    tick(60_000);
    expect(component.resendCountdown).toBe(0);

    api.post.and.returnValue(throwError(() => ({ status: 429 })));
    component.resendVerificationCode();
    expect(lastDialog(dialog).title).toBe('發送失敗');
  }));

  it('返回修改表單時清空驗證碼；密碼顯示切換', () => {
    component.isVerifyingEmail = true;
    component.verificationCode = '123456';

    component.backToRegisterForm();
    component.toggleShowPassword();
    component.toggleShowConfirmPassword();

    expect(component.isVerifyingEmail).toBeFalse();
    expect(component.verificationCode).toBe('');
    expect(component.showPassword() && component.showConfirmPassword()).toBeTrue();
  });
});
