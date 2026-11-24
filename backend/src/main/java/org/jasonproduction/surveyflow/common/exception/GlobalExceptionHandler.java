/**
 * ============================================================================
 * 檔案名稱: GlobalExceptionHandler.java
 * 所屬模組: Common 核心例外處置模組 (資安 S 級：錯誤回應格式)
 * 責任描述: 全系統唯一的錯誤回應出口：把例外轉為 RFC 9457 Problem Details (Content-Type: application/problem+json)：
 *   標準欄位 type (urn:surveyflow:problem:*)、title、status、detail (給使用者看的繁中原因)、instance (請求路徑)；
 *   擴充欄位 code (機器可讀代碼，見 ProblemType)、traceId (對應伺服器日誌)、timestamp，以及依類型附加之 errors / email。
 *   前端反應：
 *   - 400 參數驗證失敗 (errors 為欄位 → 訊息) / 業務規則不符 → 顯示 detail
 *   - 401 未登入、帳密錯誤、Refresh Token 失效 → 已登入請求自動換發；換發失敗清除登入狀態回到訪客
 *   - 403 權限不足；code=ACCOUNT_NOT_VERIFIED 時以 email 導向開通頁
 *   - 404 找不到資源；405 方法不支援
 *   - 409 樂觀鎖版本衝突或唯一欄位重複
 *   - 429 登入鎖定或請求過於頻繁 (後者附 Retry-After 標頭)
 *   - 503 驗證信寄送失敗；500 未預期例外 (只回固定訊息與 traceId，堆疊只寫伺服器日誌)
 * 架構分層: Presentation / Exception Layer
 * 聯動關係:
 *   - 上游依賴: 各 Controller 與 Service 拋出之例外；SecurityConfig 的未認證 / 權限不足處理器與 RateLimitFilter
 *              皆經 HandlerExceptionResolver 轉交本類別，確保過濾鏈內外錯誤格式一致；RequestTraceFilter 寫入之 MDC traceId
 *   - 下游消費: 前端 ApiClientService.resolveErrorMessage、AuthActionService (code / email)
 * 邊界處理: 不向用戶端洩漏 SQL、堆疊或內部類別名稱；500 之 detail 固定，只能憑 traceId 回查日誌
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jasonproduction.surveyflow.common.web.RequestTraceFilter;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailSendException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 擴充欄位名稱：機器可讀代碼 */
    public static final String PROPERTY_CODE = "code";

    /** 擴充欄位名稱：請求追蹤碼 */
    public static final String PROPERTY_TRACE_ID = "traceId";

    /**
     * 處理 Bean Validation 參數檢驗不符之例外 (400)
     * @param ex 參數校驗例外
     * @param request 目前請求 (取得 instance 路徑)
     * @return Problem Details，errors 為「欄位 → 錯誤訊息」
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> errorMessages = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
            if (error.getDefaultMessage() != null) {
                errorMessages.add(error.getDefaultMessage());
            }
        }
        String detail = errorMessages.isEmpty()
                ? "輸入參數格式驗證不符，請核對輸入內容"
                : "輸入參數格式驗證不符: " + String.join(", ", errorMessages);
        ProblemDetail problem = buildProblem(ProblemType.VALIDATION_FAILED, detail, request);
        problem.setProperty("errors", errors);
        return respond(problem);
    }

    /**
     * 處理身分驗證憑證錯誤 (401)：帳密錯誤、Refresh Token 無效或已失效
     * @param ex 憑證錯誤例外
     * @param request 目前請求
     * @return 401 Problem Details
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.UNAUTHENTICATED, ex.getMessage(), request));
    }

    /**
     * 處理未登入存取受保護端點 (401)；由 SecurityConfig 之 authenticationEntryPoint 轉交
     * @param ex 未認證例外
     * @param request 目前請求
     * @return 401 Problem Details
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated(InsufficientAuthenticationException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.UNAUTHENTICATED, "尚未登入或登入憑證已過期", request));
    }

    /**
     * 處理樂觀鎖版本衝突 (409)：前端送出之 version 與資料庫不符，或兩筆交易同時寫入
     * @param ex 樂觀鎖例外
     * @param request 目前請求
     * @return 409 Problem Details
     */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.VERSION_CONFLICT, "此問卷已被其他人修改，請重新整理後再試", request));
    }

    /**
     * 處理資料庫唯一索引衝突 (409)：應用程式「先查再寫」之間有空窗，併發請求由 UNIQUE KEY 擋下
     * @param ex 資料完整性例外
     * @param request 目前請求
     * @return 409 Problem Details (不回傳 SQL 錯誤內容)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("資料完整性衝突 (多為唯一索引重複): {}", ex.getMostSpecificCause().getMessage());
        return respond(buildProblem(ProblemType.DUPLICATE_DATA, "資料已存在 (帳號、信箱或其他唯一欄位重複)，請確認後再試", request));
    }

    /**
     * 處理驗證信寄送失敗 (503)：所有郵件管道皆失敗，交易已回滾
     * @param ex 郵件寄送例外
     * @param request 目前請求
     * @return 503 Problem Details
     */
    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<ProblemDetail> handleMailSend(MailSendException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.MAIL_UNAVAILABLE, ex.getMessage(), request));
    }

    /**
     * 處理登入失敗次數過多之鎖定 (429)
     * @param ex 帳號鎖定例外 (訊息含剩餘分鐘數)
     * @param request 目前請求
     * @return 429 Problem Details
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ProblemDetail> handleLocked(LockedException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.LOGIN_LOCKED, ex.getMessage(), request));
    }

    /**
     * 處理請求頻率超過上限 (429)；由 RateLimitFilter 轉交，回應附 Retry-After 標頭 (秒)
     * @param ex 超量例外
     * @param request 目前請求
     * @return 429 Problem Details
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimited(RateLimitExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(ProblemType.RATE_LIMITED, ex.getMessage(), request);
        problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(problem.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    /**
     * 處理權限不足 (403)：方法級 @PreAuthorize、Service 擁有者核對，以及 SecurityConfig 之 accessDeniedHandler 轉交者
     * @param ex 權限不足例外
     * @param request 目前請求
     * @return 403 Problem Details
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.ACCESS_DENIED, "權限不足，無法執行此操作", request));
    }

    /**
     * 處理帳號尚未完成信箱驗證 (403)；附 email 供前端導向開通頁並預填
     * @param ex 帳號未驗證例外
     * @param request 目前請求
     * @return 403 Problem Details，code=ACCOUNT_NOT_VERIFIED
     */
    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ProblemDetail> handleAccountNotVerified(AccountNotVerifiedException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(ProblemType.ACCOUNT_NOT_VERIFIED, ex.getMessage(), request);
        problem.setProperty("email", ex.getEmail() != null ? ex.getEmail() : "");
        return respond(problem);
    }

    /**
     * 處理業務規則不符 (400)：例如問卷已截止、驗證碼錯誤
     * @param ex 非法引數或業務衝突例外
     * @param request 目前請求
     * @return 400 Problem Details，detail 為 Service 提供之繁中原因
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.BAD_REQUEST, ex.getMessage(), request));
    }

    /**
     * 處理請求 JSON 無法解析或日期格式錯誤 (400)
     * @param ex 訊息不可讀例外
     * @param request 目前請求
     * @return 400 Problem Details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("請求內容無法解析: {}", ex.getMessage());
        return respond(buildProblem(ProblemType.MALFORMED_REQUEST, "請求內容格式有誤或日期時間格式不符合規範，請檢查後再試", request));
    }

    /**
     * 處理 HTTP 方法不支援 (405)
     * @param ex 方法不支援例外
     * @param request 目前請求
     * @return 405 Problem Details
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.METHOD_NOT_ALLOWED, "此端點不支援 " + ex.getMethod() + " 請求方法", request));
    }

    /**
     * 處理不存在之路徑 (404)
     * @param ex 找不到資源例外
     * @param request 目前請求
     * @return 404 Problem Details
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(buildProblem(ProblemType.NOT_FOUND, "找不到此路徑：" + request.getRequestURI(), request));
    }

    /**
     * 處理系統全域未捕捉之未知例外 (500)：完整堆疊寫入日誌 (含 traceId)，回應只給固定訊息
     * @param ex 系統底層未預期例外
     * @param request 目前請求
     * @return 500 Problem Details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("系統全域未捕捉之未知例外", ex);
        return respond(buildProblem(ProblemType.INTERNAL_ERROR, "伺服器內部發生未預期錯誤，請稍後再試", request));
    }

    /**
     * 組出 RFC 9457 Problem Details：標準欄位 + code、traceId、timestamp 擴充欄位
     * @param type 錯誤類型 (決定 status、type、title、code)
     * @param detail 給使用者看的繁中原因
     * @param request 目前請求 (instance 取其路徑)
     * @return Problem Details 物件
     */
    private static ProblemDetail buildProblem(ProblemType type, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problem.setType(type.typeUri());
        problem.setTitle(type.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROPERTY_CODE, type.code());
        problem.setProperty(PROPERTY_TRACE_ID, MDC.get(RequestTraceFilter.MDC_TRACE_ID));
        problem.setProperty("timestamp", LocalDateTime.now());
        return problem;
    }

    /**
     * 以 Problem Details 之 status 作為 HTTP 狀態碼回應 (Spring MVC 自動設定 application/problem+json)
     * @param problem Problem Details
     * @return 回應
     */
    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
