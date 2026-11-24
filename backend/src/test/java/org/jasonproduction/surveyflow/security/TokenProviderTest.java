/**
 * ============================================================================
 * 檔案名稱: TokenProviderTest.java
 * 測試對象: TokenProvider (JWT 簽發、解析、驗簽、類型判定、剩餘效期)
 * 測試方式: 以真實 JJWT 實作搭配測試專用金鑰執行，不使用 Mock，確保簽章與解析行為正確
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenProviderTest {

    /** 測試專用 HMAC 金鑰 (長度需 ≥ 32 bytes) */
    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";
    private static final long FIFTEEN_MINUTES_MS = 15 * 60 * 1000L;
    private static final long SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L;

    private final TokenProvider tokenProvider = new TokenProvider(SECRET, FIFTEEN_MINUTES_MS, SEVEN_DAYS_MS);

    @Test
    @DisplayName("Access Token：可解析出帳號與 jti，驗簽通過，且不是 Refresh Token")
    void accessToken_ContainsSubjectAndJti() {
        // When
        String token = tokenProvider.generateAccessToken("testuser", "ROLE_USER");

        // Then
        assertTrue(tokenProvider.validateToken(token));
        assertEquals("testuser", tokenProvider.getUsernameFromToken(token));
        assertNotNull(tokenProvider.getJtiFromToken(token));
        assertFalse(tokenProvider.isRefreshToken(token));
    }

    @Test
    @DisplayName("Refresh Token：isRefreshToken 為 true")
    void refreshToken_IsRecognized() {
        // When
        String token = tokenProvider.generateRefreshToken("testuser");

        // Then
        assertTrue(tokenProvider.validateToken(token));
        assertTrue(tokenProvider.isRefreshToken(token));
    }

    @Test
    @DisplayName("每次簽發之 jti 皆不同 (黑名單才能精準作廢單一 Token)")
    void jti_IsUniquePerToken() {
        String first = tokenProvider.generateAccessToken("testuser", "ROLE_USER");
        String second = tokenProvider.generateAccessToken("testuser", "ROLE_USER");

        assertNotEquals(tokenProvider.getJtiFromToken(first), tokenProvider.getJtiFromToken(second));
    }

    @Test
    @DisplayName("簽發時間 (iat) 接近現在，且精確到秒")
    void issuedAt_IsNowTruncatedToSeconds() {
        // Given
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // When
        Instant issuedAt = tokenProvider.getIssuedAt(tokenProvider.generateAccessToken("testuser", "ROLE_USER"));

        // Then
        assertNotNull(issuedAt);
        assertFalse(issuedAt.isBefore(before));
        assertEquals(0, issuedAt.getNano());
    }

    @Test
    @DisplayName("剩餘效期：新簽發之 Access Token 介於 0 與 15 分鐘之間")
    void remainingTtl_IsWithinLifetime() {
        long ttl = tokenProvider.getRemainingTtlMs(tokenProvider.generateAccessToken("testuser", "ROLE_USER"));

        assertTrue(ttl > 0 && ttl <= FIFTEEN_MINUTES_MS);
    }

    @Test
    @DisplayName("已過期之 Token：驗簽失敗，解析類方法回傳安全預設值 (null / 0 / false)")
    void expiredToken_IsRejected() {
        // Given：效期為負數，簽發當下即已過期
        TokenProvider expiredProvider = new TokenProvider(SECRET, -1000L, -1000L);
        String expired = expiredProvider.generateRefreshToken("testuser");

        // Then
        assertFalse(expiredProvider.validateToken(expired));
        assertNull(expiredProvider.getJtiFromToken(expired));
        assertNull(expiredProvider.getIssuedAt(expired));
        assertEquals(0L, expiredProvider.getRemainingTtlMs(expired));
        assertFalse(expiredProvider.isRefreshToken(expired));
    }

    @Test
    @DisplayName("以其他金鑰簽發之 Token (偽造)：驗簽失敗")
    void tokenSignedByOtherKey_IsRejected() {
        // Given
        TokenProvider attacker = new TokenProvider("another-secret-key-that-is-also-32-bytes-long!!", FIFTEEN_MINUTES_MS, SEVEN_DAYS_MS);
        String forged = attacker.generateAccessToken("admin", "ROLE_ADMIN");

        // Then
        assertFalse(tokenProvider.validateToken(forged));
    }

    @Test
    @DisplayName("格式錯誤之字串：驗簽失敗且不拋出例外")
    void malformedToken_IsRejected() {
        assertFalse(tokenProvider.validateToken("not-a-jwt"));
        assertFalse(tokenProvider.validateToken(""));
    }
}
