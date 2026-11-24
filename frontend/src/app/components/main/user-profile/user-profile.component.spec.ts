/**
 * ============================================================================
 * 檔案名稱: user-profile.component.spec.ts
 * 測試對象: UserProfileComponent (管理員導向會員管理、載入最新資料、修改密碼欄位切換與檢核、送出、未存檔離開確認)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { createApiClientSpy, createDialogSpy, createRouterSpy, lastDialog, ok } from '../../../../testing/test-doubles';
import { UserProfileData } from '../../../@interfaces/account-model';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService } from '../../../@services/dialog.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { UserStateService } from '../../../@services/user-state.service';
import { UserProfileComponent } from './user-profile.component';

/** 已登入會員的本機資料 */
const MEMBER: UserProfileData = {
  id: '1', access: 'member', userName: 'tester01', name: '王小明', nationalId: 'A123456789', email: 'a@x.com', phone: '0912345678',
};

describe('UserProfileComponent', () => {
  let component: any;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;
  let emitter: jasmine.SpyObj<UserEmitterService>;

  /**
   * 以指定身分建立元件
   * @param profile 本機登入資料
   */
  async function create(profile: UserProfileData = MEMBER): Promise<void> {
    TestBed.inject(UserStateService).setIdentity(profile);
    const fixture = TestBed.createComponent(UserProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    sessionStorage.clear();
    api = createApiClientSpy();
    api.get.and.returnValue(ok(null));
    dialog = createDialogSpy();
    router = createRouterSpy();
    emitter = jasmine.createSpyObj('UserEmitterService', ['emitUpdateProfile']);
    await TestBed.configureTestingModule({
      imports: [UserProfileComponent],
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
        { provide: UserEmitterService, useValue: emitter },
      ],
    }).compileComponents();
  });

  afterEach(() => sessionStorage.clear());

  it('管理員進入時導向會員管理，不載入資料', async () => {
    await create({ ...MEMBER, access: 'admin' });

    expect(router.navigate).toHaveBeenCalledWith(['/user-management']);
    expect(api.get).not.toHaveBeenCalled();
  });

  it('先顯示本機資料，再以後端最新資料覆蓋並同步登入身分', async () => {
    api.get.and.returnValue(ok({ fullName: '王大明', phone: '0987654321', identityCard: 'A123456789' }));

    await create();

    expect(api.get).toHaveBeenCalledWith('/api/user/profile');
    expect(component.formBuilderGroup.getRawValue()).toEqual(jasmine.objectContaining({
      username: 'tester01', email: 'a@x.com', name: '王大明', phone: '0987654321',
    }));
    expect(TestBed.inject(UserStateService).userProfile().name).toBe('王大明');
  });

  it('後端資料欄位缺少時保留本機值', async () => {
    api.get.and.returnValue(ok({}));

    await create();

    expect(component.formBuilderGroup.getRawValue().name).toBe('王小明');
  });

  it('欄位錯誤：列出錯誤，不送出', async () => {
    await create();
    component.formBuilderGroup.patchValue({ name: 'Tom', phone: '123' });

    component.checkProfileData();

    expect(lastDialog(dialog).message).toContain('真實姓名');
    expect(lastDialog(dialog).message).toContain('手機號碼');
    expect(emitter.emitUpdateProfile).not.toHaveBeenCalled();
  });

  it('展開修改密碼：三個欄位變必填並檢核強度與一致性；收合時清空', async () => {
    await create();
    component.togglePasswordChange();

    component.checkProfileData();
    ['目前密碼尚未填寫', '新密碼尚未填寫', '確認新密碼尚未填寫'].forEach((text) => expect(lastDialog(dialog).message).toContain(text));

    component.formBuilderGroup.patchValue({ currentPassword: 'Old1!pass', password: 'weak', confirmPassword: 'other' });
    component.checkProfileData();
    expect(lastDialog(dialog).message).toContain('新密碼強度不符');
    expect(lastDialog(dialog).message).toContain('不一致');

    component.togglePasswordChange();
    expect(component.formBuilderGroup.getRawValue().password).toBe('');
    expect(component.formBuilderGroup.valid).toBeTrue();
  });

  it('送出：只改資料時不帶密碼；有改密碼時帶目前密碼與新密碼', async () => {
    await create();
    component.checkProfileData();
    expect(emitter.emitUpdateProfile).toHaveBeenCalledWith({ name: '王小明', nationalId: 'A123456789', phone: '0912345678' });

    component.togglePasswordChange();
    component.formBuilderGroup.patchValue({ currentPassword: 'Old1!pass', password: 'New1!pass', confirmPassword: 'New1!pass' });
    component.checkProfileData();
    expect(emitter.emitUpdateProfile).toHaveBeenCalledWith(jasmine.objectContaining({ currentPassword: 'Old1!pass', password: 'New1!pass' }));
  });

  it('離開確認：有未存檔變更時詢問；沒有變更或已送出時直接離開', async () => {
    await create();
    expect(component.canDeactivate()).toBeTrue();

    component.formBuilderGroup.markAsDirty();
    component.formBuilderGroup.patchValue({ phone: '0911111111' });
    let leave: boolean | undefined;
    (component.canDeactivate() as Observable<boolean>).subscribe((value: boolean) => (leave = value));
    expect(leave).toBeTrue();
    expect(lastDialog(dialog).title).toBe('跳出確認');

    component.sendProfileData();
    expect(component.canDeactivate()).toBeTrue();
  });

  it('密碼顯示切換', async () => {
    await create();

    component.toggleShowCurrentPassword();
    component.toggleShowPassword();
    component.toggleShowConfirmPassword();

    expect(component.showCurrentPassword() && component.showPassword() && component.showConfirmPassword()).toBeTrue();
  });
});
