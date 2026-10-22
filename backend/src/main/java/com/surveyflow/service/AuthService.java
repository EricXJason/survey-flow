/**
 * ============================================================================
 * 檔案名稱: AuthService.java
 * 所屬模組: Service 業務邏輯層 - 認證模組
 * 責任描述: 負責會員註冊 (Email 驗證啟用)、身分登入校驗、零信任雙 Token (短效 Access Token + 長效 Refresh Token) 簽發、RTR 輪替、主動吊銷及忘記密碼 OTP 重設全流程
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, VerificationTokenRepository, MailService, PasswordEncoder, TokenProvider, TokenRevocationService
 *   - 下游消費: AuthController
 * 邊界處理: 驗證碼時效 15 分鐘；未驗證信箱阻斷登入；發信異常友善處理
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.auth.*;
import com.surveyflow.entity.User;
import com.surveyflow.entity.VerificationToken;
import com.surveyflow.repository.UserRepository;
import com.surveyflow.repository.VerificationTokenRepository;
import com.surveyflow.security.TokenProvider;
import com.surveyflow.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final TokenRevocationService tokenRevocationService;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 處理新會員註冊業務 (寄送 Email 驗證碼，帳號暫置於待驗證狀態)
     * @param request 註冊參數
     * @return 註冊成功資訊
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
                .username(request.getUsername().trim())
                .email(request.getEmail().trim())
                .passwordHash(encodedPassword)
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .identityCard(request.getIdentityCard() != null ? request.getIdentityCard().trim() : null)
                .role(RoleType.ROLE_USER)
                .status("PENDING_VERIFICATION")
                .build();

        user = userRepository.save(user);

        // 生成 6 碼數字 OTP 驗證碼 (15 分鐘時效)
        String code = generateSixDigitCode();
        VerificationToken token = VerificationToken.builder()
                .email(user.getEmail())
                .code(code)
                .tokenType("REGISTRATION")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .isUsed(false)
                .build();
        verificationTokenRepository.save(token);

        // 寄送註冊啟用信
        mailService.sendRegistrationVerificationMail(user.getEmail(), user.getFullName(), code);
        log.info("新會員註冊成功並發送啟用驗證信: username={}, email={}, code={}", user.getUsername(), user.getEmail(), code);

        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 處理會員註冊信箱 6 碼啟用碼核驗 (驗證通過轉為 ACTIVE 並簽發雙 Token 自動登入)
     * @param request 包含信箱與 6 碼啟用碼
     * @return 登入雙 Token 物件
     */
    @Transactional
    public AuthResponse verifyRegistrationEmail(VerifyEmailRequest request) {
        VerificationToken token = verificationTokenRepository.findValidToken(
                request.getEmail().trim(), request.getCode().trim(), "REGISTRATION"
        ).orElseThrow(() -> new IllegalArgumentException("驗證碼錯誤或不存在，請重新核對"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("啟用驗證碼已過期或已使用，請重新取得驗證碼");
        }

        User user = userRepository.findByEmailNative(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱註冊之會員帳號"));

        user.setStatus("ACTIVE");
        userRepository.save(user);

        token.setIsUsed(true);
        verificationTokenRepository.save(token);

        log.info("會員信箱驗證成功並正式開通帳號: username={}, email={}", user.getUsername(), user.getEmail());
        return createDualTokenAuthResponse(user);
    }

    /**
     * 重新寄送註冊啟用驗證信
     * @param email 目標信箱
     */
    @Transactional
    public void resendRegistrationCode(String email) {
        User user = userRepository.findByEmailNative(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱註冊之會員帳號"));

        if ("ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("此帳號已通過驗證開通，請直接登入");
        }

        String code = generateSixDigitCode();
        VerificationToken token = VerificationToken.builder()
                .email(user.getEmail())
                .code(code)
                .tokenType("REGISTRATION")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .isUsed(false)
                .build();
        verificationTokenRepository.save(token);

        mailService.sendRegistrationVerificationMail(user.getEmail(), user.getFullName(), code);
        log.info("已重新發送註冊啟用驗證信: email={}, code={}", user.getEmail(), code);
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

        if ("PENDING_VERIFICATION".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("該帳號尚未完成電子郵件驗證，請先至信箱查收 6 位數驗證碼開通帳號");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("該帳號目前處於非啟用狀態，請聯繫系統管理員");
        }

        log.info("會員登入成功: username={}", user.getUsername());
        return createDualTokenAuthResponse(user);
    }

    /**
     * 透過長效 Refresh Token 輪替換發新 Access Token 與新 Refresh Token (RTR 機制)
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

        // RTR 輪替: 舊 Token 加入黑名單，新 Token 加入白名單
        tokenRevocationService.rotateRefreshToken(oldJti, newRefreshJti, username, remainingTtl);
        log.info("Refresh Token 輪替成功: username={}, oldJti={}, newJti={}", username, oldJti, newRefreshJti);

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

    @Transactional
    public void logout(String username) {
        logout(username, null);
    }

    /**
     * 處理忘記密碼申請 (僅需提供 Email，生成 6 碼 OTP 並發送郵件)
     * @param request 包含信箱之請求
     */
    @Transactional
    public void processForgotPassword(ForgetPasswordRequest request) {
        User user = userRepository.findByEmailNative(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱綁定之會員帳號，請確認輸入是否正確"));

        String code = generateSixDigitCode();
        VerificationToken token = VerificationToken.builder()
                .email(user.getEmail())
                .code(code)
                .tokenType("FORGOT_PASSWORD")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .isUsed(false)
                .build();
        verificationTokenRepository.save(token);

        mailService.sendPasswordResetMail(user.getEmail(), code);
        log.info("忘記密碼驗證信已成功發送: email={}, code={}", user.getEmail(), code);
    }

    /**
     * 處理重設新密碼 (校驗信箱、6 碼驗證碼與新密碼)
     * @param request 包含信箱、驗證碼與新密碼之請求
     */
    @Transactional
    public void processResetPassword(ResetPasswordRequest request) {
        VerificationToken token = verificationTokenRepository.findValidToken(
                request.getEmail().trim(), request.getCode().trim(), "FORGOT_PASSWORD"
        ).orElseThrow(() -> new IllegalArgumentException("驗證碼錯誤或不存在，請重新核對"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("此驗證碼已過期或已使用，請重新申請忘記密碼");
        }

        User user = userRepository.findByEmailNative(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱對應之會員帳號"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setIsUsed(true);
        verificationTokenRepository.save(token);

        log.info("會員密碼已透過信箱驗證碼成功重設: username={}, email={}", user.getUsername(), user.getEmail());
    }

    /**
     * 內部簽發零信任雙 Token 並封裝回傳物件
     */
    private AuthResponse createDualTokenAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());
        String refreshJti = tokenProvider.getJtiFromToken(refreshToken);

        tokenRevocationService.registerRefreshToken(refreshJti, user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)
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

    /**
     * 生成安全 6 碼數字 OTP 驗證碼
     */
    private String generateSixDigitCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }
}
