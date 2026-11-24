/**
 * ============================================================================
 * 檔案名稱: AuthController.java
 * 所屬模組: Controller 控制器層 - 會員認證 (資安 S 級)
 * 責任描述: 認證相關 HTTP 端點 (註冊、信箱驗證、登入、換發、登出、忘記 / 重設密碼)，並負責 Refresh Token Cookie 之下發與清除：
 *   - Access Token 放在回應 Body，前端存於 sessionStorage，以 Bearer 標頭傳送
 *   - Refresh Token 只放在 Cookie，回應 Body 中一律為 null (JavaScript 讀不到，降低 XSS 竊取風險)
 *   Cookie 屬性 (見 buildRefreshTokenCookie)：HttpOnly、Secure、SameSite、Path=/、Max-Age=Refresh Token 效期
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: AuthService；application.properties (jwt.refresh-token-expiration-ms、app.auth.refresh-cookie-*)
 *   - 下游消費: 前端 AuthActionService (登入登出)、ApiClientService (401 時呼叫換發)、Register / VerifyEmail / Reset 頁
 * 邊界處理: 錯誤由 GlobalExceptionHandler 轉換：帳密錯誤 401、帳號鎖定 429、未驗證 403、寄信失敗 503
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.common.ApiResponse;
import org.jasonproduction.surveyflow.dto.auth.AuthResponse;
import org.jasonproduction.surveyflow.dto.auth.ForgetPasswordRequest;
import org.jasonproduction.surveyflow.dto.auth.LoginRequest;
import org.jasonproduction.surveyflow.dto.auth.RefreshTokenRequest;
import org.jasonproduction.surveyflow.dto.auth.RegisterRequest;
import org.jasonproduction.surveyflow.dto.auth.ResetPasswordRequest;
import org.jasonproduction.surveyflow.dto.auth.VerifyEmailRequest;
import org.jasonproduction.surveyflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.util.StringUtils;

@RestController
public class AuthController {

    /** Refresh Token Cookie 名稱 */
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final long refreshCookieMaxAgeSeconds;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;

    /**
     * 建構子注入認證服務與 Refresh Cookie 設定 (效期與 jwt.refresh-token-expiration-ms 共用同一來源)
     * @param authService 認證業務服務
     * @param refreshTokenExpirationMs Refresh Token 存活毫秒數
     * @param refreshCookieSecure 是否標記 Secure (正式環境 true；本機 HTTP 開發以環境變數設為 false)
     * @param refreshCookieSameSite SameSite 屬性 (前後端跨站部署須為 None；本機 HTTP 開發用 Lax)
     */
    public AuthController(AuthService authService,
                          @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs,
                          @Value("${app.auth.refresh-cookie-secure}") boolean refreshCookieSecure,
                          @Value("${app.auth.refresh-cookie-same-site}") String refreshCookieSameSite) {
        this.authService = authService;
        this.refreshCookieMaxAgeSeconds = refreshTokenExpirationMs / 1000;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    /**
     * 新會員註冊端點 (公開)
     * @param request 註冊資料
     * @return 會員概要 (尚未簽發 Token，需完成信箱驗證)
     */
    @PostMapping("/api/public/auth/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        // [Why]: 註冊後帳號仍待信箱驗證，尚未簽發任何 Token，故不下發 Refresh Cookie
        return ResponseEntity.ok(ApiResponse.ok(response, "會員註冊成功！請至信箱收取 6 碼啟用驗證碼"));
    }

    /**
     * 會員帳密登入端點 (公開)
     * @param request 登入帳密
     * @return 雙 Token 與使用者身分，並注入 HttpOnly Refresh Token Cookie
     */
    @PostMapping("/api/public/auth/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withRefreshCookie(response, "登入成功");
    }

    /**
     * 長效 Refresh Token 輪替換發 Access Token 端點 (公開)
     * 支援從 Request Body 或 HttpOnly Cookie 讀取 Refresh Token
     * @param request 包含 refreshToken 之請求
     * @param cookieRefreshToken 從 Cookie 自動攜帶之 Refresh Token (若有)
     * @return 輪替換發後之全新雙 Token
     */
    @PostMapping("/api/public/auth/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieRefreshToken) {
        AuthResponse response = authService.refreshToken(request, cookieRefreshToken);
        return withRefreshCookie(response, "權杖刷新成功");
    }

    /**
     * 會員主動登出端點 (需登入，同時吊銷 Access Token 與 Refresh Token 並清除 Cookie)
     * @param userDetails 當前登入身分
     * @param authHeader 當前請求之 Authorization 標頭
     * @param cookieRefreshToken 瀏覽器自動攜帶之 Refresh Token Cookie (若有)
     * @return 登出確認
     */
    @PostMapping("/api/user/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieRefreshToken) {
        // [Why]: /api/user/** 已由 SecurityConfig 要求登入，userDetails 必定存在
        authService.logout(userDetails.getUsername(), authHeader, cookieRefreshToken);
        ResponseCookie cleanCookie = buildRefreshTokenCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.ok("已安全登出"));
    }

    /**
     * 會員註冊信箱 6 碼啟用碼核驗端點 (公開)
     * @param request 包含信箱與 6 碼驗證碼
     * @return 驗證通過開通帳號並直接簽發雙 Token 登入
     */
    @PostMapping("/api/public/auth/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = authService.verifyRegistrationEmail(request);
        return withRefreshCookie(response, "電子信箱驗證成功，帳號已正式開通！");
    }

    /**
     * 重新寄送註冊啟用驗證碼端點 (公開)
     * @param payload 包含 email 鍵值
     * @return 寄發結果
     */
    @PostMapping("/api/public/auth/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("請提供欲重新寄發之電子信箱");
        }
        authService.resendRegistrationCode(email);
        return ResponseEntity.ok(ApiResponse.ok("啟用驗證碼已重新寄發至您的電子信箱"));
    }

    /**
     * 忘記密碼身分核驗與寄發 OTP 端點 (公開，僅需提供 Email)
     * @param request 包含信箱之請求
     * @return 核驗結果
     */
    @PostMapping("/api/public/auth/forget-password")
    public ResponseEntity<ApiResponse<Void>> forgetPassword(@Valid @RequestBody ForgetPasswordRequest request) {
        authService.processForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("已發送 6 位數安全驗證碼至您的電子信箱，請查收以完成重設"));
    }

    /**
     * 會員重設新密碼端點 (公開，校驗信箱、6 碼驗證碼與新密碼)
     * @param request 包含信箱、驗證碼與新密碼
     * @return 重設結果
     */
    @PostMapping("/api/public/auth/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.processResetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("密碼已成功重設，請使用新密碼重新登入"));
    }

    /**
     * 以 HttpOnly Cookie 下發 Refresh Token，並自回應 Body 移除 Refresh Token
     * [Why]: Refresh Token 只存在瀏覽器無法以 JavaScript 讀取之 Cookie，Body 不重複回傳以免被前端程式或日誌接觸
     * @param response 認證結果 (含新簽發之雙 Token)
     * @param message 成功訊息
     * @return 含 Set-Cookie 標頭之 200 回應
     */
    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(AuthResponse response, String message) {
        ResponseCookie cookie = buildRefreshTokenCookie(response.getRefreshToken(), refreshCookieMaxAgeSeconds);
        AuthResponse body = response.toBuilder().refreshToken(null).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(body, message));
    }

    /**
     * 封裝 Refresh Token 之安全 Cookie 物件
     * - HttpOnly: 禁止 JavaScript 讀取，防 XSS 竊取
     * - Secure: 僅經 HTTPS 傳送 (由 app.auth.refresh-cookie-secure 控制，本機 HTTP 開發可關閉)
     * - SameSite: 由 app.auth.refresh-cookie-same-site 控制。正式環境前端 (pages.dev) 與後端 (onrender.com) 跨站，
     *   必須為 None 瀏覽器才會在換發請求夾帶 Cookie；跨站濫用由 CORS 白名單阻擋 (非白名單來源讀不到回應)
     * - Path=/: 全站路徑可見，供換發與登出端點讀取
     * @param token Refresh Token 字串
     * @param maxAgeSeconds Cookie 存活秒數
     * @return 封裝完成之 ResponseCookie
     */
    private ResponseCookie buildRefreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(refreshCookieSameSite)
                .build();
    }
}
