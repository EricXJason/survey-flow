/**
 * ============================================================================
 * 檔案名稱: AuthController.java
 * 所屬模組: Controller 控制器層 - 會員認證
 * 責任描述: 提供會員註冊、身分登入、雙 Token 換發之公開 (Public) RESTful API 端點，以及會員登出端點
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: AuthService
 *   - 下游消費: 前端 LogInComponent, RegisterComponent, UserStateService
 * 邊界處理: 公開端點免 Token，登出端點需由 SecurityContext 取得目前登入使用者
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.auth.AuthResponse;
import com.surveyflow.dto.auth.LoginRequest;
import com.surveyflow.dto.auth.RefreshTokenRequest;
import com.surveyflow.dto.auth.RegisterRequest;
import com.surveyflow.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
     * @return 註冊成功之認證 Token 與會員概要
     */
    @PostMapping({"/api/public/auth/register", "/api/auth/register"})
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "會員註冊成功！歡迎加入 SurveyFlow"));
    }

    /**
     * 會員帳密登入端點 (公開)
     * @param request 登入帳密
     * @return 雙 Token 與使用者身分
     */
    @PostMapping({"/api/public/auth/login", "/api/auth/login"})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "登入成功"));
    }

    /**
     * 長效 Refresh Token 換發 Access Token 端點 (公開)
     * @param request 包含 refreshToken 之請求
     * @return 刷新後之雙 Token
     */
    @PostMapping({"/api/public/auth/refresh", "/api/auth/refresh"})
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "權杖刷新成功"));
    }

    /**
     * 會員登出端點 (需登入)
     * @param userDetails 當前登入身分
     * @return 登出確認
     */
    @PostMapping({"/api/user/auth/logout", "/api/auth/logout"})
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            authService.logout(userDetails.getUsername());
        }
        return ResponseEntity.ok(ApiResponse.ok("已安全登出"));
    }

    /**
     * 忘記密碼身分核驗端點 (公開)
     * @param request 帳號與信箱
     * @return 核驗結果
     */
    @PostMapping({"/api/public/auth/forget-password", "/api/auth/forget-password"})
    public ResponseEntity<ApiResponse<Void>> forgetPassword(@Valid @RequestBody com.surveyflow.dto.auth.ForgetPasswordRequest request) {
        authService.processForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("身分核驗通過，已寄發重設密碼驗證信"));
    }

    /**
     * 會員重設新密碼端點 (公開)
     * @param request 包含帳號、信箱與新密碼
     * @return 重設結果
     */
    @PostMapping({"/api/public/auth/reset-password", "/api/auth/reset-password"})
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody com.surveyflow.dto.auth.ResetPasswordRequest request) {
        authService.processResetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("密碼已成功重設，請使用新密碼重新登入"));
    }
}
