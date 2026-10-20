/**
 * ============================================================================
 * 檔案名稱: RefreshTokenRequest.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 接收前端傳遞之長效 Refresh Token 以便換發新 Access Token
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: 前端 HTTP Interceptor
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
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh Token 不得為空")
    private String refreshToken;
}
