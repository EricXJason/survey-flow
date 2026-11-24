/**
 * ============================================================================
 * 檔案名稱: AuthControllerTest.java
 * 測試對象: AuthController (認證端點之路由、參數驗證、Refresh Token Cookie 屬性、錯誤狀態碼)
 * 測試方式: MockMvc standalone + AuthService Mock
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.dto.auth.AuthResponse;
import org.jasonproduction.surveyflow.dto.auth.ForgetPasswordRequest;
import org.jasonproduction.surveyflow.dto.auth.LoginRequest;
import org.jasonproduction.surveyflow.dto.auth.RegisterRequest;
import org.jasonproduction.surveyflow.dto.auth.ResetPasswordRequest;
import org.jasonproduction.surveyflow.dto.auth.VerifyEmailRequest;
import org.jasonproduction.surveyflow.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    /** 7 天 (毫秒)，Cookie Max-Age 應為 604800 秒 */
    private static final long SEVEN_DAYS_MS = 604_800_000L;

    @Mock private AuthService authService;

    private MockMvc mockMvc;

    /**
     * 以正式環境之 Cookie 設定 (Secure、SameSite=None) 建立受測 Controller
     */
    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AuthController(authService, SEVEN_DAYS_MS, true, "None"));
    }

    /**
     * 清除模擬登入狀態
     */
    @AfterEach
    void tearDown() {
        ControllerTestSupport.logout();
    }

    /**
     * 建立含雙 Token 之認證結果
     * @return AuthResponse
     */
    private AuthResponse tokens() {
        return AuthResponse.builder().accessToken("access-jwt").token("access-jwt").refreshToken("refresh-jwt")
                .tokenType("Bearer").userId(1L).username("testuser").role("ROLE_USER").build();
    }

    @Test
    @DisplayName("POST /login 成功：Refresh Token 只放在 HttpOnly; Secure; SameSite=None Cookie，回應 Body 不含 Refresh Token")
    void login_SetsSecureCookieAndHidesRefreshToken() throws Exception {
        // Given
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens());

        // When / Then
        MvcResult result = mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(new LoginRequest("testuser", "User1234!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.startsWith("refreshToken=refresh-jwt"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=None"));
        assertTrue(cookie.contains("Max-Age=604800"));
        assertTrue(cookie.contains("Path=/"));
    }

    @Test
    @DisplayName("POST /login 密碼為空：400，且不呼叫 Service")
    void login_BlankPassword_Returns400() throws Exception {
        mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(new LoginRequest("testuser", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("POST /login 帳密錯誤：401；帳號鎖定中：429")
    void login_ErrorStatuses() throws Exception {
        // Given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("帳號或密碼錯誤"))
                .thenThrow(new LockedException("登入失敗次數過多，請於 15 分鐘後再試"));
        String body = ControllerTestSupport.json(new LoginRequest("testuser", "wrong"));

        // Then
        mockMvc.perform(post("/api/public/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("帳號或密碼錯誤"));
        mockMvc.perform(post("/api/public/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", containsString("15 分鐘")));
    }

    @Test
    @DisplayName("POST /register 成功：200 且不下發 Cookie (尚未驗證信箱)")
    void register_NoCookie() throws Exception {
        // Given
        when(authService.register(any(RegisterRequest.class))).thenReturn(AuthResponse.builder().username("newuser").build());
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser").email("new@example.com").password("Password1!").fullName("新會員").build();

        // When
        MvcResult result = mockMvc.perform(post("/api/public/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(ControllerTestSupport.json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andReturn();

        // Then
        assertNull(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("POST /register 信箱格式錯誤：400 VALIDATION_FAILED，errors 標示 email 欄位")
    void register_InvalidEmail_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser").email("not-an-email").password("Password1!").fullName("新會員").build();

        mockMvc.perform(post("/api/public/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(ControllerTestSupport.json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /register 驗證信寄送失敗：503")
    void register_MailFailure_Returns503() throws Exception {
        // Given
        when(authService.register(any(RegisterRequest.class))).thenThrow(new MailSendException("驗證信寄送失敗，請稍後再試"));
        RegisterRequest request = RegisterRequest.builder()
                .username("newuser").email("new@example.com").password("Password1!").fullName("新會員").build();

        // Then
        mockMvc.perform(post("/api/public/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(ControllerTestSupport.json(request)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /verify-email 成功：下發 Refresh Cookie")
    void verifyEmail_SetsCookie() throws Exception {
        // Given
        when(authService.verifyRegistrationEmail(any(VerifyEmailRequest.class))).thenReturn(tokens());

        // Then
        mockMvc.perform(post("/api/public/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(new VerifyEmailRequest("t@example.com", "123456"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-jwt")));
    }

    @Test
    @DisplayName("POST /refresh 不帶 Body：以 Cookie 中之 Refresh Token 換發，並下發新 Cookie")
    void refresh_UsesCookie() throws Exception {
        // Given
        when(authService.refreshToken(isNull(), any())).thenReturn(tokens());

        // When / Then
        mockMvc.perform(post("/api/public/auth/refresh").cookie(new Cookie("refreshToken", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-jwt")));
        verify(authService).refreshToken(null, "old-refresh");
    }

    @Test
    @DisplayName("POST /user/auth/logout：吊銷 Access 與 Refresh Token，並以 Max-Age=0 清除 Cookie")
    void logout_RevokesAndClearsCookie() throws Exception {
        // Given
        ControllerTestSupport.loginAs("testuser", "ROLE_USER");

        // When / Then
        mockMvc.perform(post("/api/user/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-jwt")
                        .cookie(new Cookie("refreshToken", "refresh-jwt")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(authService).logout("testuser", "Bearer access-jwt", "refresh-jwt");
    }

    @Test
    @DisplayName("POST /resend-verification：缺少 email 時 400；有 email 時委派 Service")
    void resendVerification() throws Exception {
        mockMvc.perform(post("/api/public/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON).content(ControllerTestSupport.json(Map.of())))
                .andExpect(status().isBadRequest());
        verify(authService, never()).resendRegistrationCode(anyString());

        mockMvc.perform(post("/api/public/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON).content(ControllerTestSupport.json(Map.of("email", "t@example.com"))))
                .andExpect(status().isOk());
        verify(authService).resendRegistrationCode("t@example.com");
    }

    @Test
    @DisplayName("POST /forget-password 與 /reset-password：驗證通過後委派 Service")
    void forgetAndResetPassword() throws Exception {
        mockMvc.perform(post("/api/public/auth/forget-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(ForgetPasswordRequest.builder().email("t@example.com").build())))
                .andExpect(status().isOk());
        verify(authService).processForgotPassword(any(ForgetPasswordRequest.class));

        mockMvc.perform(post("/api/public/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(ResetPasswordRequest.builder()
                                .email("t@example.com").code("123456").newPassword("NewPass1!").build())))
                .andExpect(status().isOk());
        verify(authService).processResetPassword(any(ResetPasswordRequest.class));
    }
}
