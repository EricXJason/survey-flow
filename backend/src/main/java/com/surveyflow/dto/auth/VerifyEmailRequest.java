/**
 * ============================================================================
 * 檔案名稱: VerifyEmailRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 封裝會員註冊後透過信箱 6 碼驗證碼啟用帳號之請求
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: RegisterComponent
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
public class VerifyEmailRequest {

    /**
     * 會員註冊電子信箱
     */
    @NotBlank(message = "電子信箱不可為空")
    @Email(message = "電子信箱格式不正確")
    private String email;

    /**
     * 信箱收到的 6 碼啟用驗證碼
     */
    @NotBlank(message = "驗證碼不可為空")
    @Size(min = 6, max = 6, message = "驗證碼須為 6 位數字")
    private String code;
}
