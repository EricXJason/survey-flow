/**
 * ============================================================================
 * 檔案名稱: AuthServiceTest.java
 * 測試對象: AuthService (註冊、信箱驗證、登入、Token 換發、登出、忘記 / 重設密碼)
 * 測試方式: JUnit 5 + Mockito 純單元測試；所有 Repository、MailService、Token 元件皆以 Mock 取代，
 *           不連線資料庫、不寄信。以 @Nested 依「被測方法」分組，每個測試以 Given / When / Then 三段撰寫。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.common.enums.VerificationTokenType;
import org.jasonproduction.surveyflow.common.exception.AccountNotVerifiedException;
import org.jasonproduction.surveyflow.dto.auth.AuthResponse;
import org.jasonproduction.surveyflow.dto.auth.ForgetPasswordRequest;
import org.jasonproduction.surveyflow.dto.auth.LoginRequest;
import org.jasonproduction.surveyflow.dto.auth.RefreshTokenRequest;
import org.jasonproduction.surveyflow.dto.auth.RegisterRequest;
import org.jasonproduction.surveyflow.dto.auth.ResetPasswordRequest;
import org.jasonproduction.surveyflow.dto.auth.VerifyEmailRequest;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.entity.VerificationToken;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.jasonproduction.surveyflow.repository.VerificationTokenRepository;
import org.jasonproduction.surveyflow.security.LoginAttemptLimiter;
import org.jasonproduction.surveyflow.security.TokenProvider;
import org.jasonproduction.surveyflow.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private MailService mailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenProvider tokenProvider;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private LoginAttemptLimiter loginAttemptLimiter;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuthService authService;

    /**
     * 每個測試前以建構子顯式建立受測物件 (與 Spring 實際注入方式相同)
     */
    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, verificationTokenRepository, mailService,
                passwordEncoder, tokenProvider, tokenRevocationService, loginAttemptLimiter, eventPublisher);
    }

    /**
     * 建立指定狀態之測試會員
     * @param status 帳號狀態
     * @return 會員實體 (帳號 testuser、信箱 test@example.com)
     */
    private User userWithStatus(UserStatus status) {
        return User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed_password")
                .fullName("測試人員")
                .phone("0912345678")
                .identityCard("A123456789")
                .role(RoleType.ROLE_USER)
                .status(status)
                .build();
    }

    /**
     * 建立一筆未使用之驗證碼
     * @param type 驗證碼用途
     * @param expiresAt 到期時間
     * @return 驗證碼實體 (信箱 test@example.com、驗證碼 123456)
     */
    private VerificationToken tokenOf(VerificationTokenType type, LocalDateTime expiresAt) {
        return VerificationToken.builder()
                .id(10L)
                .email("test@example.com")
                .code("123456")
                .tokenType(type)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();
    }

    /**
     * 讓 TokenProvider 簽發固定字串之雙 Token，並回傳 jti
     */
    private void stubTokenIssuing() {
        when(tokenProvider.generateAccessToken("testuser", "ROLE_USER")).thenReturn("new_access");
        when(tokenProvider.generateRefreshToken("testuser")).thenReturn("new_refresh");
    }

    @Nested
    @DisplayName("register 會員註冊")
    class Register {

        /**
         * 建立註冊請求 (帳號 newuser、信箱 new@example.com，欄位前後帶空白以驗證 trim)
         * @return 註冊請求
         */
        private RegisterRequest request() {
            return RegisterRequest.builder()
                    .username(" newuser ")
                    .email(" new@example.com ")
                    .password("Password123!")
                    .fullName(" 新會員 ")
                    .phone(" 0912345678 ")
                    .identityCard(null)
                    .build();
        }

        @Test
        @DisplayName("全新帳號：建立待驗證會員 (欄位已 trim、密碼已雜湊)，寫入 15 分鐘驗證碼並寄出啟用信")
        void newAccount_CreatesPendingUserAndSendsMail() {
            // Given
            when(userRepository.findByUsernameNative("newuser")).thenReturn(Optional.empty());
            when(userRepository.findByEmailNative("new@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Password123!")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            AuthResponse response = authService.register(request());

            // Then
            ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUser.capture());
            assertEquals("newuser", savedUser.getValue().getUsername());
            assertEquals("new@example.com", savedUser.getValue().getEmail());
            assertEquals("新會員", savedUser.getValue().getFullName());
            assertEquals("0912345678", savedUser.getValue().getPhone());
            assertEquals("encoded", savedUser.getValue().getPasswordHash());
            assertEquals(UserStatus.PENDING_VERIFICATION, savedUser.getValue().getStatus());

            ArgumentCaptor<VerificationToken> savedToken = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(savedToken.capture());
            assertEquals(VerificationTokenType.REGISTRATION, savedToken.getValue().getTokenType());
            assertTrue(savedToken.getValue().getCode().matches("\\d{6}"), "驗證碼必須為 6 位數字");
            assertTrue(savedToken.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(14)));

            verify(mailService).sendRegistrationVerificationMail(eq("new@example.com"), eq("新會員"), eq(savedToken.getValue().getCode()));
            assertEquals("newuser", response.getUsername());
            assertNull(response.getAccessToken(), "註冊後尚未驗證信箱，不可簽發 Token");
        }

        @Test
        @DisplayName("帳號已存在且為待驗證：覆寫原註冊資料並重新寄送驗證碼，而非拋出重複錯誤")
        void pendingUsername_OverwritesRegistration() {
            // Given
            User pending = userWithStatus(UserStatus.PENDING_VERIFICATION);
            when(userRepository.findByUsernameNative("newuser")).thenReturn(Optional.of(pending));
            when(userRepository.findByEmailNative("new@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Password123!")).thenReturn("encoded");
            when(userRepository.save(pending)).thenReturn(pending);

            // When
            authService.register(request());

            // Then
            assertEquals("new@example.com", pending.getEmail());
            assertEquals("encoded", pending.getPasswordHash());
            verify(mailService).sendRegistrationVerificationMail(eq("new@example.com"), eq("新會員"), anyString());
        }

        @Test
        @DisplayName("信箱已存在且為待驗證：同樣允許覆寫")
        void pendingEmail_OverwritesRegistration() {
            // Given
            User pending = userWithStatus(UserStatus.PENDING_VERIFICATION);
            when(userRepository.findByUsernameNative("newuser")).thenReturn(Optional.empty());
            when(userRepository.findByEmailNative("new@example.com")).thenReturn(Optional.of(pending));
            when(passwordEncoder.encode("Password123!")).thenReturn("encoded");
            when(userRepository.save(pending)).thenReturn(pending);

            // When
            authService.register(request());

            // Then
            assertEquals("newuser", pending.getUsername());
        }

        @Test
        @DisplayName("帳號已被啟用中會員使用：拋出 IllegalArgumentException，不寫入任何資料")
        void activeUsername_Throws() {
            // Given
            when(userRepository.findByUsernameNative("newuser")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            when(userRepository.findByEmailNative("new@example.com")).thenReturn(Optional.empty());

            // When / Then
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(request()));
            assertEquals("此帳號已被註冊，請更換其他帳號", ex.getMessage());
            verify(userRepository, never()).save(any());
            verify(mailService, never()).sendRegistrationVerificationMail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("信箱已被啟用中會員使用：拋出 IllegalArgumentException")
        void activeEmail_Throws() {
            // Given
            when(userRepository.findByUsernameNative("newuser")).thenReturn(Optional.empty());
            when(userRepository.findByEmailNative("new@example.com")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));

            // When / Then
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(request()));
            assertEquals("此電子信箱已被使用，請更換其他信箱", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("verifyRegistrationEmail 信箱驗證開通")
    class VerifyRegistrationEmail {

        private final VerifyEmailRequest request = new VerifyEmailRequest(" test@example.com ", " 123456 ");

        @Test
        @DisplayName("驗證碼正確且未過期：帳號轉為 ACTIVE、驗證碼核銷、簽發雙 Token")
        void validCode_ActivatesAndIssuesTokens() {
            // Given
            VerificationToken token = tokenOf(VerificationTokenType.REGISTRATION, LocalDateTime.now().plusMinutes(10));
            User pending = userWithStatus(UserStatus.PENDING_VERIFICATION);
            when(verificationTokenRepository.findValidTokenForUpdate("test@example.com", "123456", "REGISTRATION")).thenReturn(Optional.of(token));
            when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(pending));
            stubTokenIssuing();

            // When
            AuthResponse response = authService.verifyRegistrationEmail(request);

            // Then
            assertEquals(UserStatus.ACTIVE, pending.getStatus());
            assertTrue(token.getIsUsed());
            assertEquals("new_access", response.getAccessToken());
            assertEquals("new_refresh", response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());
        }

        @Test
        @DisplayName("查無驗證碼：拋出 IllegalArgumentException")
        void codeNotFound_Throws() {
            // Given
            when(verificationTokenRepository.findValidTokenForUpdate(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.verifyRegistrationEmail(request));
        }

        @Test
        @DisplayName("驗證碼已過期：拋出 IllegalArgumentException 且帳號維持待驗證")
        void expiredCode_Throws() {
            // Given
            VerificationToken expired = tokenOf(VerificationTokenType.REGISTRATION, LocalDateTime.now().minusMinutes(1));
            when(verificationTokenRepository.findValidTokenForUpdate(anyString(), anyString(), anyString())).thenReturn(Optional.of(expired));

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.verifyRegistrationEmail(request));
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("resendRegistrationCode 補發啟用碼")
    class ResendRegistrationCode {

        @Test
        @DisplayName("待驗證帳號：產生新驗證碼並重新寄信")
        void pendingUser_SendsNewCode() {
            // Given
            when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(userWithStatus(UserStatus.PENDING_VERIFICATION)));

            // When
            authService.resendRegistrationCode(" test@example.com ");

            // Then
            verify(verificationTokenRepository).save(any(VerificationToken.class));
            verify(mailService).sendRegistrationVerificationMail(eq("test@example.com"), eq("測試人員"), anyString());
        }

        @Test
        @DisplayName("帳號已開通：拋出 IllegalArgumentException 且不寄信")
        void activeUser_Throws() {
            // Given
            when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.resendRegistrationCode("test@example.com"));
            verify(mailService, never()).sendRegistrationVerificationMail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("查無信箱：拋出 IllegalArgumentException")
        void unknownEmail_Throws() {
            // Given
            when(userRepository.findByEmailNative("nobody@example.com")).thenReturn(Optional.empty());

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.resendRegistrationCode("nobody@example.com"));
        }
    }

    @Nested
    @DisplayName("login 帳密登入")
    class Login {

        private final LoginRequest request = new LoginRequest("testuser", "User1234!");

        @Test
        @DisplayName("帳密正確且帳號啟用：清除失敗次數並簽發雙 Token")
        void validCredentials_IssuesTokens() {
            // Given
            User active = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(active));
            when(passwordEncoder.matches("User1234!", "hashed_password")).thenReturn(true);
            stubTokenIssuing();

            // When
            AuthResponse response = authService.login(request);

            // Then
            verify(loginAttemptLimiter).recordSuccess("testuser");
            assertEquals("new_access", response.getAccessToken());
            assertEquals("testuser", response.getUsername());
            assertEquals("A123456789", response.getIdentityCard());
            assertEquals("ROLE_USER", response.getRole());
        }

        @Test
        @DisplayName("密碼錯誤：計入失敗次數並拋出 BadCredentialsException")
        void wrongPassword_RecordsFailure() {
            // Given
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            when(passwordEncoder.matches("User1234!", "hashed_password")).thenReturn(false);

            // When / Then
            assertThrows(BadCredentialsException.class, () -> authService.login(request));
            verify(loginAttemptLimiter).recordFailure("testuser");
            verify(loginAttemptLimiter, never()).recordSuccess(anyString());
            verify(eventPublisher).publishEvent(new AuditEvent(AuditAction.LOGIN_FAILED, "testuser", null, null, "密碼錯誤"));
        }

        @Test
        @DisplayName("帳號不存在：同樣計入失敗次數，且錯誤訊息與密碼錯誤相同 (不洩漏帳號是否存在)")
        void unknownUser_RecordsFailureWithSameMessage() {
            // Given
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.empty());

            // When
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(request));

            // Then
            assertEquals("帳號或密碼錯誤", ex.getMessage());
            verify(loginAttemptLimiter).recordFailure("testuser");
            verify(eventPublisher).publishEvent(new AuditEvent(AuditAction.LOGIN_FAILED, "testuser", null, null, "帳號不存在"));
        }

        @Test
        @DisplayName("帳號鎖定中：直接拒絕，不查詢資料庫")
        void lockedAccount_ThrowsWithoutQuery() {
            // Given
            doThrow(new LockedException("登入失敗次數過多，請於 15 分鐘後再試")).when(loginAttemptLimiter).ensureNotLocked("testuser");

            // When / Then
            assertThrows(LockedException.class, () -> authService.login(request));
            verify(userRepository, never()).findByUsernameNative(anyString());
        }

        @Test
        @DisplayName("帳號尚未驗證信箱：拋出 AccountNotVerifiedException 並帶回信箱")
        void pendingUser_ThrowsNotVerified() {
            // Given
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.PENDING_VERIFICATION)));
            when(passwordEncoder.matches("User1234!", "hashed_password")).thenReturn(true);

            // When
            AccountNotVerifiedException ex = assertThrows(AccountNotVerifiedException.class, () -> authService.login(request));

            // Then
            assertEquals("test@example.com", ex.getEmail());
        }

        @Test
        @DisplayName("帳號已停權：拋出 IllegalArgumentException")
        void suspendedUser_Throws() {
            // Given
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.SUSPENDED)));
            when(passwordEncoder.matches("User1234!", "hashed_password")).thenReturn(true);

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.login(request));
            verify(tokenProvider, never()).generateAccessToken(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("refreshToken Refresh Token 換發")
    class RefreshToken {

        private final Instant issuedAt = Instant.parse("2026-11-01T00:00:00Z");

        /**
         * 讓 TokenProvider 將 token 視為合法 Refresh Token
         * @param token Token 字串
         */
        private void stubValidRefreshToken(String token) {
            when(tokenProvider.validateToken(token)).thenReturn(true);
            when(tokenProvider.isRefreshToken(token)).thenReturn(true);
            when(tokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
            when(tokenProvider.getJtiFromToken(token)).thenReturn("old_jti");
        }

        /**
         * 讓輪替檢查通過或失敗所需之共用 Stub
         * @param token Token 字串
         * @param rotated 輪替結果
         */
        private void stubRotation(String token, boolean rotated) {
            when(tokenProvider.getIssuedAt(token)).thenReturn(issuedAt);
            when(tokenProvider.getRemainingTtlMs(token)).thenReturn(1000L);
            when(tokenRevocationService.rotateRefreshToken("old_jti", "testuser", issuedAt, 1000L)).thenReturn(rotated);
        }

        @Test
        @DisplayName("Body 帶有效 Refresh Token：輪替成功後簽發新雙 Token")
        void validBodyToken_RotatesAndIssues() {
            // Given
            stubValidRefreshToken("refresh_in_body");
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            stubRotation("refresh_in_body", true);
            stubTokenIssuing();

            // When
            AuthResponse response = authService.refreshToken(new RefreshTokenRequest(" refresh_in_body "), "cookie_ignored");

            // Then
            assertEquals("new_access", response.getAccessToken());
            assertEquals("new_refresh", response.getRefreshToken());
        }

        @Test
        @DisplayName("Body 未帶 Token：改用 Cookie 中之 Refresh Token")
        void noBodyToken_FallsBackToCookie() {
            // Given
            stubValidRefreshToken("refresh_in_cookie");
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            stubRotation("refresh_in_cookie", true);
            stubTokenIssuing();

            // When
            AuthResponse response = authService.refreshToken(null, "refresh_in_cookie");

            // Then
            assertEquals("new_access", response.getAccessToken());
        }

        @Test
        @DisplayName("Body 與 Cookie 都沒有 Token：拋出 BadCredentialsException")
        void missingToken_Throws() {
            assertThrows(BadCredentialsException.class, () -> authService.refreshToken(new RefreshTokenRequest(" "), null));
        }

        @Test
        @DisplayName("Token 驗簽失敗或已過期：拋出 BadCredentialsException")
        void invalidToken_Throws() {
            // Given
            when(tokenProvider.validateToken("broken")).thenReturn(false);

            // When / Then
            assertThrows(BadCredentialsException.class, () -> authService.refreshToken(null, "broken"));
        }

        @Test
        @DisplayName("拿 Access Token 冒充 Refresh Token：拋出 BadCredentialsException")
        void accessTokenUsedAsRefresh_Throws() {
            // Given
            when(tokenProvider.validateToken("access_token")).thenReturn(true);
            when(tokenProvider.isRefreshToken("access_token")).thenReturn(false);

            // When / Then
            assertThrows(BadCredentialsException.class, () -> authService.refreshToken(null, "access_token"));
        }

        @Test
        @DisplayName("帳號已停權：拋出 IllegalArgumentException 且不進行輪替")
        void suspendedUser_Throws() {
            // Given
            stubValidRefreshToken("refresh");
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.SUSPENDED)));

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.refreshToken(null, "refresh"));
            verify(tokenRevocationService, never()).rotateRefreshToken(anyString(), anyString(), any(), anyLong());
        }

        @Test
        @DisplayName("輪替被拒 (重放或已整批作廢)：拋出 BadCredentialsException 且不簽發新 Token")
        void rotationRejected_ThrowsWithoutIssuing() {
            // Given
            stubValidRefreshToken("replayed");
            when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));
            stubRotation("replayed", false);

            // When / Then
            assertThrows(BadCredentialsException.class, () -> authService.refreshToken(null, "replayed"));
            verify(tokenProvider, never()).generateAccessToken(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("logout 登出")
    class Logout {

        @Test
        @DisplayName("同時帶 Access Token 與 Refresh Token：兩者 jti 皆以剩餘效期加入黑名單")
        void bothTokens_RevokesBoth() {
            // Given
            when(tokenProvider.validateToken("access")).thenReturn(true);
            when(tokenProvider.getJtiFromToken("access")).thenReturn("access_jti");
            when(tokenProvider.getRemainingTtlMs("access")).thenReturn(800L);
            when(tokenProvider.validateToken("refresh")).thenReturn(true);
            when(tokenProvider.getJtiFromToken("refresh")).thenReturn("refresh_jti");
            when(tokenProvider.getRemainingTtlMs("refresh")).thenReturn(9000L);

            // When
            authService.logout("testuser", "Bearer access", "refresh");

            // Then
            verify(tokenRevocationService).revokeToken("access_jti", 800L);
            verify(tokenRevocationService).revokeToken("refresh_jti", 9000L);
        }

        @Test
        @DisplayName("驗簽失敗之 Token 不寫入黑名單 (避免黑名單被偽造字串灌爆)")
        void invalidToken_NotRevoked() {
            // Given
            when(tokenProvider.validateToken("forged")).thenReturn(false);

            // When
            authService.logout("testuser", "forged", null);

            // Then
            verify(tokenRevocationService, never()).revokeToken(anyString(), anyLong());
        }

        @Test
        @DisplayName("未帶任何 Token：不呼叫黑名單，仍正常完成")
        void noTokens_DoesNothing() {
            // When
            authService.logout("testuser", null, " ");

            // Then
            verify(tokenProvider, never()).validateToken(anyString());
        }
    }

    @Nested
    @DisplayName("processForgotPassword / processResetPassword 忘記密碼")
    class ForgotAndResetPassword {

        @Test
        @DisplayName("申請忘記密碼：寫入 FORGOT_PASSWORD 驗證碼並寄出重設信")
        void forgotPassword_SendsResetMail() {
            // Given
            when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE)));

            // When
            authService.processForgotPassword(ForgetPasswordRequest.builder().email(" test@example.com ").build());

            // Then
            ArgumentCaptor<VerificationToken> savedToken = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(savedToken.capture());
            assertEquals(VerificationTokenType.FORGOT_PASSWORD, savedToken.getValue().getTokenType());
            verify(mailService).sendPasswordResetMail("test@example.com", savedToken.getValue().getCode());
        }

        @Test
        @DisplayName("申請忘記密碼但信箱不存在：拋出 IllegalArgumentException")
        void forgotPassword_UnknownEmail_Throws() {
            // Given
            when(userRepository.findByEmailNative("nobody@example.com")).thenReturn(Optional.empty());

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> authService.processForgotPassword(ForgetPasswordRequest.builder().email("nobody@example.com").build()));
        }

        @Test
        @DisplayName("重設密碼成功：更新密碼雜湊、核銷驗證碼，並作廢該使用者此前所有 Token")
        void resetPassword_UpdatesHashAndRevokesAllTokens() {
            // Given
            VerificationToken token = tokenOf(VerificationTokenType.FORGOT_PASSWORD, LocalDateTime.now().plusMinutes(10));
            User user = userWithStatus(UserStatus.ACTIVE);
            when(verificationTokenRepository.findValidTokenForUpdate("test@example.com", "123456", "FORGOT_PASSWORD")).thenReturn(Optional.of(token));
            when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NewPass123!")).thenReturn("new_hash");

            // When
            authService.processResetPassword(ResetPasswordRequest.builder()
                    .email("test@example.com").code("123456").newPassword("NewPass123!").build());

            // Then
            assertEquals("new_hash", user.getPasswordHash());
            assertTrue(token.getIsUsed());
            verify(tokenRevocationService).revokeAllTokensOf("testuser");
        }

        @Test
        @DisplayName("重設密碼但驗證碼已過期：拋出 IllegalArgumentException 且密碼不變")
        void resetPassword_ExpiredCode_Throws() {
            // Given
            VerificationToken expired = tokenOf(VerificationTokenType.FORGOT_PASSWORD, LocalDateTime.now().minusMinutes(1));
            when(verificationTokenRepository.findValidTokenForUpdate(anyString(), anyString(), anyString())).thenReturn(Optional.of(expired));

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> authService.processResetPassword(ResetPasswordRequest.builder()
                    .email("test@example.com").code("123456").newPassword("NewPass123!").build()));
            verify(passwordEncoder, never()).encode(anyString());
        }
    }
}
