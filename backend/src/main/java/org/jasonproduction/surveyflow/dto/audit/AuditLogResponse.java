/**
 * ============================================================================
 * 檔案名稱: AuditLogResponse.java
 * 所屬模組: DTO 資料傳輸層 - 稽核模組
 * 責任描述: 管理員稽核日誌清單之單筆回應 (含事件繁中名稱與雜湊值)
 * 聯動關係:
 *   - 上游依賴: AuditLog 實體、AuditAction
 *   - 下游消費: AuditLogController、前端稽核日誌頁
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
public class AuditLogResponse {

    private Long id;
    private LocalDateTime occurredAt;
    private String actor;
    /** 事件代碼 (AuditAction 名稱) */
    private String action;
    /** 事件繁中名稱 */
    private String actionLabel;
    private String targetType;
    private String targetId;
    private String detail;
    private String clientIp;
    private String traceId;
    /** 本筆 HMAC-SHA256 雜湊 (64 字元十六進位) */
    private String hash;
}
