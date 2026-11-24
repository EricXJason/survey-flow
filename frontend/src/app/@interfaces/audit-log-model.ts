/**
 * ============================================================================
 * 檔案名稱: audit-log-model.ts
 * 責任: 稽核日誌之前端型別，欄位與後端 AuditLogResponse、AuditActionOption、AuditChainVerificationResponse 一致
 * 聯動: 後端 /api/admin/audit-logs* 回應 → 本檔 → AuditLogPanelComponent
 * ============================================================================
 */

/** 單筆稽核紀錄 */
export interface AuditLogEntry {
  id: number;
  occurredAt: string;
  actor: string | null;
  action: string;
  actionLabel: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  clientIp: string | null;
  traceId: string | null;
  hash: string;
}

/** 稽核事件類型選項 (篩選選單) */
export interface AuditActionOption {
  code: string;
  label: string;
}

/** 雜湊鏈完整性驗證結果 */
export interface AuditChainVerification {
  valid: boolean;
  checkedCount: number;
  brokenAtId: number | null;
  message: string;
  verifiedAt: string;
}

/** 後端 Spring Data 分頁回應中本頁面使用到之欄位 */
export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
}
