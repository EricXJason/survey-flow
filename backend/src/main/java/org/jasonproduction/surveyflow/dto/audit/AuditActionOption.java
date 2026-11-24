/**
 * ============================================================================
 * 檔案名稱: AuditActionOption.java
 * 所屬模組: DTO 資料傳輸層 - 稽核模組
 * 責任描述: 稽核事件類型選項 (代碼 + 繁中名稱)，供前端篩選選單使用，避免前端另存一份事件清單
 * 聯動關係:
 *   - 上游依賴: AuditAction
 *   - 下游消費: AuditLogController、前端稽核日誌頁
 * ============================================================================
 */

package org.jasonproduction.surveyflow.dto.audit;

/**
 * @param code 事件代碼
 * @param label 繁中名稱
 */
public record AuditActionOption(String code, String label) {
}
