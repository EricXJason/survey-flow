/**
 * ============================================================================
 * 檔案名稱: GlobalExceptionHandler.java
 * 所屬模組: Common 核心例外處置模組 (Exception)
 * 責任描述: 全域攔截 Spring MVC 拋出之商業邏輯、資料庫校驗與安全例外，統一封裝為繁體中文 ApiResponse 回傳
 * 架構分層: Presentation / Exception Layer
 * 聯動關係:
 *   - 上游依賴: 各 Controller、Spring Security 框架或 JPA
 *   - 下游消費: 外部 HTTP 用戶端 (包含 Angular 前端)
 * 邊界處理: 避免向用戶端洩漏內部 SQL 堆疊追蹤，保護系統資安
 * ============================================================================
 */

package com.surveyflow.common.exception;

import com.surveyflow.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 處理 Bean Validation 參數檢驗不符之例外 (400)
     * @param ex 參數校驗例外
     * @return 包含各欄位錯誤詳情的統一回應
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .success(false)
                .message("輸入參數格式驗證不符，請核對輸入內容")
                .data(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 處理身分驗證憑證錯誤 (401)
     * @param ex 密碼或憑證錯誤例外
     * @return 401 統一回應
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "帳號或密碼錯誤，請重新確認"));
    }

    /**
     * 處理存取權限不足之例外 (403)
     * @param ex 授權不足例外
     * @return 403 統一回應
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "權限不足，無法執行此操作"));
    }

    /**
     * 處理帳號尚未完成信箱驗證例外 (403 FORBIDDEN，附帶 status 與 email 供前端啟動重發流程)
     * @param ex 帳號未驗證例外
     * @return 403 包含 ACCOUNT_NOT_VERIFIED 之結構化回應
     */
    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> handleAccountNotVerified(AccountNotVerifiedException ex) {
        java.util.Map<String, String> data = java.util.Map.of(
                "status", "ACCOUNT_NOT_VERIFIED",
                "email", ex.getEmail() != null ? ex.getEmail() : ""
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<java.util.Map<String, String>>builder()
                        .status(HttpStatus.FORBIDDEN.value())
                        .success(false)
                        .message(ex.getMessage())
                        .data(data)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    /**
     * 處理自訂業務違例 (IllegalArgumentException 等)
     * @param ex 非法引數或業務衝突例外
     * @return 400 統一回應
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * 處理系統全域未捕捉之未知例外 (500)
     * @param ex 系統底層未預期例外
     * @return 500 統一回應
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("💥 系統全域未捕捉之未知例外: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "伺服器內部發生未預期錯誤，請稍後再試"));
    }
}
