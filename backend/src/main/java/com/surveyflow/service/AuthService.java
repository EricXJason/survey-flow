/**
 * ============================================================================
 * 檔案名稱: AuthService.java
 * 所屬模組: Service 業務邏輯層 - 認證模組
 * 責任描述: 負責會員註冊 (Argon2id 密碼雜湊)、身分登入校驗、零信任雙 Token (短效 Access Token + 長效 Refresh Token) 簽發、RTR 輪替與主動吊銷
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, PasswordEncoder, TokenProvider, TokenRevocationService
 *   - 下游消費: AuthController
 * 邊界處理: 密碼校驗失敗拋出 BadCredentialsException；Token 異常或重放攻擊拋出 BadCredentialsException/IllegalArgumentException
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.auth.AuthResponse;
import com.surveyflow.dto.auth.ForgetPasswordRequest;
import com.surveyflow.dto.auth.LoginRequest;
import com.surveyflow.dto.auth.RefreshTokenRequest;
import com.surveyflow.dto.auth.RegisterRequest;
import com.surveyflow.dto.auth.ResetPasswordRequest;
import com.surveyflow.entity.User;
import com.surveyflow.repository.UserRepository;
import com.surveyflow.security.TokenProvider;
import com.surveyflow.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final TokenRevocationService tokenRevocationService;

    /**
     * 處理新會員註冊業務
     * @param request 註冊參數
     * @return 註冊成功後簽發之雙 Token 與會員概要
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("此帳號已被註冊，請更換其他帳號");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("此電子信箱已被使用，請更換其他信箱");
        }

        // 使用 Argon2id 加密密碼
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .identityCard(request.getIdentityCard())
                .role(RoleType.ROLE_USER)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);
        log.info("新會員註冊成功: username={}, email={}", user.getUsername(), user.getEmail());

        return createDualTokenAuthResponse(user);
    }

    /**
     * 處理會員登入驗證
     * @param request 登入請求
     * @return 登入成功之雙 Token 與會員概要
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("帳號或密碼錯誤"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("帳號或密碼錯誤");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("該帳號目前處於非啟用狀態，請聯繫系統管理員");
        }

        log.info("會員登入成功: username={}", user.getUsername());
        return createDualTokenAuthResponse(user);
    }

    /**
     * 透過長效 Refresh Token 輪替換發新 Access Token 與新 Refresh Token (RTR 機制)
     * @param request 包含 refreshToken 之請求物件
     * @param cookieRefreshToken 從 HttpOnly Cookie 提取之 Refresh Token (可為空)
     * @return 輪替簽發後之全新雙 Token 物件
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request, String cookieRefreshToken) {
        String tokenStr = (request != null && StringUtils.hasText(request.getRefreshToken()))
                ? request.getRefreshToken().trim()
                : cookieRefreshToken;

        if (!StringUtils.hasText(tokenStr)) {
            throw new BadCredentialsException("缺少 Refresh Token 憑證，請重新登入");
        }

        if (!tokenProvider.validateToken(tokenStr)) {
            throw new BadCredentialsException("Refresh Token 憑證無效或已過期，請重新登入");
        }

        if (!tokenProvider.isRefreshToken(tokenStr)) {
            throw new BadCredentialsException("憑證類型錯誤，非有效之 Refresh Token");
        }

        String username = tokenProvider.getUsernameFromToken(tokenStr);
        String oldJti = tokenProvider.getJtiFromToken(tokenStr);
        long remainingTtl = tokenProvider.getRemainingTtlMs(tokenStr);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("查無所屬會員資料"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("該帳號已被凍結或停用");
        }

        // 簽發新雙 Token
        String newAccessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getUsername());
        String newRefreshJti = tokenProvider.getJtiFromToken(newRefreshToken);

        // 執行 RTR 單次輪替防重放保護
        boolean rotated = tokenRevocationService.rotateRefreshToken(oldJti, newRefreshJti, username, remainingTtl);
        if (!rotated) {
            throw new BadCredentialsException("偵測到異常重放攻擊或已作廢之 Refresh Token，系統已安全阻斷，請重新登入");
        }

        log.info("Refresh Token 輪替成功 (RTR): username={}, newRefreshJti={}", username, newRefreshJti);

        return AuthResponse.builder()
                .token(newAccessToken)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 處理會員主動登出 (將當前 Access Token 加入黑名單即刻吊銷)
     * @param username 當前登入帳號
     * @param bearerToken 當前持有的 Bearer Access Token 字串 (若有)
     */
    @Transactional
    public void logout(String username, String bearerToken) {
        if (StringUtils.hasText(bearerToken)) {
            String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
            String jti = tokenProvider.getJtiFromToken(token);
            long remainingTtl = tokenProvider.getRemainingTtlMs(token);
            tokenRevocationService.revokeToken(jti, remainingTtl);
        }
        log.info("會員已主動安全登出: username={}", username);
    }

    /**
     * 處理會員登出多載 (相容無 Token 參數呼叫)
     * @param username 當前登入帳號
     */
    @Transactional
    public void logout(String username) {
        logout(username, null);
    }

    /**
     * 處理忘記密碼身分核驗
     * @param request 包含帳號與信箱之請求
     */
    @Transactional(readOnly = true)
    public void processForgotPassword(ForgetPasswordRequest request) {
        User user = userRepository.findByUsernameNative(request.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號，請確認輸入是否正確"));

        if (!user.getEmail().equalsIgnoreCase(request.getEmail().trim())) {
            throw new IllegalArgumentException("輸入之電子信箱與該帳號綁定之信箱不相符");
        }
        log.info("忘記密碼核驗成功: username={}, email={}", user.getUsername(), user.getEmail());
    }

    /**
     * 處理重設新密碼
     * @param request 包含帳號、信箱與新密碼之請求
     */
    @Transactional
    public void processResetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByUsernameNative(request.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號"));

        if (!user.getEmail().equalsIgnoreCase(request.getEmail().trim())) {
            throw new IllegalArgumentException("電子信箱核對失敗，無法重設密碼");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("會員密碼已成功重設: username={}", user.getUsername());
    }

    /**
     * 內部簽發零信任雙 Token (短效 Access Token + 長效 Refresh Token) 並封裝回傳物件
     */
    private AuthResponse createDualTokenAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());
        String refreshJti = tokenProvider.getJtiFromToken(refreshToken);

        // 註冊有效 Refresh Token 至狀態管理服務
        tokenRevocationService.registerRefreshToken(refreshJti, user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)             // 向下相容單一讀取端點
                .accessToken(accessToken)       // 短效存取權杖 (15 分鐘)
                .refreshToken(refreshToken)     // 長效更新權杖 (7 天)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
