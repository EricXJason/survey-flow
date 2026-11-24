/**
 * ============================================================================
 * 檔案名稱: AuditLogController.java
 * 所屬模組: Controller 控制器層 - 稽核日誌 (Admin 專屬)
 * 責任描述: 提供管理員查詢稽核日誌、取得事件類型清單與驗證雜湊鏈完整性之端點
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: AuditLogService
 *   - 下游消費: 前端 AuditLogComponent (稽核日誌頁)
 * 邊界處理: URL 規則 (/api/admin/**) 與類別層級 @PreAuthorize 雙重限定 ROLE_ADMIN；只有讀取端點，沒有修改或刪除
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import lombok.RequiredArgsConstructor;
import org.jasonproduction.surveyflow.common.ApiResponse;
import org.jasonproduction.surveyflow.dto.audit.AuditActionOption;
import org.jasonproduction.surveyflow.dto.audit.AuditChainVerificationResponse;
import org.jasonproduction.surveyflow.dto.audit.AuditLogResponse;
import org.jasonproduction.surveyflow.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 稽核日誌條件分頁查詢 (由新到舊)
     * @param actor 操作者帳號 (完全比對，選填)
     * @param action 事件代碼 (選填)
     * @param pageable 分頁參數 (預設每頁 20 筆)
     * @return 稽核紀錄分頁
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> searchLogs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.searchLogs(actor, action, pageable), "成功取得稽核日誌"));
    }

    /**
     * 取得全部事件類型 (代碼與繁中名稱)，供篩選選單
     * @return 事件類型清單
     */
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<AuditActionOption>>> listActions() {
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.listActions(), "成功取得稽核事件類型"));
    }

    /**
     * 驗證整條稽核雜湊鏈是否完整 (唯讀，不改變任何資料)
     * @return 驗證結果
     */
    @GetMapping("/verification")
    public ResponseEntity<ApiResponse<AuditChainVerificationResponse>> verifyChain() {
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.verifyChain(), "已完成稽核日誌完整性驗證"));
    }
}
