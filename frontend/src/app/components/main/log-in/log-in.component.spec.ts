/**
 * ============================================================================
 * 檔案名稱: log-in.component.spec.ts
 * 測試對象: LogInComponent (欄位檢核、送出登入、密碼顯示切換、前往註冊 / 忘記密碼)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { createDialogSpy, createRouterSpy, lastDialog } from '../../../../testing/test-doubles';
import { DialogService } from '../../../@services/dialog.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { LogInComponent } from './log-in.component';

describe('LogInComponent', () => {
  let component: any;
  let emitter: jasmine.SpyObj<UserEmitterService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    emitter = jasmine.createSpyObj('UserEmitterService', ['emitLogin']);
    dialog = createDialogSpy();
    router = createRouterSpy();
    await TestBed.configureTestingModule({
      imports: [LogInComponent],
      providers: [
        { provide: UserEmitterService, useValue: emitter },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(LogInComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('欄位未填：列出所有錯誤，不送出登入', () => {
    component.checkLogInData();

    expect(lastDialog(dialog).title).toBe('登入資料錯誤');
    expect(lastDialog(dialog).message).toContain('使用者帳號');
    expect(lastDialog(dialog).message).toContain('登入密碼');
    expect(emitter.emitLogin).not.toHaveBeenCalled();
  });

  it('欄位正確：送出帳號密碼', () => {
    component.formBuilderGroup.setValue({ username: 'admin', password: 'Admin123!' });

    component.checkLogInData();

    expect(emitter.emitLogin).toHaveBeenCalledWith({ username: 'admin', password: 'Admin123!' });
    expect(dialog.showDialog).not.toHaveBeenCalled();
  });

  it('密碼顯示切換與頁面導覽', () => {
    component.toggleShowPassword();
    expect(component.showPassword()).toBeTrue();

    component.goToForgetPasswordPage();
    component.goToRegisterAccountPage();

    expect(router.navigateByUrl.calls.allArgs()).toEqual([['/forget-password'], ['/register']]);
  });
});
