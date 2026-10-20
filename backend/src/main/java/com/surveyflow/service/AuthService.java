/**
 * ============================================================================
 * 檔案名稱: AuthService.java
 * 所屬模組: Service 業務邏輯層 - 認證模組
 * 責任描述: 處理會員註冊 (Argon2id 密碼雜湊)、身分登入校驗、JWT 單一高安全 Token 簽發與無狀態驗證
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, PasswordEncoder, TokenProvider
 *   - 下游消費: AuthController
 * 邊界處理: 密碼校驗失敗時拋出 BadCredentialsException，使用者重複註冊時拋出 IllegalArgumentException
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    /**
     * 處理新會員註冊業務
     * @param request 註冊參數
     * @return 註冊成功後直接簽發之 AuthResponse
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

        return createAuthResponse(user);
    }

    /**
     * 處理會員登入驗證
     * @param request 登入請求
     * @return 登入成功之 AuthResponse
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
        return createAuthResponse(user);
    }

    /**
     * 透過現有有效 Token 續約或重新核發單一 Token (簡化架構相容端點)
     * @param request 刷新請求
     * @return 重新簽發之 AuthResponse
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.getRefreshToken();
        if (!tokenProvider.validateToken(tokenStr)) {
            throw new IllegalArgumentException("憑證無效或已過期，請重新登入");
        }

        String username = tokenProvider.getUsernameFromToken(tokenStr);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("查無所屬使用者"));

        return createAuthResponse(user);
    }

    /**
     * 處理會員主動登出 (無狀態 JWT 由客戶端丟棄，後端記錄審計日誌)
     * @param username 當前登入帳號
     */
    @Transactional
    public void logout(String username) {
        log.info("會員已成功登出 (無狀態會話終止): username={}", username);
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
     * 內部簽發單一高安全 JWT Token 並封裝回傳物件
     */
    private AuthResponse createAuthResponse(User user) {
        String token = tokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .accessToken(token)      // 向下相容既有讀取
                .refreshToken(token)     // 向下相容
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
