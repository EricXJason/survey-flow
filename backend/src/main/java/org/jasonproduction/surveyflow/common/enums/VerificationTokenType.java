/**
 * ============================================================================
 * 檔案名稱: VerificationTokenType.java
 * 所屬模組: Common 核心列舉
 * 責任描述: 定義信箱驗證碼 (OTP) 之用途類型，區隔註冊啟用與忘記密碼兩種流程
 * 架構分層: Domain / Enum Layer
 * 聯動關係:
 *   - 上游依賴: verification_tokens.token_type 欄位 (VARCHAR，以列舉名稱存放)
 *   - 下游消費: VerificationToken 實體、AuthService、VerificationTokenRepository
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.enums;

public enum VerificationTokenType {
    /** 註冊啟用驗證碼 */
    REGISTRATION,

    /** 忘記密碼重設驗證碼 */
    FORGOT_PASSWORD
}
