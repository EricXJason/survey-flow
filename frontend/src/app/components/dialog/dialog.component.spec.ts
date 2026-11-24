/**
 * ============================================================================
 * 檔案名稱: dialog.component.spec.ts
 * 測試對象: DialogComponent (三種模式的按鈕、Esc 與背景關閉、按鈕文字預設值、訊息格式化)
 * ============================================================================
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogData, DialogService } from '../../@services/dialog.service';
import { DialogComponent } from './dialog.component';

describe('DialogComponent', () => {
  let fixture: ComponentFixture<DialogComponent>;
  let dialogService: DialogService;
  let results: boolean[];

  /**
   * 開啟對話框並更新畫面
   * @param data 對話框設定
   */
  const open = (data: DialogData): void => {
    dialogService.showDialog(data).subscribe((value) => results.push(value));
    fixture.detectChanges();
  };

  /**
   * 依 CSS 選擇器取得元素
   * @param selector CSS 選擇器
   * @returns 元素，找不到時為 null
   */
  const query = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DialogComponent] }).compileComponents();
    dialogService = TestBed.inject(DialogService);
    fixture = TestBed.createComponent(DialogComponent);
    results = [];
    fixture.detectChanges();
  });

  it('沒有對話框時不渲染任何內容', () => {
    expect(query('.dialog-overlay')).toBeNull();
  });

  it('確認模式：顯示取消與確認，點確認回傳 true 並關閉', () => {
    open({ title: '刪除？', message: '確定刪除', mode: 'checkMode', confirmText: '刪除' });

    expect(query('#dialog-title')!.textContent!.trim()).toBe('刪除？');
    expect(query('.btn-cancel')!.textContent!.trim()).toBe('取消');
    query('.btn-check')!.click();
    fixture.detectChanges();

    expect(results).toEqual([true]);
    expect(query('.dialog-overlay')).toBeNull();
  });

  it('錯誤模式：只有一顆按鈕，點擊視為取消；沒有標題時顯示預設標題', () => {
    open({ message: '失敗', mode: 'errorMode' });

    expect(query('#dialog-title')!.textContent!.trim()).toBe('提示訊息');
    expect(query('.btn-error')!.textContent!.trim()).toBe('了解');
    query('.btn-error')!.click();

    expect(results).toEqual([false]);
  });

  it('通知模式：按鈕預設「我知道了」，點擊視為確認', () => {
    open({ message: '成功', mode: 'noticeMode' });

    expect(query('#dialog-title')!.textContent!.trim()).toBe('系統通知');
    query('.btn-notice')!.click();

    expect(results).toEqual([true]);
  });

  it('點背景或叉叉視為取消；點對話框本身不會關閉', () => {
    open({ message: 'A', mode: 'checkMode' });
    query('.dialog-card')!.click();
    expect(results).toEqual([]);

    query('.dialog-overlay')!.click();
    expect(results).toEqual([false]);

    fixture.detectChanges();
    open({ message: 'B', mode: 'checkMode' });
    query('.btn-close')!.click();
    expect(results).toEqual([false, false]);
  });

  it('按下 Esc 視為取消', () => {
    open({ message: 'A' });

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Esc' }));

    expect(results).toEqual([false]);
  });

  describe('按鈕文字與訊息格式', () => {
    let component: any;

    beforeEach(() => (component = fixture.componentInstance));

    it('按鈕文字優先使用自訂文字，空白時使用預設', () => {
      expect(component.getConfirmText({ message: '', confirmText: ' 好 ' })).toBe('好');
      expect(component.getConfirmText({ message: '', confirmText: ' ' })).toBe('確認');
      expect(component.getCancelText({ message: '', errorText: '返回' })).toBe('返回');
      expect(component.getErrorButtonText({ message: '', confirmText: '確定' })).toBe('確定');
      expect(component.getErrorButtonText({ message: '', cancelText: '關閉' })).toBe('關閉');
    });

    it('原始英文 HTTP 錯誤轉成繁中訊息', () => {
      expect(component.formatMessage('Http failure response for /x: 403')).toContain('權限不足');
      expect(component.formatMessage('error status: 401')).toContain('重新登入');
      expect(component.formatMessage('failed: 404')).toContain('找不到');
      expect(component.formatMessage('failed status: 500')).toContain('伺服器內部');
      expect(component.formatMessage('failed: 0')).toContain('無法連線');
    });

    it('多行或條列訊息拆成清單；單行訊息直接顯示；空訊息為空字串', () => {
      expect(component.formatMessage('')).toBe('');
      expect(component.formatMessage('單行')).toBe('<div class="dialog-msg-single">單行</div>');
      const list: string = component.formatMessage('• 標題必填\n- 至少一題');
      expect(list).toContain('dialog-msg-list');
      expect(list.match(/dialog-msg-item/g)!.length).toBe(2);
      expect(list).toContain('<span class="msg-content">至少一題</span>');
      expect(component.formatMessage('· 只有一項')).toContain('dialog-msg-list');
    });
  });
});
