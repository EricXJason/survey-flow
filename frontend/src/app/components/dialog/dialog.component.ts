/**
 * ============================================================================
 * 檔案名稱: dialog.component.ts
 * 所屬模組: 全域彈出視窗組件模組 (Modal Dialog Module)
 * 責任描述: 負責訂閱 DialogService 串流並呈現 Notice、Check、Error 等三種互動對話框，處理無障礙 ESC 關閉與按鈕點擊反饋。
 * 架構分層: Presentation Layer / Shared Modal Component
 * 聯動關係:
 *   - 上游依賴: ../../@services/dialog.service
 *   - 下游消費: AppComponent 宿主直接調用
 * 邊界處理: 鍵盤 ESC 快捷鍵關閉；按鈕文字動態防呆回退支援
 * ============================================================================
 */

import { Component, HostListener, inject } from '@angular/core';
import { DialogService, DialogData } from '../../@services/dialog.service';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';

/**
 * 全域互動式對話框組件 (Dialog Component)
 * 負責渲染 Notice (通知)、Check (雙鈕確認)、Error (錯誤警報) 等三種不同情境的視窗對話框，
 * 支援無障礙鍵盤 ESC 關閉、高對比主題及自訂按鈕文字。
 */
@Component({
  selector: 'app-dialog',
  imports: [CommonModule],
  templateUrl: './dialog.component.html',
  styleUrl: './dialog.component.scss',
})
export class DialogComponent {
  /** 注入對話框管理服務，控制開關與按鈕回饋 */
  protected readonly dialogService = inject(DialogService);

  /** 對話框當前狀態與資料之 RxJS Observable 串流 */
  protected readonly dialogState$: Observable<DialogData | null> =
    this.dialogService.dialogState$;

  /**
   * 監聽鍵盤 ESC 按鍵事件 (window:keydown)
   * 當使用者在對話框開啟期間按下 ESC，自動執行取消/關閉對話框，符合 WCAG 無障礙規範。
   * @param event 鍵盤按鍵事件
   */
  @HostListener('window:keydown', ['$event'])
  protected handleEscape(event: KeyboardEvent): void {
    if (event.key === 'Escape' || event.key === 'Esc') {
      event.preventDefault();
      this.dialogService.cancel();
    }
  }

  /**
   * 解析確認按鈕（主要操作）的顯示文字
   * 若對話框資料有設定 confirmText 則優先採用；noticeMode 預設為「我知道了」，其餘為「確認」。
   * @param data 對話框設定資料物件
   * @returns 確認按鈕文字字串
   */
  protected getConfirmText(data: DialogData): string {
    return data.confirmText?.trim() || (data.mode === 'noticeMode' ? '我知道了' : '確認');
  }

  /**
   * 解析取消按鈕（次要操作）的顯示文字
   * 優先採用 cancelText 或 errorText，若未設定則回退為「取消」。
   * @param data 對話框設定資料物件
   * @returns 取消按鈕文字字串
   */
  protected getCancelText(data: DialogData): string {
    return data.cancelText?.trim() || data.errorText?.trim() || '取消';
  }

  /**
   * 解析錯誤模式 (errorMode) 下操作按鈕的顯示文字
   * 依序核對 errorText、confirmText、cancelText，若無設定則預設為「了解」。
   * @param data 對話框設定資料物件
   * @returns 錯誤按鈕文字字串
   */
  protected getErrorButtonText(data: DialogData): string {
    return (
      data.errorText?.trim() ||
      data.confirmText?.trim() ||
      data.cancelText?.trim() ||
      '了解'
    );
  }

  /**
   * 格式化對話框內文訊息，確保每個錯誤或提示項目獨立分行，徹底避免黏在同一行
   * 自動攔截 raw 英文 Http failure response，100% 轉譯為臺灣繁體中文友好訊息
   * @param message 原始對話框內文字串
   * @returns 轉換後之結構化 HTML 字串
   */
  protected formatMessage(message?: string): string {
    if (!message) return '';

    // 攔截並轉譯 raw 英文 HTTP 錯誤代碼 (終極防線)
    if (message.includes('Http failure response') || message.includes('status: 403') || message.endsWith(': 403')) {
      message = '存取權限不足或登入憑證已失效，請確認帳號權限或重新登入。';
    } else if (message.includes('status: 401') || message.endsWith(': 401')) {
      message = '尚未登入或認證資訊已失效，請重新登入後再試。';
    } else if (message.includes('status: 404') || message.endsWith(': 404')) {
      message = '找不到所請求的伺服器資源或目標問卷不存在。';
    } else if (message.includes('status: 500') || message.endsWith(': 500')) {
      message = '伺服器內部發生異常，請稍後再試或聯繫系統管理員。';
    } else if (message.includes('status: 0') || message.endsWith(': 0')) {
      message = '無法連線至伺服器，請確認後端服務是否已啟動或檢查網路連線。';
    }

    // 若訊息包含換行符或條目符號，拆分為清單呈現
    const lines = message.split('\n').map((l) => l.trim()).filter((l) => l.length > 0);
    if (lines.length > 1 || lines.some((l) => l.startsWith('•') || l.startsWith('·') || l.startsWith('-'))) {
      const itemsHtml = lines
        .map((line) => {
          // 清除開頭的小圓點或破折號，以統一之結構化樣式呈現
          const cleanText = line.replace(/^[•·\-\*]\s*/, '');
          return `<div class="dialog-msg-item"><span class="msg-bullet">•</span><span class="msg-content">${cleanText}</span></div>`;
        })
        .join('');
      return `<div class="dialog-msg-list">${itemsHtml}</div>`;
    }

    return `<div class="dialog-msg-single">${message.replace(/\n/g, '<br>')}</div>`;
  }
}
