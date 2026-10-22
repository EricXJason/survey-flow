/**
 * ============================================================================
 * 檔案名稱: ForgetPasswordRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 封裝會員申請忘記密碼請求 (僅需提供電子信箱)
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: ForgetPasswordComponent
 *   - 下游消費: AuthController, AuthService
 * ============================================================================
 */

package com.surveyflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgetPasswordRequest {

    /**
     * 會員登入帳號 (選填，相容舊版介面)
     */
    private String username;

    /**
     * 會員註冊時所綁定之電子信箱 (核心必填欄位)
     */
    @NotBlank(message = "電子信箱不可為空")
    @Email(message = "電子信箱格式不正確")
    private String email;
}
