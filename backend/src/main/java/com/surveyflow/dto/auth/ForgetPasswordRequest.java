/**
 * ============================================================================
 * 檔案名稱: ForgetPasswordRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 封裝會員申請忘記密碼身分審核請求 (帳號與註冊信箱)
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: ForgetPasswordComponent
 *   - 下游消費: AuthController, AuthService
 * ============================================================================
 */

package com.surveyflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgetPasswordRequest {

    /**
     * 會員登入帳號
     */
    @NotBlank(message = "使用者帳號不可為空")
    private String username;

    /**
     * 會員註冊時所綁定之電子信箱
     */
    @NotBlank(message = "電子信箱不可為空")
    @Email(message = "電子信箱格式不正確")
    private String email;
}
