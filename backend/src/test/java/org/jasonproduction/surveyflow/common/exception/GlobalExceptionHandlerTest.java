/**
 * ============================================================================
 * 檔案名稱: GlobalExceptionHandlerTest.java
 * 測試對象: GlobalExceptionHandler (例外 → RFC 9457 Problem Details 之狀態碼、type、code、detail 對照)
 * 測試方式: 直接呼叫各 @ExceptionHandler 方法，驗證標準欄位與擴充欄位，以及 500 時不外洩內部訊息
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.exception;

import jakarta.persistence.OptimisticLockException;
import org.jasonproduction.surveyflow.common.web.RequestTraceFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailSendException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/auth/login");

    /**
     * 每個測試結束清除 MDC，避免 traceId 影響其他測試
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 驗證 Problem Details 之標準欄位 (status、type、title、detail、instance) 與 code 擴充欄位
     * @param response 處理器回應
     * @param type 預期錯誤類型
     * @param detail 預期給使用者看的訊息
     * @return 回應主體，供個別測試再檢查擴充欄位
     */
    private ProblemDetail assertProblem(ResponseEntity<ProblemDetail> response, ProblemType type, String detail) {
        assertEquals(type.status(), response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals(type.status().value(), body.getStatus());
        assertEquals(type.typeUri(), body.getType());
        assertEquals(type.title(), body.getTitle());
        assertEquals(detail, body.getDetail());
        assertEquals(URI.create("/api/public/auth/login"), body.getInstance());
        assertEquals(type.code(), body.getProperties().get(GlobalExceptionHandler.PROPERTY_CODE));
        return body;
    }

    /**
     * 建立含單一欄位錯誤之驗證例外
     * @param message 欄位錯誤訊息，可為 null
     * @return 驗證例外
     * @throws NoSuchMethodException 不會發生 (取 Object.toString 作為方法參數)
     */
    private static MethodArgumentNotValidException validationException(String message) throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "username", message));
        return new MethodArgumentNotValidException(new MethodParameter(Object.class.getMethod("toString"), -1), bindingResult);
    }

    @Test
    @DisplayName("type 為 urn:surveyflow:problem:* 小寫連字號；traceId 取自 MDC；附 timestamp")
    void problemCarriesTypeUrnAndTraceId() {
        MDC.put(RequestTraceFilter.MDC_TRACE_ID, "trace-abc-123");

        ProblemDetail body = assertProblem(handler.handleIllegalArgument(new IllegalArgumentException("x"), request), ProblemType.BAD_REQUEST, "x");

        assertEquals(URI.create("urn:surveyflow:problem:bad-request"), body.getType());
        assertEquals("trace-abc-123", body.getProperties().get(GlobalExceptionHandler.PROPERTY_TRACE_ID));
        assertNotNull(body.getProperties().get("timestamp"));
    }

    @Test
    @DisplayName("欄位驗證失敗 → 400 VALIDATION_FAILED，errors 為「欄位 → 錯誤訊息」")
    void validationError_Returns400WithFieldErrors() throws Exception {
        ProblemDetail body = assertProblem(handler.handleValidationExceptions(validationException("帳號不可為空"), request),
                ProblemType.VALIDATION_FAILED, "輸入參數格式驗證不符: 帳號不可為空");

        assertEquals(Map.of("username", "帳號不可為空"), body.getProperties().get("errors"));
    }

    @Test
    @DisplayName("欄位驗證錯誤沒有訊息時：使用通用提示文字")
    void validationErrorWithoutMessage_UsesGenericText() throws Exception {
        assertProblem(handler.handleValidationExceptions(validationException(null), request),
                ProblemType.VALIDATION_FAILED, "輸入參數格式驗證不符，請核對輸入內容");
    }

    @Test
    @DisplayName("BadCredentialsException → 401 UNAUTHENTICATED，訊息沿用例外訊息")
    void badCredentials_Returns401() {
        assertProblem(handler.handleBadCredentials(new BadCredentialsException("帳號或密碼錯誤"), request), ProblemType.UNAUTHENTICATED, "帳號或密碼錯誤");
    }

    @Test
    @DisplayName("過濾鏈未認證 (InsufficientAuthenticationException) → 401，固定訊息")
    void unauthenticated_Returns401() {
        assertProblem(handler.handleUnauthenticated(new InsufficientAuthenticationException("Full authentication is required"), request),
                ProblemType.UNAUTHENTICATED, "尚未登入或登入憑證已過期");
    }

    @Test
    @DisplayName("LockedException → 429 LOGIN_LOCKED")
    void locked_Returns429() {
        assertProblem(handler.handleLocked(new LockedException("請稍後再試"), request), ProblemType.LOGIN_LOCKED, "請稍後再試");
    }

    @Test
    @DisplayName("RateLimitExceededException → 429 RATE_LIMITED，附 Retry-After 標頭與 retryAfterSeconds")
    void rateLimited_Returns429WithRetryAfter() {
        ResponseEntity<ProblemDetail> response = handler.handleRateLimited(new RateLimitExceededException(42), request);

        ProblemDetail body = assertProblem(response, ProblemType.RATE_LIMITED, "請求過於頻繁，請於 42 秒後再試");
        assertEquals("42", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(42L, body.getProperties().get("retryAfterSeconds"));
    }

    @Test
    @DisplayName("RateLimitExceededException 秒數不足 1 時以 1 秒計")
    void rateLimited_MinimumOneSecond() {
        assertEquals(1, new RateLimitExceededException(0).getRetryAfterSeconds());
    }

    @Test
    @DisplayName("樂觀鎖衝突 (JPA 或 Spring 轉譯後之例外) → 409 VERSION_CONFLICT")
    void optimisticLock_Returns409() {
        assertProblem(handler.handleOptimisticLock(new OptimisticLockException("x"), request), ProblemType.VERSION_CONFLICT, "此問卷已被其他人修改，請重新整理後再試");
        assertProblem(handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException(Object.class, 1L), request),
                ProblemType.VERSION_CONFLICT, "此問卷已被其他人修改，請重新整理後再試");
    }

    @Test
    @DisplayName("唯一索引衝突 (併發註冊) → 409 DUPLICATE_DATA，訊息不含 SQL 細節")
    void dataIntegrityViolation_Returns409() {
        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("Duplicate entry 'testuser' for key 'uk_users_username'"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().getDetail().contains("Duplicate"));
    }

    @Test
    @DisplayName("MailSendException → 503 MAIL_UNAVAILABLE")
    void mailSend_Returns503() {
        assertProblem(handler.handleMailSend(new MailSendException("驗證信寄送失敗，請稍後再試"), request),
                ProblemType.MAIL_UNAVAILABLE, "驗證信寄送失敗，請稍後再試");
    }

    @Test
    @DisplayName("AccessDeniedException → 403 ACCESS_DENIED，使用固定訊息")
    void accessDenied_Returns403() {
        assertProblem(handler.handleAccessDenied(new AccessDeniedException("內部細節"), request), ProblemType.ACCESS_DENIED, "權限不足，無法執行此操作");
    }

    @Test
    @DisplayName("AccountNotVerifiedException → 403，code=ACCOUNT_NOT_VERIFIED 並帶回信箱；沒有信箱時為空字串")
    void accountNotVerified_Returns403WithEmail() {
        ProblemDetail body = assertProblem(handler.handleAccountNotVerified(new AccountNotVerifiedException("尚未驗證", "t@example.com"), request),
                ProblemType.ACCOUNT_NOT_VERIFIED, "尚未驗證");
        assertEquals("t@example.com", body.getProperties().get("email"));

        ProblemDetail withoutEmail = handler.handleAccountNotVerified(new AccountNotVerifiedException("尚未驗證", null), request).getBody();
        assertEquals("", withoutEmail.getProperties().get("email"));
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 BAD_REQUEST，訊息沿用例外訊息 (業務規則錯誤)")
    void illegalArgument_Returns400() {
        assertProblem(handler.handleIllegalArgument(new IllegalArgumentException("問卷已截止"), request), ProblemType.BAD_REQUEST, "問卷已截止");
    }

    @Test
    @DisplayName("JSON 格式錯誤 → 400 MALFORMED_REQUEST")
    void unreadableBody_Returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));

        assertProblem(handler.handleHttpMessageNotReadable(ex, request), ProblemType.MALFORMED_REQUEST,
                "請求內容格式有誤或日期時間格式不符合規範，請檢查後再試");
    }

    @Test
    @DisplayName("不支援之 HTTP 方法 → 405，訊息含方法名稱")
    void methodNotSupported_Returns405() {
        assertProblem(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("TRACE"), request),
                ProblemType.METHOD_NOT_ALLOWED, "此端點不支援 TRACE 請求方法");
    }

    @Test
    @DisplayName("不存在之路徑 → 404 NOT_FOUND，訊息含路徑")
    void noResource_Returns404() {
        assertProblem(handler.handleNoResource(new NoResourceFoundException(HttpMethod.POST, "/api/public/auth/login", null), request),
                ProblemType.NOT_FOUND, "找不到此路徑：/api/public/auth/login");
    }

    @Test
    @DisplayName("未預期例外 → 500 INTERNAL_ERROR，回應不包含內部例外訊息")
    void unexpectedException_Returns500WithoutLeakingDetails() {
        ResponseEntity<ProblemDetail> response = handler.handleGeneralException(new RuntimeException("SQL syntax error near users"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().getDetail().contains("未預期錯誤"));
        assertFalse(response.getBody().getDetail().contains("SQL"));
    }
}
