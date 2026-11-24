/**
 * ============================================================================
 * 檔案名稱: test-doubles.ts
 * 責任描述: 元件測試共用的替身 (Test Double) 工廠：後端成功回應、ApiClientService / DialogService / Router 的 Spy
 * 聯動關係: 只被 *.spec.ts 匯入；不在 tsconfig.app.json 的編譯範圍內，不會打包進正式網站
 * ============================================================================
 */

import { Observable, of } from 'rxjs';
import { Router } from '@angular/router';
import { ApiClientService, ApiResponse } from '../app/@services/api-client.service';
import { DialogService } from '../app/@services/dialog.service';

/**
 * 包成後端成功回應 (ApiResponse) 的同步 Observable
 * @param data 回應資料
 * @returns 立即發出成功回應的 Observable
 */
export function ok<T>(data: T = null as T): Observable<ApiResponse<T>> {
  return of({ status: 200, success: true, message: '', data, timestamp: '' });
}

/**
 * 建立 ApiClientService 的 Spy；resolveErrorMessage 預設回傳呼叫端提供的預設訊息
 * @returns ApiClientService Spy
 */
export function createApiClientSpy(): jasmine.SpyObj<ApiClientService> {
  const api = jasmine.createSpyObj<ApiClientService>('ApiClientService', ['get', 'post', 'put', 'patch', 'delete', 'resolveErrorMessage']);
  api.resolveErrorMessage.and.callFake((_err: unknown, fallback: string) => fallback);
  return api;
}

/**
 * 建立 DialogService 的 Spy；showDialog 預設回傳「使用者按下確認 (true)」
 * @param confirmed 使用者在對話框的選擇
 * @returns DialogService Spy
 */
export function createDialogSpy(confirmed = true): jasmine.SpyObj<DialogService> {
  const dialog = jasmine.createSpyObj<DialogService>('DialogService', ['showDialog', 'confirm', 'cancel']);
  dialog.showDialog.and.returnValue(of(confirmed));
  return dialog;
}

/**
 * 建立 Router 的 Spy (navigate / navigateByUrl 回傳成功的 Promise)
 * @returns Router Spy
 */
export function createRouterSpy(): jasmine.SpyObj<Router> {
  const router = jasmine.createSpyObj<Router>('Router', ['navigate', 'navigateByUrl']);
  router.navigate.and.resolveTo(true);
  router.navigateByUrl.and.resolveTo(true);
  return router;
}

/**
 * 取出最後一次對話框的設定 (標題、訊息、模式)
 * @param dialog DialogService Spy
 * @returns 最後一次 showDialog 的參數
 */
export function lastDialog(dialog: jasmine.SpyObj<DialogService>) {
  return dialog.showDialog.calls.mostRecent().args[0];
}
