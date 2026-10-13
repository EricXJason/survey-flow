/**
 * ============================================================================
 * 檔案名稱: AuthService.java
 * 所屬模組: Service 業務邏輯層 - 認證模組
 * 責任描述: 處理會員註冊 (Argon2id 密碼雜湊)、身分登入校驗、JWT 雙 Token 簽發與旋轉更新
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, RefreshTokenRepository, PasswordEncoder, TokenProvider
 *   - 下游消費: AuthController
 * 邊界處理: 密碼校驗失敗時拋出 BadCredentialsException，使用者重複註冊時拋出 IllegalArgumentException
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.auth.AuthResponse;
import com.surveyflow.dto.auth.LoginRequest;
import com.surveyflow.dto.auth.RefreshTokenRequest;
import com.surveyflow.dto.auth.RegisterRequest;
import com.surveyflow.entity.RefreshToken;
import com.surveyflow.entity.User;
import com.surveyflow.repository.RefreshTokenRepository;
import com.surveyflow.repository.UserRepository;
import com.surveyflow.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
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
     * 透過長效 Refresh Token 換發新的 Access Token (旋轉機制)
     * @param request 刷新請求
     * @return 換發後之 AuthResponse
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.getRefreshToken();
        if (!tokenProvider.validateToken(refreshTokenStr)) {
            throw new IllegalArgumentException("Refresh Token 無效或已過期，請重新登入");
        }

        String username = tokenProvider.getUsernameFromToken(refreshTokenStr);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("查無所屬使用者"));

        return createAuthResponse(user);
    }

    /**
     * 處理會員主動登出，撤銷名下所有 Refresh Token
     * @param username 當前登入帳號
     */
    @Transactional
    public void logout(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            refreshTokenRepository.revokeAllByUser(user);
            log.info("會員已成功登出並撤銷權杖: username={}", username);
        });
    }

    /**
     * 內部簽發雙 Token 並封裝回傳物件
     */
    private AuthResponse createAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

        // 持久化記錄 Refresh Token
        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
