/**
 * ============================================================================
 * 檔案名稱: AuthController.java
 * 所屬模組: Controller 控制器層 - 會員認證
 * 責任描述: 提供會員註冊、身分登入、零信任雙 Token 換發之公開 (Public) RESTful API 端點，以及會員安全登出端點
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: AuthService
 *   - 下游消費: 前端 LogInComponent, RegisterComponent, UserStateService
 * 邊界處理: 公開端點免 Token，登出端點需由 SecurityContext 取得目前登入使用者並主動吊銷 Access Token
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.auth.AuthResponse;
import com.surveyflow.dto.auth.ForgetPasswordRequest;
import com.surveyflow.dto.auth.LoginRequest;
import com.surveyflow.dto.auth.RefreshTokenRequest;
import com.surveyflow.dto.auth.RegisterRequest;
import com.surveyflow.dto.auth.ResetPasswordRequest;
import com.surveyflow.dto.auth.VerifyEmailRequest;
import com.surveyflow.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 新會員註冊端點 (公開)
     * @param request 註冊資料
     * @return 註冊成功之雙 Token 憑證與會員概要，並注入 HttpOnly Refresh Token Cookie
     */
    @PostMapping({"/api/public/auth/register", "/api/auth/register"})
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        ResponseCookie cookie = buildRefreshTokenCookie(response.getRefreshToken(), 7 * 24 * 3600);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(response, "會員註冊成功！歡迎加入 SurveyFlow"));
    }

    /**
     * 會員帳密登入端點 (公開)
     * @param request 登入帳密
     * @return 雙 Token 與使用者身分，並注入 HttpOnly Refresh Token Cookie
     */
    @PostMapping({"/api/public/auth/login", "/api/auth/login"})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        ResponseCookie cookie = buildRefreshTokenCookie(response.getRefreshToken(), 7 * 24 * 3600);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(response, "登入成功"));
    }

    /**
     * 長效 Refresh Token 輪替換發 Access Token 端點 (公開)
     * 支援從 Request Body 或 HttpOnly Cookie 讀取 Refresh Token
     * @param request 包含 refreshToken 之請求
     * @param cookieRefreshToken 從 Cookie 自動攜帶之 Refresh Token (若有)
     * @return 輪替換發後之全新雙 Token
     */
    @PostMapping({"/api/public/auth/refresh", "/api/auth/refresh"})
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken) {
        AuthResponse response = authService.refreshToken(request, cookieRefreshToken);
        ResponseCookie cookie = buildRefreshTokenCookie(response.getRefreshToken(), 7 * 24 * 3600);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(response, "權杖刷新成功"));
    }

    /**
     * 會員主動登出端點 (需登入，撤銷 Access Token 並清除 Refresh Token Cookie)
     * @param userDetails 當前登入身分
     * @param authHeader 當前請求之 Authorization 標頭
     * @return 登出確認
     */
    @PostMapping({"/api/user/auth/logout", "/api/auth/logout"})
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (userDetails != null) {
            authService.logout(userDetails.getUsername(), authHeader);
        }
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
    @PostMapping({"/api/public/auth/verify-email", "/api/auth/verify-email"})
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = authService.verifyRegistrationEmail(request);
        ResponseCookie cookie = buildRefreshTokenCookie(response.getRefreshToken(), 7 * 24 * 3600L);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(response, "電子信箱驗證成功，帳號已正式開通！"));
    }

    /**
     * 重新寄送註冊啟用驗證碼端點 (公開)
     * @param payload 包含 email 鍵值
     * @return 寄發結果
     */
    @PostMapping({"/api/public/auth/resend-verification", "/api/auth/resend-verification"})
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody java.util.Map<String, String> payload) {
        String email = payload.get("email");
        if (!org.springframework.util.StringUtils.hasText(email)) {
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
    @PostMapping({"/api/public/auth/forget-password", "/api/auth/forget-password"})
    public ResponseEntity<ApiResponse<Void>> forgetPassword(@Valid @RequestBody ForgetPasswordRequest request) {
        authService.processForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("已發送 6 位數安全驗證碼至您的電子信箱，請查收以完成重設"));
    }

    /**
     * 會員重設新密碼端點 (公開，校驗信箱、6 碼驗證碼與新密碼)
     * @param request 包含信箱、驗證碼與新密碼
     * @return 重設結果
     */
    @PostMapping({"/api/public/auth/reset-password", "/api/auth/reset-password"})
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.processResetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("密碼已成功重設，請使用新密碼重新登入"));
    }

    /**
     * 封裝 Refresh Token 之安全 Cookie 物件
     */
    private ResponseCookie buildRefreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false) // 支援本地 localhost HTTP 開發環境
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }
}
