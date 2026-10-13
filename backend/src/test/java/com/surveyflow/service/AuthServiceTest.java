/**
 * ============================================================================
 * 檔案名稱: AuthServiceTest.java
 * 所屬模組: Test 測試層 - 會員認證業務單元測試
 * 責任描述: 採用 JUnit 5 與 Mockito 對 AuthService 進行完整單元測試覆蓋
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: AuthService
 *   - 模擬對象: UserRepository, RefreshTokenRepository, PasswordEncoder, TokenProvider
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.auth.AuthResponse;
import com.surveyflow.dto.auth.LoginRequest;
import com.surveyflow.dto.auth.RegisterRequest;
import com.surveyflow.entity.User;
import com.surveyflow.repository.RefreshTokenRepository;
import com.surveyflow.repository.UserRepository;
import com.surveyflow.security.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

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
    @DisplayName("註冊測試: 正常輸入參數時應成功建立使用者並簽發雙 Token")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("Password123!")
                .fullName("新註冊用戶")
                .phone("0912345678")
                .build();

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(tokenProvider.generateAccessToken(anyString(), anyString())).thenReturn("mock_access_token");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("mock_refresh_token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("註冊測試: 帳號已存在時應拋出 IllegalArgumentException")
    void register_DuplicateUsername_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .username("existinguser")
                .email("existing@example.com")
                .password("Password123!")
                .fullName("重複用戶")
                .build();

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("登入測試: 帳號密碼正確時應成功登入並回傳會員資料")
    void login_Success() {
        LoginRequest request = new LoginRequest("testuser", "CorrectPassword123!");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("CorrectPassword123!", "hashed_argon2_password")).thenReturn(true);
        when(tokenProvider.generateAccessToken(anyString(), anyString())).thenReturn("access_token_ok");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refresh_token_ok");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("ROLE_USER", response.getRole());
    }

    @Test
    @DisplayName("登入測試: 密碼錯誤時應拋出 BadCredentialsException")
    void login_WrongPassword_ThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest("testuser", "WrongPassword!");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword!", "hashed_argon2_password")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("登出測試: 應調用 RefreshTokenRepository 撤銷名下所有 Token")
    void logout_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));

        authService.logout("testuser");

        verify(refreshTokenRepository, times(1)).revokeAllByUserId(sampleUser.getId());
    }
}
