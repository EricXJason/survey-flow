/**
 * ============================================================================
 * 檔案名稱: AccountNotVerifiedException.java
 * 所屬模組: 核心通用例外與錯誤處理模組 (Common Exceptions)
 * 責任描述: 定義會員帳號尚未完成電子信箱驗證時之專屬業務例外，攜帶待驗證之信箱資訊
 * 架構分層: Presentation & Domain Layer / Custom Exception
 * 聯動關係:
 *   - 上游依賴: AuthService (驗證使用者狀態為 PENDING_VERIFICATION 時拋出)
 *   - 下游消費: GlobalExceptionHandler (捕捉並映射為 HTTP 403 與 ACCOUNT_NOT_VERIFIED 狀態)
 * 邊界處理: 封裝待開通信箱地址，供前端呈現重發驗證信與快速開通按鈕
 * ============================================================================
 */

package com.surveyflow.common;

import lombok.Getter;

/**
 * 帳號尚未完成信箱開通業務例外
 */
@Getter
public class AccountNotVerifiedException extends RuntimeException {

    private final String email;

    /**
     * 建構子宣告例外訊息與目標信箱
     * @param message 友善錯誤提示
     * @param email 待開通信箱
     */
    public AccountNotVerifiedException(String message, String email) {
        super(message);
        this.email = email;
    }
}
