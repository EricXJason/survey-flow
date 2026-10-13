/**
 * ============================================================================
 * 檔案名稱: dialog.service.ts
 * 所屬模組: 全域對話框與互動彈窗服務模組 (Dialog Service Module)
 * 責任描述: 集中管控全站彈出式視窗狀態（通知 noticeMode、雙鈕確認 checkMode、錯誤警報 errorMode），提供非同步布林結果回呼與自動關閉計時。
 * 架構分層: Service Layer / Interaction Service
 * 聯動關係:
 *   - 上游依賴: rxjs (Observable, Subject)
 *   - 下游消費: DialogComponent (視圖渲染), UserEmitterService, 各頁面組件與路由守衛
 * 邊界處理: 重複呼叫時自動關閉前一對話框並派發 false；銷毀或新開彈窗時安全清理計時器防記憶體洩漏
 * ============================================================================
 */

import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/** 對話框顯示模式：確認模式、錯誤模式、通知模式 */
export type DialogMode = 'checkMode' | 'errorMode' | 'noticeMode';

/** 對話框資料模型 */
export interface DialogData {
  title?: string;
  message: string;
  mode?: DialogMode;
  confirmText?: string;
  cancelText?: string;
  errorText?: string;
  showCloseButton?: boolean;
  autoCloseDuration?: number;
}

/**
 * 全域對話框服務 (DialogService)
 * 統一管理系統對話框狀態、結果回傳與自動關閉計時器。
 */
@Injectable({
  providedIn: 'root',
})
export class DialogService {
  private readonly _dialogState$ = new Subject<DialogData | null>();
  public readonly dialogState$ = this._dialogState$.asObservable();

  private _resultState$: Subject<boolean> | null = null;
  private _timer: any;

  /**
   * 顯示對話框並回傳使用者確認結果 Observable
   * @param data 對話框設定資料物件（包含訊息、標題、模式與自訂按鈕文字）
   * @returns Observable<boolean> 使用者點選確認(true)或取消/關閉(false)之非同步串流
   */
  public showDialog(data: DialogData): Observable<boolean> {
    this.clearTimer();

    if (this._resultState$ && !this._resultState$.closed) {
      this._resultState$.next(false);
      this._resultState$.complete();
    }

    const currentSubject = new Subject<boolean>();
    this._resultState$ = currentSubject;
    this._dialogState$.next(data);

    if (data.mode === 'noticeMode') {
      const duration = data.autoCloseDuration ?? 3000;
      this._timer = setTimeout(() => {
        this.confirm();
      }, duration);
    }

    return currentSubject.asObservable();
  }

  /**
   * 使用者點選確認
   */
  public confirm(): void {
    this.closeWithResult(true);
  }

  /**
   * 使用者點選取消
   */
  public cancel(): void {
    this.closeWithResult(false);
  }

  /**
   * 關閉對話框並發送布林結果
   */
  private closeWithResult(result: boolean): void {
    this.clearTimer();

    const targetSubject = this._resultState$;
    this._resultState$ = null;

    this._dialogState$.next(null);

    if (targetSubject && !targetSubject.closed) {
      targetSubject.next(result);
      targetSubject.complete();
    }
  }

  /**
   * 清除自動關閉計時器
   */
  private clearTimer(): void {
    if (this._timer) {
      clearTimeout(this._timer);
      this._timer = null;
    }
  }
}
