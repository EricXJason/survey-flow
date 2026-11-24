/**
 * ============================================================================
 * 檔案名稱: ProblemType.java
 * 所屬模組: Common 核心例外處置模組
 * 責任描述: 定義全系統錯誤回應 (RFC 9457 Problem Details) 的錯誤類型：HTTP 狀態、機器可讀代碼與繁中標題
 * 架構分層: Common / Exception Layer
 * 聯動關係:
 *   - 上游依賴: 無
 *   - 下游消費: GlobalExceptionHandler (組成回應之 type、title、status、code)、前端依 code 判斷特殊流程 (如 ACCOUNT_NOT_VERIFIED)
 * 邊界處理: type 使用不可解析之 URN (RFC 9457 允許)，僅作為穩定識別碼；前端不應依 title 判斷流程，一律以 code 判斷
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.exception;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Locale;

public enum ProblemType {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "輸入內容不符合格式"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "請求無法處理"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "請求內容格式錯誤"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "尚未登入或憑證無效"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "權限不足"),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "帳號尚未開通"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "找不到資源"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支援的請求方法"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "資料已被他人修改"),
    DUPLICATE_DATA(HttpStatus.CONFLICT, "資料重複"),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "登入暫時鎖定"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "請求過於頻繁"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "伺服器內部錯誤"),
    MAIL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "郵件服務暫時無法使用");

    /** type URI 前綴，完整值如 urn:surveyflow:problem:validation-failed */
    private static final String TYPE_URN_PREFIX = "urn:surveyflow:problem:";

    private final HttpStatus status;
    private final String title;

    /**
     * @param status 對應之 HTTP 狀態碼
     * @param title 給人看的簡短標題 (同一類型固定不變)
     */
    ProblemType(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    /**
     * 取得此錯誤類型之 HTTP 狀態碼
     * @return HTTP 狀態
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 取得此錯誤類型之繁中標題 (Problem Details 的 title 欄位)
     * @return 標題
     */
    public String title() {
        return title;
    }

    /**
     * 取得機器可讀代碼 (Problem Details 擴充欄位 code)，即列舉名稱，如 ACCOUNT_NOT_VERIFIED
     * @return 代碼
     */
    public String code() {
        return name();
    }

    /**
     * 取得 Problem Details 的 type 欄位：以列舉名稱轉為小寫連字號之 URN
     * @return 如 urn:surveyflow:problem:account-not-verified
     */
    public URI typeUri() {
        return URI.create(TYPE_URN_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
