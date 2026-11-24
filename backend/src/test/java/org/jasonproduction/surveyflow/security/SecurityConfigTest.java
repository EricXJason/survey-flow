/**
 * ============================================================================
 * 檔案名稱: SecurityConfigTest.java
 * 測試對象: SecurityConfig 中可獨立驗證之 Bean (Argon2id 密碼雜湊器、CORS 白名單)
 * 測試方式: 直接呼叫 @Bean 方法；過濾鏈 (SecurityFilterChain) 需 Spring 容器，屬整合測試範圍，不在此驗證
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final SecurityConfig securityConfig =
            new SecurityConfig(mock(JwtAuthenticationFilter.class), mock(RateLimitFilter.class));

    /** 與 application.properties 相同之 Argon2id 參數建立之雜湊器 */
    private final PasswordEncoder passwordEncoder = securityConfig.passwordEncoder(16, 32, 1, 4096, 3);

    @Test
    @DisplayName("Argon2id：同一密碼每次雜湊結果不同 (隨機鹽)，但都能比對成功")
    void argon2_HashIsSaltedAndVerifiable() {
        // When
        String first = passwordEncoder.encode("User1234!");
        String second = passwordEncoder.encode("User1234!");

        // Then
        assertTrue(first.startsWith("$argon2id$"));
        assertNotEquals(first, second);
        assertTrue(passwordEncoder.matches("User1234!", first));
        assertFalse(passwordEncoder.matches("wrong", first));
    }

    @Test
    @DisplayName("seed/01 種子帳號之密碼雜湊可被目前參數驗證 (admin / Admin123!、testuser / User1234!)")
    void seedDataHashes_MatchDocumentedPasswords() {
        String adminHash = "$argon2id$v=19$m=4096,t=3,p=1$dxlqSLPglwBvQ/uW8I4u4Q$XFRrN5aM8WzQSfMGTQCyLYi880buSWKmRgRpc0FwPgI";
        String userHash = "$argon2id$v=19$m=4096,t=3,p=1$DM3gQ1RL2fM7v/ZRQvfQkA$XSnvuHaBR+qeZj1DECEcP74/lCIrrGngc6sba6EDTAM";

        assertTrue(passwordEncoder.matches("Admin123!", adminHash));
        assertTrue(passwordEncoder.matches("User1234!", userHash));
    }

    /**
     * 取得指定來源之 CORS 設定
     * @param source CorsConfigurationSource
     * @return /api 路徑之 CORS 設定
     */
    private CorsConfiguration corsFor(CorsConfigurationSource source) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/surveys/lobby");
        return source.getCorsConfiguration(request);
    }

    @Test
    @DisplayName("CORS：只允許 FRONTEND_BASE_URL 與 CORS_ALLOWED_ORIGINS，不內建 localhost")
    void cors_AllowsOnlyConfiguredOrigins() {
        // When
        CorsConfiguration cors = corsFor(securityConfig.corsConfigurationSource(
                " https://extra.example.com , ", "https://survey-flow.pages.dev",
                "GET,POST", "Authorization,Content-Type", 3600L));

        // Then
        assertNotNull(cors);
        assertEquals(List.of("https://survey-flow.pages.dev", "https://extra.example.com"), cors.getAllowedOriginPatterns());
        assertNull(cors.checkOrigin("http://localhost:4200"), "未設定時 localhost 不得被允許");
        assertEquals("https://survey-flow.pages.dev", cors.checkOrigin("https://survey-flow.pages.dev"));
        assertEquals(List.of("GET", "POST"), cors.getAllowedMethods());
        assertTrue(Boolean.TRUE.equals(cors.getAllowCredentials()));
        assertEquals(3600L, cors.getMaxAge());
    }

    @Test
    @DisplayName("CORS：額外來源為空字串時，只允許前端網址")
    void cors_EmptyExtraOrigins() {
        CorsConfiguration cors = corsFor(securityConfig.corsConfigurationSource(
                "", "http://localhost:4200", "GET", "Authorization", 60L));

        assertEquals(List.of("http://localhost:4200"), cors.getAllowedOriginPatterns());
    }
}
