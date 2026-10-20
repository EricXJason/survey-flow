/**
 * ============================================================================
 * 檔案名稱: AuthResponse.java
 * 所屬模組: DTO 資料傳輸層 - 認證模組
 * 責任描述: 封裝登入與 Token 刷新成功後回傳給前端之雙 Token 與會員概要
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AuthService, TokenProvider
 *   - 下游消費: 前端 UserStateService (存儲 accessToken 與使用者資訊)
 * ============================================================================
 */

package com.surveyflow.dto.auth;

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
public class AuthResponse {

    /** 單一標準 JWT Token */
    private String token;

    /** 向下相容 JWT Access Token */
    private String accessToken;

    /** 向下相容 JWT Refresh Token */
    private String refreshToken;

    /** Token 類型 (固定為 Bearer) */
    @Builder.Default
    private String tokenType = "Bearer";

    /** 使用者 ID */
    private Long userId;

    /** 使用者帳號 */
    private String username;

    /** 使用者真實姓名 */
    private String fullName;

    /** 電子信箱 */
    private String email;

    /** 系統角色權限 (ROLE_USER 或 ROLE_ADMIN) */
    private String role;
}
