/**
 * ============================================================================
 * 檔案名稱: ResetPasswordRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 封裝會員透過信箱收件驗證碼重設新密碼之請求
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: ResetPasswordComponent
 *   - 下游消費: AuthController, AuthService
 * ============================================================================
 */

package com.surveyflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    /**
     * 會員帳號 (選填，可由信箱自動對齊)
     */
    private String username;

    /**
     * 會員註冊電子信箱
     */
    @NotBlank(message = "電子信箱不可為空")
    @Email(message = "電子信箱格式不正確")
    private String email;

    /**
     * 信箱收到的 6 碼數字安全驗證碼
     */
    @NotBlank(message = "驗證碼不可為空")
    @Size(min = 6, max = 6, message = "驗證碼須為 6 位數字")
    private String code;

    /**
     * 欲重設之新密碼
     */
    @NotBlank(message = "新密碼不可為空")
    @Size(min = 6, max = 100, message = "新密碼長度須介於 6 至 100 字元")
    private String newPassword;
}
