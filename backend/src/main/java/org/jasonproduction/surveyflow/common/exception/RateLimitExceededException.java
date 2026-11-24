/**
 * ============================================================================
 * 檔案名稱: RateLimitExceededException.java
 * 所屬模組: Common 核心例外處置模組
 * 責任描述: 代表用戶端在同一時間窗內請求次數超過上限，攜帶需等待之秒數供回應 Retry-After 標頭
 * 架構分層: Common / Exception Layer
 * 聯動關係:
 *   - 上游依賴: RateLimitFilter (偵測超量時建立)
 *   - 下游消費: GlobalExceptionHandler (轉為 429 Problem Details 與 Retry-After)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    /** 距離目前時間窗結束還需等待的秒數 (至少 1) */
    private final long retryAfterSeconds;

    /**
     * 建立超量例外，訊息會告知使用者需等待之秒數
     * @param retryAfterSeconds 需等待秒數
     */
    public RateLimitExceededException(long retryAfterSeconds) {
        super("請求過於頻繁，請於 " + Math.max(retryAfterSeconds, 1) + " 秒後再試");
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }
}
