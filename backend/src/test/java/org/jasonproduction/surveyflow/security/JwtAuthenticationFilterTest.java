/**
 * ============================================================================
 * 檔案名稱: JwtAuthenticationFilterTest.java
 * 測試對象: JwtAuthenticationFilter (每個請求解析 Bearer Token 並決定是否寫入 SecurityContext)
 * 測試方式: TokenProvider、TokenRevocationService、CustomUserDetailsService 以 Mock 取代；
 *           請求與回應使用 Spring 提供之 MockHttpServletRequest / MockHttpServletResponse
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private TokenProvider tokenProvider;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private CustomUserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockFilterChain filterChain;
    private final Instant issuedAt = Instant.parse("2026-11-01T00:00:00Z");

    /**
     * 建立受測 Filter 與空白請求
     */
    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenProvider, tokenRevocationService, userDetailsService);
        request = new MockHttpServletRequest();
        filterChain = new MockFilterChain();
    }

    /**
     * SecurityContext 為執行緒共用狀態，每個測試後清除避免互相影響
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 讓 TokenProvider 將 token 視為驗簽通過之 testuser Token
     * @param token Token 字串
     */
    private void stubValidToken(String token) {
        request.addHeader("Authorization", "Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getJtiFromToken(token)).thenReturn("jti-1");
        when(tokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenProvider.getIssuedAt(token)).thenReturn(issuedAt);
    }

    /**
     * 建立 Spring Security 使用者
     * @param enabled 是否啟用
     * @return UserDetails
     */
    private UserDetails userDetails(boolean enabled) {
        return User.withUsername("testuser").password("x")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .disabled(!enabled)
                .build();
    }

    /**
     * 執行 Filter 並回傳執行後之認證狀態
     * @return SecurityContext 中之 Authentication，未建立時為 null
     * @throws Exception Filter 執行例外
     */
    private Authentication runFilter() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        assertNotNull(filterChain.getRequest(), "不論認證成功與否，請求都必須繼續往下傳遞");
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("有效 Access Token 且帳號啟用：寫入認證，權限為 ROLE_USER")
    void validAccessToken_SetsAuthentication() throws Exception {
        // Given
        stubValidToken("access");
        when(tokenRevocationService.isRevoked("jti-1", "testuser", issuedAt)).thenReturn(false);
        when(tokenProvider.isRefreshToken("access")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails(true));

        // When
        Authentication authentication = runFilter();

        // Then
        assertNotNull(authentication);
        assertEquals("testuser", authentication.getName());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("沒有 Authorization 標頭：不建立認證，請求以匿名身分繼續")
    void noHeader_StaysAnonymous() throws Exception {
        assertNull(runFilter());
        verify(tokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("標頭不是 Bearer 格式：不建立認證")
    void nonBearerHeader_StaysAnonymous() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        assertNull(runFilter());
    }

    @Test
    @DisplayName("Token 驗簽失敗或過期：不建立認證")
    void invalidToken_StaysAnonymous() throws Exception {
        // Given
        request.addHeader("Authorization", "Bearer expired");
        when(tokenProvider.validateToken("expired")).thenReturn(false);

        // Then
        assertNull(runFilter());
    }

    @Test
    @DisplayName("Token 已被吊銷 (登出或整批作廢)：不建立認證，也不查詢帳號")
    void revokedToken_StaysAnonymous() throws Exception {
        // Given
        stubValidToken("revoked");
        when(tokenRevocationService.isRevoked("jti-1", "testuser", issuedAt)).thenReturn(true);

        // Then
        assertNull(runFilter());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("以 Refresh Token 呼叫業務 API：拒絕建立認證 (防止 Token 類型混用)")
    void refreshTokenUsedForApi_StaysAnonymous() throws Exception {
        // Given
        stubValidToken("refresh");
        when(tokenRevocationService.isRevoked("jti-1", "testuser", issuedAt)).thenReturn(false);
        when(tokenProvider.isRefreshToken("refresh")).thenReturn(true);

        // Then
        assertNull(runFilter());
    }

    @Test
    @DisplayName("帳號已停權 (disabled)：即使 Token 未過期也不建立認證")
    void disabledAccount_StaysAnonymous() throws Exception {
        // Given
        stubValidToken("access");
        when(tokenRevocationService.isRevoked("jti-1", "testuser", issuedAt)).thenReturn(false);
        when(tokenProvider.isRefreshToken("access")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails(false));

        // Then
        assertNull(runFilter());
    }

    @Test
    @DisplayName("載入帳號時發生例外 (例如帳號已刪除)：吞下例外並以匿名身分繼續，不中斷請求")
    void exceptionDuringLookup_StaysAnonymous() throws Exception {
        // Given
        stubValidToken("access");
        when(tokenRevocationService.isRevoked("jti-1", "testuser", issuedAt)).thenReturn(false);
        when(tokenProvider.isRefreshToken("access")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("testuser")).thenThrow(new RuntimeException("查無帳號"));

        // Then
        assertNull(runFilter());
    }
}
