/**
 * ============================================================================
 * 檔案名稱: audit-log-panel.component.ts
 * 責任: 管理員稽核日誌面板：依操作者與事件類型查詢 (後端分頁)、檢視每筆來源 IP 與追蹤碼、驗證雜湊鏈完整性
 * 聯動: ApiClientService → GET /api/admin/audit-logs、/actions、/verification → 本元件 → 會員管理頁「稽核日誌」分頁
 * 邊界: 只讀不寫 (後端也沒有修改或刪除端點)；僅在會員管理頁 (已限定管理員) 中顯示。
 *       查詢失敗時顯示伺服器錯誤區塊，不以本地假資料替代
 * ============================================================================
 */

import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { ApiClientService } from '../../../../@services/api-client.service';
import {
  AuditActionOption,
  AuditChainVerification,
  AuditLogEntry,
  AuditLogPage,
} from '../../../../@interfaces/audit-log-model';

/** 後端稽核端點根路徑 */
const AUDIT_ENDPOINT = '/api/admin/audit-logs';

/** 操作對象類型之顯示名稱 */
const TARGET_TYPE_LABELS: Record<string, string> = {
  USER: '會員',
  SURVEY: '問卷',
};

@Component({
  selector: 'app-audit-log-panel',
  standalone: true,
  imports: [DatePipe, MatTableModule, MatPaginatorModule],
  templateUrl: './audit-log-panel.component.html',
  styleUrl: './audit-log-panel.component.scss',
})
export class AuditLogPanelComponent implements OnInit {
  private readonly _apiClient = inject(ApiClientService);

  /** 表格欄位 */
  public readonly displayedColumns = ['occurredAt', 'actor', 'action', 'target', 'detail', 'source'];

  /** 每頁筆數選項 */
  public readonly pageSizeOptions = [20, 50, 100];

  /** 目前頁面之稽核紀錄 */
  public readonly entries = signal<AuditLogEntry[]>([]);
  /** 符合條件之總筆數 (分頁器使用) */
  public readonly totalCount = signal<number>(0);
  /** 目前頁碼 (從 0 開始) */
  public readonly pageIndex = signal<number>(0);
  /** 目前每頁筆數 */
  public readonly pageSize = signal<number>(20);
  /** 事件類型選項 */
  public readonly actionOptions = signal<AuditActionOption[]>([]);
  /** 操作者篩選輸入值 */
  public readonly actorFilter = signal<string>('');
  /** 事件類型篩選值 (空字串為全部) */
  public readonly actionFilter = signal<string>('');
  /** 是否載入中 */
  public readonly isLoading = signal<boolean>(true);
  /** 查詢失敗時之錯誤訊息 (null 表示正常) */
  public readonly loadError = signal<string | null>(null);
  /** 是否正在驗證雜湊鏈 */
  public readonly isVerifying = signal<boolean>(false);
  /** 最近一次驗證結果 (尚未驗證為 null) */
  public readonly verification = signal<AuditChainVerification | null>(null);
  /** 驗證請求失敗時之錯誤訊息 */
  public readonly verifyError = signal<string | null>(null);

  /**
   * 初始化：載入事件類型選單與第一頁紀錄
   */
  public ngOnInit(): void {
    this._apiClient.get<AuditActionOption[]>(`${AUDIT_ENDPOINT}/actions`).subscribe({
      next: (res) => this.actionOptions.set(res?.data ?? []),
      error: () => this.actionOptions.set([]),
    });
    this.loadPage();
  }

  /**
   * 依目前篩選條件與頁碼查詢稽核紀錄 (GET /api/admin/audit-logs?page=&size=&actor=&action=)
   */
  public loadPage(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    const params = new URLSearchParams({ page: String(this.pageIndex()), size: String(this.pageSize()) });
    if (this.actorFilter().trim()) {
      params.set('actor', this.actorFilter().trim());
    }
    if (this.actionFilter()) {
      params.set('action', this.actionFilter());
    }
    this._apiClient.get<AuditLogPage>(`${AUDIT_ENDPOINT}?${params.toString()}`).subscribe({
      next: (res) => {
        this.entries.set(res?.data?.content ?? []);
        this.totalCount.set(res?.data?.totalElements ?? 0);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.entries.set([]);
        this.totalCount.set(0);
        this.loadError.set(this._apiClient.resolveErrorMessage(err, '目前無法載入稽核日誌，請稍後再試。'));
        this.isLoading.set(false);
      },
    });
  }

  /**
   * 套用篩選條件：回到第一頁重新查詢
   */
  public applyFilters(): void {
    this.pageIndex.set(0);
    this.loadPage();
  }

  /**
   * 清除篩選條件並重新查詢第一頁
   */
  public clearFilters(): void {
    this.actorFilter.set('');
    this.actionFilter.set('');
    this.applyFilters();
  }

  /**
   * 操作者輸入框內容變更
   * @param event input 事件
   */
  public onActorInput(event: Event): void {
    this.actorFilter.set((event.target as HTMLInputElement).value);
  }

  /**
   * 事件類型選單變更：立即重新查詢
   * @param event change 事件
   */
  public onActionChange(event: Event): void {
    this.actionFilter.set((event.target as HTMLSelectElement).value);
    this.applyFilters();
  }

  /**
   * 分頁器換頁或改變每頁筆數
   * @param event 分頁事件
   */
  public onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadPage();
  }

  /**
   * 驗證整條雜湊鏈 (GET /api/admin/audit-logs/verification)，結果顯示於工具列下方
   */
  public verifyChain(): void {
    this.isVerifying.set(true);
    this.verifyError.set(null);
    this._apiClient.get<AuditChainVerification>(`${AUDIT_ENDPOINT}/verification`).subscribe({
      next: (res) => {
        this.verification.set(res?.data ?? null);
        this.isVerifying.set(false);
      },
      error: (err) => {
        this.verification.set(null);
        this.verifyError.set(this._apiClient.resolveErrorMessage(err, '目前無法驗證稽核日誌，請稍後再試。'));
        this.isVerifying.set(false);
      },
    });
  }

  /**
   * 組出「對象」欄文字，如「問卷 #12」；沒有對象時回傳「—」
   * @param entry 稽核紀錄
   * @returns 顯示文字
   */
  public getTargetLabel(entry: AuditLogEntry): string {
    if (!entry.targetType) {
      return '—';
    }
    const typeLabel = TARGET_TYPE_LABELS[entry.targetType] ?? entry.targetType;
    return entry.targetId ? `${typeLabel} #${entry.targetId}` : typeLabel;
  }

  /**
   * 取得追蹤碼前 8 碼 (完整值放在 title 提示)；沒有追蹤碼時回傳「—」
   * @param traceId 追蹤碼
   * @returns 縮短後之追蹤碼
   */
  public getShortTraceId(traceId: string | null): string {
    return traceId ? traceId.slice(0, 8) : '—';
  }
}
