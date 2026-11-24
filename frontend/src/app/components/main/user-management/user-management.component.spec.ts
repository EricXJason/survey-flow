/**
 * ============================================================================
 * 檔案名稱: user-management.component.spec.ts
 * 測試對象: UserManagementComponent (非管理員導回、載入會員清單、統計與篩選、刪除確認、凍結 / 恢復確認、會員 / 稽核分頁切換)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { createApiClientSpy, createDialogSpy, lastDialog, ok } from '../../../../testing/test-doubles';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService } from '../../../@services/dialog.service';
import { UserStateService } from '../../../@services/user-state.service';
import { ManagedUser, UserManagementComponent } from './user-management.component';

/** 模擬 /api/admin/users 回應：含一筆管理員 (驗證畫面防護判斷)、正常、凍結、待開通與一筆未知狀態代碼 */
const BACKEND_USERS = [
  { id: 1, username: 'admin', role: 'ROLE_ADMIN', status: 'ACTIVE' },
  { id: 2, username: 'alice', fullName: '王小美', email: 'alice@x.com', phone: '0911', role: 'ROLE_USER', status: 'ACTIVE', createdAt: '2026-01-02T03:04:05' },
  { id: 3, username: 'bob', role: 'ROLE_USER', status: 'SUSPENDED' },
  { id: 4, role: 'ROLE_USER', status: 'PENDING_VERIFICATION' },
  { id: 5, username: 'eve', role: 'ROLE_USER', status: 'UNKNOWN' },
];

