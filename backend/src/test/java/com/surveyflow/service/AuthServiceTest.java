/**
 * ============================================================================
 * 檔案名稱: AuthServiceTest.java
 * 所屬模組: Test 測試層 - 會員認證業務單元測試
 * 責任描述: 採用 JUnit 5 與 Mockito 對 AuthService 進行完整單元測試覆蓋，驗證零信任雙 Token 簽發、RTR 輪替、主動吊銷、信箱驗證與忘記密碼 OTP
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: AuthService
 *   - 模擬對象: UserRepository, VerificationTokenRepository, MailService, PasswordEncoder, TokenProvider, TokenRevocationService
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed_argon2_password")
                .fullName("測試人員")
                .role(RoleType.ROLE_USER)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("註冊測試: 正常輸入參數時應成功建立使用者 (待驗證狀態) 並寄發 6 碼啟用碼")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("Password123!")
                .fullName("新註冊用戶")
                .phone("0912345678")
                .build();

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmailNative("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
        verify(verificationTokenRepository, times(1)).save(any(VerificationToken.class));
        verify(mailService, times(1)).sendRegistrationVerificationMail(eq("test@example.com"), eq("測試人員"), anyString());
    }

    @Test
    @DisplayName("信箱啟用測試: 正確輸入 6 碼驗證碼時應開通帳號並簽發雙 Token 自動登入")
    void verifyRegistrationEmail_Success() {
        VerifyEmailRequest request = new VerifyEmailRequest("test@example.com", "123456");
        VerificationToken token = VerificationToken.builder()
                .id(1L)
                .email("test@example.com")
                .code("123456")
                .tokenType("REGISTRATION")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .isUsed(false)
                .build();

        when(verificationTokenRepository.findValidToken("test@example.com", "123456", "REGISTRATION"))
                .thenReturn(Optional.of(token));
        when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(tokenProvider.generateAccessToken(anyString(), anyString())).thenReturn("mock_access_token");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("mock_refresh_token");
        when(tokenProvider.getJtiFromToken("mock_refresh_token")).thenReturn("mock_refresh_jti");

        AuthResponse response = authService.verifyRegistrationEmail(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
        assertTrue(token.getIsUsed());
        verify(verificationTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("登入測試: 密碼正確且狀態為 ACTIVE 時應成功簽發雙 Token")
    void login_Success() {
        LoginRequest request = new LoginRequest("testuser", "Password123!");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123!", "hashed_argon2_password")).thenReturn(true);
        when(tokenProvider.generateAccessToken("testuser", "ROLE_USER")).thenReturn("mock_access_token");
        when(tokenProvider.generateRefreshToken("testuser")).thenReturn("mock_refresh_token");
        when(tokenProvider.getJtiFromToken("mock_refresh_token")).thenReturn("mock_refresh_jti");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
        verify(tokenRevocationService, times(1)).registerRefreshToken("mock_refresh_jti", "testuser");
    }

    @Test
    @DisplayName("忘記密碼測試: 僅提供 Email 應成功產生 6 碼 OTP 並發送驗證信")
    void forgotPassword_Success() {
        ForgetPasswordRequest request = ForgetPasswordRequest.builder()
                .email("test@example.com")
                .build();

        when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(sampleUser));

        assertDoesNotThrow(() -> authService.processForgotPassword(request));
        verify(verificationTokenRepository, times(1)).save(any(VerificationToken.class));
        verify(mailService, times(1)).sendPasswordResetMail(eq("test@example.com"), anyString());
    }

    @Test
    @DisplayName("重設密碼測試: 驗證碼正確且未過期時應成功更新密碼")
    void resetPassword_Success() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("test@example.com")
                .code("654321")
                .newPassword("NewSecret123!")
                .build();

        VerificationToken token = VerificationToken.builder()
                .id(2L)
                .email("test@example.com")
                .code("654321")
                .tokenType("FORGOT_PASSWORD")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .isUsed(false)
                .build();

        when(verificationTokenRepository.findValidToken("test@example.com", "654321", "FORGOT_PASSWORD"))
                .thenReturn(Optional.of(token));
        when(userRepository.findByEmailNative("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("new_encoded_hash");

        assertDoesNotThrow(() -> authService.processResetPassword(request));
        assertEquals("new_encoded_hash", sampleUser.getPasswordHash());
        assertTrue(token.getIsUsed());
        verify(userRepository, times(1)).save(sampleUser);
        verify(verificationTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("Token 換發測試: 有效 Refresh Token 應觸發 RTR 輪替並簽發新雙 Token")
    void refreshToken_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");

        when(tokenProvider.validateToken("valid_refresh_token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("valid_refresh_token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid_refresh_token")).thenReturn("testuser");
        when(tokenProvider.getJtiFromToken("valid_refresh_token")).thenReturn("old_jti");
        when(tokenProvider.getRemainingTtlMs("valid_refresh_token")).thenReturn(600000000L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(tokenProvider.generateAccessToken("testuser", "ROLE_USER")).thenReturn("new_access_token");
        when(tokenProvider.generateRefreshToken("testuser")).thenReturn("new_refresh_token");
        when(tokenProvider.getJtiFromToken("new_refresh_token")).thenReturn("new_jti");
        when(tokenRevocationService.rotateRefreshToken(eq("old_jti"), eq("new_jti"), eq("testuser"), anyLong())).thenReturn(true);

        AuthResponse response = authService.refreshToken(request, null);

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals("new_refresh_token", response.getRefreshToken());
        verify(tokenRevocationService, times(1)).rotateRefreshToken(eq("old_jti"), eq("new_jti"), eq("testuser"), anyLong());
    }

    @Test
    @DisplayName("登出測試: 攜帶 Bearer Token 登出時應成功將 JTI 加入黑名單")
    void logout_WithBearerToken_Success() {
        when(tokenProvider.getJtiFromToken("sample_access_jwt")).thenReturn("jwt_jti_123");
        when(tokenProvider.getRemainingTtlMs("sample_access_jwt")).thenReturn(800000L);

        assertDoesNotThrow(() -> authService.logout("testuser", "Bearer sample_access_jwt"));
        verify(tokenRevocationService, times(1)).revokeToken("jwt_jti_123", 800000L);
    }
}
