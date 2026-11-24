/**
 * ============================================================================
 * 檔案名稱: audit-log-panel.component.spec.ts
 * 測試對象: AuditLogPanelComponent (載入事件選單與第一頁、篩選與分頁參數、錯誤狀態、雜湊鏈驗證結果、顯示文字轉換)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { throwError } from 'rxjs';
import { createApiClientSpy, ok } from '../../../../../testing/test-doubles';
import { ApiClientService } from '../../../../@services/api-client.service';
import { AuditLogEntry } from '../../../../@interfaces/audit-log-model';
import { AuditLogPanelComponent } from './audit-log-panel.component';

/** 一筆稽核紀錄 (欄位與後端 AuditLogResponse 相同) */
const ENTRY: AuditLogEntry = {
  id: 9, occurredAt: '2026-11-21T10:00:00.123', actor: 'admin', action: 'USER_STATUS_CHANGED', actionLabel: '變更會員狀態',
  targetType: 'USER', targetId: '3', detail: 'bob → SUSPENDED', clientIp: '203.0.113.5',
  traceId: '65d2c9f3995a497cbbd94b0fb28a4132', hash: 'a'.repeat(64),
};

describe('AuditLogPanelComponent', () => {
  let component: AuditLogPanelComponent;
  let api: jasmine.SpyObj<ApiClientService>;
  let host: HTMLElement;

  /**
   * 依網址回傳對應之假回應：/actions → 事件選單、/verification → 驗證結果、其餘 → 一頁紀錄
   * @param url 請求網址
   * @returns 假回應
   */
  const respond = (url: string) => {
    if (url.endsWith('/actions')) {
      return ok([{ code: 'LOGIN_FAILED', label: '登入失敗' }]);
    }
    if (url.endsWith('/verification')) {
      return ok({ valid: false, checkedCount: 4, brokenAtId: 2, message: '第 2 筆內容與雜湊不相符', verifiedAt: '2026-11-21T10:01:00' });
    }
    return ok({ content: [ENTRY], totalElements: 41 });
  };

  /**
   * 建立元件並完成第一次渲染
   */
  async function create(): Promise<void> {
    const fixture = TestBed.createComponent(AuditLogPanelComponent);
    component = fixture.componentInstance;
    host = fixture.nativeElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    api = createApiClientSpy();
    api.get.and.callFake(((url: string) => respond(url)) as never);
    await TestBed.configureTestingModule({
      imports: [AuditLogPanelComponent],
      providers: [provideNoopAnimations(), { provide: ApiClientService, useValue: api }],
    }).compileComponents();
  });

  it('初始化：載入事件選單與第一頁 (page=0、size=20)，表格顯示事件名稱與縮短之追蹤碼', async () => {
    await create();

    expect(api.get).toHaveBeenCalledWith('/api/admin/audit-logs/actions');
    expect(api.get).toHaveBeenCalledWith('/api/admin/audit-logs?page=0&size=20');
    expect(component.actionOptions()).toEqual([{ code: 'LOGIN_FAILED', label: '登入失敗' }]);
    expect(component.totalCount()).toBe(41);
    expect(host.textContent).toContain('變更會員狀態');
    expect(host.textContent).toContain('65d2c9f3');
  });

  it('套用篩選：操作者去除空白、帶入事件代碼並回到第一頁；清除條件後不帶篩選參數', async () => {
    await create();
    component.pageIndex.set(3);
    component.onActorInput({ target: { value: ' admin ' } } as unknown as Event);
    component.onActionChange({ target: { value: 'LOGIN_FAILED' } } as unknown as Event);

    expect(api.get).toHaveBeenCalledWith('/api/admin/audit-logs?page=0&size=20&actor=admin&action=LOGIN_FAILED');

    component.clearFilters();
    expect(api.get.calls.mostRecent().args[0]).toBe('/api/admin/audit-logs?page=0&size=20');
  });

  it('換頁與改變每頁筆數：以新的頁碼與筆數查詢', async () => {
    await create();

    component.onPage({ pageIndex: 2, pageSize: 50, length: 41 });

    expect(api.get.calls.mostRecent().args[0]).toBe('/api/admin/audit-logs?page=2&size=50');
  });

  it('查詢失敗：清空清單並顯示後端訊息；事件選單載入失敗時為空', async () => {
    api.get.and.returnValue(throwError(() => ({ status: 500 })));
    api.resolveErrorMessage.and.returnValue('伺服器內部發生異常（追蹤碼：abc）');

    await create();

    expect(component.entries()).toEqual([]);
    expect(component.actionOptions()).toEqual([]);
    expect(component.loadError()).toBe('伺服器內部發生異常（追蹤碼：abc）');
    expect(host.textContent).toContain('無法載入稽核日誌');
  });

  it('驗證完整性：顯示結果；請求失敗時顯示錯誤訊息並清除舊結果', async () => {
    await create();

    component.verifyChain();
    expect(component.verification()?.brokenAtId).toBe(2);
    expect(component.isVerifying()).toBeFalse();

    api.get.and.returnValue(throwError(() => ({ status: 503 })));
    component.verifyChain();
    expect(component.verification()).toBeNull();
    expect(component.verifyError()).toBe('目前無法驗證稽核日誌，請稍後再試。');
  });

  it('顯示文字：對象類型轉為中文 (未知類型原樣、沒有 ID 只顯示類型、沒有對象為「—」)；沒有追蹤碼為「—」', async () => {
    await create();

    expect(component.getTargetLabel(ENTRY)).toBe('會員 #3');
    expect(component.getTargetLabel({ ...ENTRY, targetType: 'SURVEY', targetId: null })).toBe('問卷');
    expect(component.getTargetLabel({ ...ENTRY, targetType: 'OTHER' })).toBe('OTHER #3');
    expect(component.getTargetLabel({ ...ENTRY, targetType: null })).toBe('—');
    expect(component.getShortTraceId(null)).toBe('—');
  });
});