describe('UserManagementComponent', () => {
  let component: UserManagementComponent;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: Router;

  /**
   * 以指定權限建立元件
   * @param access 登入者權限
   */
  async function create(access = 'admin'): Promise<void> {
    TestBed.inject(UserStateService).access.set(access);
    const fixture = TestBed.createComponent(UserManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /**
   * 依編號找出清單中的會員
   * @param id 會員編號
   * @returns 會員
   */
  const user = (id: string): ManagedUser => component.usersList().find((u) => u.id === id)!;

  beforeEach(async () => {
    api = createApiClientSpy();
    api.get.and.returnValue(ok({ content: BACKEND_USERS }));
    dialog = createDialogSpy();
    await TestBed.configureTestingModule({
      imports: [UserManagementComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  it('管理員專區切換：預設為會員帳號，切到稽核日誌後顯示稽核面板', async () => {
    await create();
    expect(component.activeView()).toBe('users');

    component.setActiveView('audit');

    expect(component.activeView()).toBe('audit');
  });

  it('非管理員：導回問卷管理且不載入資料', async () => {
    await create('member');

    expect(router.navigate).toHaveBeenCalledWith(['/survey-management']);
    expect(api.get).not.toHaveBeenCalled();
  });

  it('載入清單：不另行過濾回應；狀態轉為正常 / 待開通 / 凍結 (未知代碼視為凍結)；欄位缺少時顯示預設值', async () => {
    await create();

    expect(api.get).toHaveBeenCalledWith('/api/admin/users?page=0&size=100');
    expect(component.usersList().map((u) => u.id)).toEqual(['1', '2', '3', '4', '5']);
    expect(user('1').role).toBe('admin');
    expect(user('2')).toEqual(jasmine.objectContaining({ fullName: '王小美', registeredAt: '2026-01-02', status: 'active', role: 'user' }));
    expect(user('4')).toEqual(jasmine.objectContaining({ username: 'user', email: '未填寫', phone: '未填寫', registeredAt: '-', status: 'pending' }));
    expect(user('5').status).toBe('suspended');
    expect([component.totalUsers(), component.activeUsers(), component.pendingUsers(), component.suspendedUsers()]).toEqual([5, 2, 1, 2]);
  });

  it('載入失敗顯示無法連線；回應沒有 content 時清單維持空白', async () => {
    api.get.and.returnValue(throwError(() => ({ status: 0 })));
    await create();
    expect(component.isServerUnavailable()).toBeTrue();

    api.get.and.returnValue(ok({}));
    component.loadUsers();
    expect(component.isLoading()).toBeFalse();
    expect(component.usersList()).toEqual([]);
  });

  it('狀態篩選與關鍵字搜尋 (帳號、姓名、信箱、電話、編號)', async () => {
    await create();

    component.setStatusFilter('suspended');
    expect(component.filteredUsers().map((u) => u.id)).toEqual(['3', '5']);
    component.setStatusFilter('pending');
    expect(component.filteredUsers().map((u) => u.id)).toEqual(['4']);

    component.setStatusFilter('all');
    component.onSearchInput({ target: { value: 'ALICE' } } as unknown as Event);
    expect(component.filteredUsers().map((u) => u.id)).toEqual(['2']);
    component.onSearchInput({ target: { value: '小美' } } as unknown as Event);
    expect(component.filteredUsers().map((u) => u.id)).toEqual(['2']);

    component.clearSearch();
    expect(component.filteredUsers().length).toBe(5);
  });

  it('角色名稱與頭像縮寫', async () => {
    await create();

    expect(component.getRoleLabel('ROLE_ADMIN')).toBe('系統管理員');
    expect(component.getRoleLabel('user')).toBe('一般會員');
    expect(component.getUserInitial('alice')).toBe('a');
    expect(component.getUserInitial('')).toBe('U');
  });

  describe('刪除會員', () => {
    it('確認後刪除並從清單移除', async () => {
      await create();
      api.delete.and.returnValue(ok());

      component.onDeleteUser(user('2'));

      expect(api.delete).toHaveBeenCalledWith('/api/admin/users/2');
      expect(component.usersList().map((u) => u.id)).toEqual(['1', '3', '4', '5']);
      expect(lastDialog(dialog).title).toBe('會員帳號已移除');
    });

    it('取消時不刪除；失敗時顯示錯誤且清單不變；管理員不可刪除', async () => {
      await create();
      dialog.showDialog.and.returnValue(of(false));
      component.onDeleteUser(user('2'));
      expect(api.delete).not.toHaveBeenCalled();

      dialog.showDialog.and.returnValue(of(true));
      api.delete.and.returnValue(throwError(() => ({ status: 500 })));
      component.onDeleteUser(user('2'));
      expect(lastDialog(dialog).title).toBe('移除會員失敗');
      expect(component.usersList().length).toBe(5);

      component.onDeleteUser({ ...user('2'), role: 'admin' });
      expect(lastDialog(dialog).title).toBe('無法刪除系統管理員');
    });
  });

  describe('凍結 / 恢復帳號', () => {
    it('凍結前先確認並說明影響，確認後送出並更新該列', async () => {
      await create();
      api.patch.and.returnValue(ok());

      component.toggleUserStatus(user('2'));

      expect(lastDialog(dialog).title).toBe('確認凍結會員帳號');
      expect(lastDialog(dialog).message).toContain('無法登入');
      expect(api.patch).toHaveBeenCalledWith('/api/admin/users/2/status?status=SUSPENDED', {});
      expect(user('2').status).toBe('suspended');
    });

    it('恢復帳號送出 ACTIVE；取消確認時不送出', async () => {
      await create();
      api.patch.and.returnValue(ok());

      component.toggleUserStatus(user('3'));
      expect(api.patch).toHaveBeenCalledWith('/api/admin/users/3/status?status=ACTIVE', {});
      expect(user('3').status).toBe('active');

      dialog.showDialog.and.returnValue(of(false));
      component.toggleUserStatus(user('2'));
      expect(api.patch).toHaveBeenCalledTimes(1);
    });

    it('送出失敗顯示錯誤且狀態不變；管理員與待開通帳號不可切換', async () => {
      await create();
      api.patch.and.returnValue(throwError(() => ({ status: 403 })));

      component.toggleUserStatus(user('2'));
      expect(lastDialog(dialog).title).toBe('變更帳號狀態失敗');
      expect(user('2').status).toBe('active');

      dialog.showDialog.calls.reset();
      component.toggleUserStatus({ ...user('2'), role: 'admin' });
      component.toggleUserStatus(user('4'));
      expect(dialog.showDialog).not.toHaveBeenCalled();
    });

    it('狀態開關文字：管理員受保護、正常、待開通、已凍結各有對應說明', async () => {
      await create();

      expect(component.getStatusToggleText(user('1')).hint).toContain('受保護');
      expect(component.getStatusToggleText(user('2')).label).toBe('正常啟用');
      expect(component.getStatusToggleText(user('4'))).toEqual({ label: '待開通', hint: '會員尚未完成信箱驗證，開通後才可登入' });
      expect(component.getStatusToggleText(user('3')).label).toBe('已凍結');
      expect([component.canToggleStatus(user('1')), component.canToggleStatus(user('2')), component.canToggleStatus(user('4'))]).toEqual([false, true, false]);
    });
  });
});
