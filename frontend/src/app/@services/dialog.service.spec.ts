/**
 * ============================================================================
 * 檔案名稱: dialog.service.spec.ts
 * 測試對象: DialogService (確認 / 取消結果、通知模式自動關閉、新對話框取代舊對話框)
 * ============================================================================
 */

import { fakeAsync, tick } from '@angular/core/testing';
import { DialogData, DialogService } from './dialog.service';

describe('DialogService', () => {
  let service: DialogService;
  let shown: (DialogData | null)[];

  beforeEach(() => {
    service = new DialogService();
    shown = [];
    service.dialogState$.subscribe((state) => shown.push(state));
  });

  it('confirm：對話框隱藏 (null) 且訂閱者收到 true 後結束', () => {
    const results: boolean[] = [];
    let completed = false;
    service.showDialog({ message: '確定？', mode: 'checkMode' }).subscribe({
      next: (value) => results.push(value),
      complete: () => (completed = true),
    });

    service.confirm();

    expect(shown).toEqual([{ message: '確定？', mode: 'checkMode' }, null]);
    expect(results).toEqual([true]);
    expect(completed).toBeTrue();
  });

  it('cancel：訂閱者收到 false', () => {
    const results: boolean[] = [];
    service.showDialog({ message: '確定？', mode: 'checkMode' }).subscribe((value) => results.push(value));

    service.cancel();

    expect(results).toEqual([false]);
  });

  it('通知模式：預設 3 秒後自動視為確認', fakeAsync(() => {
    const results: boolean[] = [];
    service.showDialog({ message: '成功', mode: 'noticeMode' }).subscribe((value) => results.push(value));

    tick(2999);
    expect(results).toEqual([]);
    tick(1);
    expect(results).toEqual([true]);
  }));

  it('通知模式：可自訂自動關閉時間；手動關閉後計時器不再觸發', fakeAsync(() => {
    const results: boolean[] = [];
    service.showDialog({ message: '成功', mode: 'noticeMode', autoCloseDuration: 500 }).subscribe((value) => results.push(value));

    service.cancel();
    tick(1000);

    expect(results).toEqual([false]);
  }));

  it('開新對話框時，前一個對話框的訂閱者收到 false', fakeAsync(() => {
    const first: boolean[] = [];
    const second: boolean[] = [];
    service.showDialog({ message: '一', mode: 'noticeMode' }).subscribe((value) => first.push(value));
    service.showDialog({ message: '二', mode: 'checkMode' }).subscribe((value) => second.push(value));

    tick(5000);
    service.confirm();

    expect(first).toEqual([false]);
    expect(second).toEqual([true]);
  }));

  it('沒有開啟中的對話框時呼叫 confirm 不會出錯', () => {
    expect(() => service.confirm()).not.toThrow();
    expect(shown).toEqual([null]);
  });
});
