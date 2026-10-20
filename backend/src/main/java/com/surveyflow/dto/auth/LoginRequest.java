/**
 * ============================================================================
 * 檔案名稱: LoginRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 接收會員登入請求之帳號與密碼參數
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: 前端 LogInComponent 表單
 *   - 下游消費: AuthController, AuthService
 * ============================================================================
 */

package com.surveyflow.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "使用者帳號不得為空")
    private String username;

    @NotBlank(message = "密碼不得為空")
    private String password;
}
