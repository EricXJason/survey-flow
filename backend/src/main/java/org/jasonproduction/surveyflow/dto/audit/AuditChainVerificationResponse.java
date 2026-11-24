/**
 * ============================================================================
 * 檔案名稱: AuditChainVerificationResponse.java
 * 所屬模組: DTO 資料傳輸層 - 稽核模組
 * 責任描述: 稽核雜湊鏈驗證結果：是否完整、檢查筆數、第一個斷點與原因
 * 聯動關係:
 *   - 上游依賴: AuditLogService.verifyChain
 *   - 下游消費: AuditLogController、前端稽核日誌頁「驗證完整性」
 * ============================================================================
 */

package org.jasonproduction.surveyflow.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class AuditChainVerificationResponse {

    /** true 表示整條鏈完整無竄改 */
    private boolean valid;
    /** 已檢查之紀錄筆數 */
    private long checkedCount;
    /** 第一筆驗證失敗之紀錄 ID；完整時為 null */
    private Long brokenAtId;
    /** 驗證結果說明 (繁中) */
    private String message;
    /** 驗證完成時間 */
    private LocalDateTime verifiedAt;
}
