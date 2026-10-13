/**
 * ============================================================================
 * 檔案名稱: RegisterRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 接收新會員註冊資料，包含信箱、帳號、密碼、姓名與手機
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: 前端 RegisterComponent 表單
 *   - 下游消費: AuthController, AuthService
 * ============================================================================
 */

package com.surveyflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "使用者帳號不得為空")
    @Size(min = 3, max = 50, message = "帳號長度需介於 3 至 50 字元")
    private String username;

    @NotBlank(message = "電子信箱不得為空")
    @Email(message = "電子信箱格式不正確")
    private String email;

    @NotBlank(message = "密碼不得為空")
    @Size(min = 6, max = 100, message = "密碼長度至少需 6 碼以上")
    private String password;

    @NotBlank(message = "真實姓名不得為空")
    @Size(min = 2, max = 50, message = "姓名長度需介於 2 至 50 字元")
    private String fullName;

    @Pattern(regexp = "^09\\d{8}$", message = "手機號碼需為 09 開頭共 10 碼數字")
    private String phone;

    private String identityCard;
}
