/**
 * ============================================================================
 * 檔案名稱: app.routes.spec.ts
 * 測試對象: 路由表與兩個守衛 (authGuard 未登入導向登入頁、canDeactivateGuard 離開前確認)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { authGuard, canDeactivateGuard, CanComponentDeactivate, routes } from './app.routes';
import { DialogService } from './@services/dialog.service';
import { UserStateService } from './@services/user-state.service';

describe('app.routes', () => {
  let dialog: jasmine.SpyObj<DialogService>;

  beforeEach(() => {
    sessionStorage.clear();
    dialog = jasmine.createSpyObj('DialogService', ['showDialog']);
    dialog.showDialog.and.returnValue(of(true));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: DialogService, useValue: dialog }],
    });
  });

  afterEach(() => sessionStorage.clear());

  /**
   * 在注入環境中執行 authGuard
   * @returns 守衛結果 (true 或導向用的 UrlTree)
   */
  const runAuthGuard = () =>
    TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  describe('authGuard', () => {
    it('已登入：放行', () => {
      TestBed.inject(UserStateService).setIdentity({
        id: '1', access: 'member', userName: 'u1', name: 'U', nationalId: '', email: '', phone: '',
      });

      expect(runAuthGuard()).toBeTrue();
      expect(dialog.showDialog).not.toHaveBeenCalled();
    });

    it('訪客：提示請先登入並導向 /log-in', () => {
      const result = runAuthGuard() as UrlTree;

      expect(TestBed.inject(Router).serializeUrl(result)).toBe('/log-in');
      expect(dialog.showDialog).toHaveBeenCalledWith(jasmine.objectContaining({ title: '請先登入會員', mode: 'errorMode' }));
    });
  });

  describe('canDeactivateGuard', () => {
    /**
     * 以指定元件執行離開守衛
     * @param component 目前頁面元件
     * @returns 守衛結果
     */
    const leave = (component: CanComponentDeactivate | null) =>
      TestBed.runInInjectionContext(() =>
        canDeactivateGuard(component as CanComponentDeactivate, {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot, {} as RouterStateSnapshot));

    it('元件有 canDeactivate 時以它的結果決定能否離開', () => {
      expect(leave({ canDeactivate: () => false })).toBeFalse();
      expect(leave({ canDeactivate: () => true })).toBeTrue();
    });

    it('元件沒有實作 canDeactivate 或元件不存在時直接放行', () => {
      expect(leave({} as CanComponentDeactivate)).toBeTrue();
      expect(leave(null)).toBeTrue();
    });
  });

  describe('路由表', () => {
    /**
     * 依路徑找出路由設定
     * @param path 路由路徑
     * @returns 路由設定
     */
    const route = (path: string) => routes.find((r) => r.path === path)!;

    it('需要登入的頁面都掛上 authGuard；公開頁面沒有', () => {
      ['survey-design', 'survey-management', 'user-management', 'user-profile'].forEach((path) =>
        expect(route(path).canActivate).toEqual([authGuard]));
      ['home', 'lobby', 'survey-runner', 'log-in', 'register', 'forget-password', 'reset-password', 'verify-email'].forEach((path) =>
        expect(route(path).canActivate).toBeUndefined());
    });

    it('有編輯內容的頁面都掛上離開確認守衛', () => {
      ['survey-runner', 'survey-design', 'user-profile'].forEach((path) =>
        expect(route(path).canDeactivate).toEqual([canDeactivateGuard]));
    });

    it('根路徑導向 /home，未知網址顯示 not-found；每個頁面都能延遲載入', async () => {
      expect(route('').redirectTo).toBe('/home');
      expect(routes[routes.length - 1].path).toBe('**');

      const loaded = await Promise.all(routes.filter((r) => r.loadComponent).map((r) => r.loadComponent!()));
      loaded.forEach((component) => expect(component).toBeTruthy());
    });
  });
});
