/**
 * ============================================================================
 * 檔案名稱: TokenRevocationServiceTest.java
 * 測試對象: TokenRevocationService (單一 Token 黑名單、使用者整批作廢、Refresh Token 輪替與重放偵測)
 * 測試方式: RevokedTokenRepository、UserRepository 以 Mock 取代，只驗證流程判斷與傳給資料庫的參數；
 *           SQL 本身與「重啟後仍有效」由 RevokedTokenRepositoryIT 以真實 MySQL 驗證
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.repository.RevokedTokenRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock private RevokedTokenRepository revokedTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TokenRevocationService service;

    /** 一小時前簽發之時間點 (代表作廢前就存在的舊 Token) */
    private final Instant anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

    /**
     * 每個測試建立新的受測物件
     */
    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(revokedTokenRepository, userRepository, eventPublisher);
    }

    @Test
    @DisplayName("吊銷：以「現在 + 剩餘效期」為到期時間寫入黑名單；剩餘效期為負時以現在為到期時間")
    void revokeToken_WritesExpiryFromRemainingTtl() {
        // When
        service.revokeToken("jti-1", 60_000L);
        service.revokeToken("jti-2", -5L);

        // Then
        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> revokedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(revokedTokenRepository).insertIfAbsent(eq("jti-1"), expiresAt.capture(), revokedAt.capture());
        assertEquals(60_000L, ChronoUnit.MILLIS.between(revokedAt.getValue(), expiresAt.getValue()));
        verify(revokedTokenRepository).insertIfAbsent(eq("jti-2"), expiresAt.capture(), revokedAt.capture());
        assertEquals(revokedAt.getValue(), expiresAt.getValue());
    }

    @Test
    @DisplayName("jti 為空白時不寫入黑名單")
    void revokeToken_BlankJti_IsIgnored() {
        service.revokeToken(" ", 60_000L);

        verify(revokedTokenRepository, never()).insertIfAbsent(anyString(), any(), any());
    }

    @Test
    @DisplayName("整批作廢：寫入截斷至整秒之作廢時間點；帳號空白時略過")
    void revokeAllTokensOf_WritesSecondPrecisionCutoff() {
        // When
        service.revokeAllTokensOf("testuser");
        service.revokeAllTokensOf(" ");

        // Then
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).updateTokensRevokedBeforeNative(eq("testuser"), cutoff.capture());
        assertEquals(0, cutoff.getValue().getNano());
        verify(userRepository, never()).updateTokensRevokedBeforeNative(eq(" "), any());
    }

    @Test
    @DisplayName("判定：資料庫有任一條件命中即失效；簽發時間轉為 JVM 時區、空白 jti 以 null 查詢")
    void isRevoked_DelegatesToSingleQuery() {
        // Given
        LocalDateTime issuedAt = LocalDateTime.ofInstant(anHourAgo, ZoneId.systemDefault());
        when(revokedTokenRepository.countRevocationMatches(eq("jti-1"), eq("testuser"), eq(issuedAt), any())).thenReturn(1);
        when(revokedTokenRepository.countRevocationMatches(isNull(), eq("testuser"), isNull(), any())).thenReturn(0);

        // When / Then
        assertTrue(service.isRevoked("jti-1", "testuser", anHourAgo));
        assertFalse(service.isRevoked(" ", "testuser", null));
    }

    @Test
    @DisplayName("輪替成功：舊 jti 首次寫入黑名單 (1 筆) → 回傳 true，不整批作廢")
    void rotate_FirstUse_Succeeds() {
        // Given
        when(revokedTokenRepository.countRevocationMatches(isNull(), eq("testuser"), any(), any())).thenReturn(0);
        when(revokedTokenRepository.insertIfAbsent(eq("old-jti"), any(), any())).thenReturn(1);

        // When / Then
        assertTrue(service.rotateRefreshToken("old-jti", "testuser", anHourAgo, 60_000L));
        verify(userRepository, never()).updateTokensRevokedBeforeNative(anyString(), any());
    }

    @Test
    @DisplayName("重放：舊 jti 已在黑名單 (寫入 0 筆) → 回傳 false，並作廢該使用者全部 Token")
    void rotate_ReplayedToken_RevokesAllTokensOfUser() {
        // Given
        when(revokedTokenRepository.countRevocationMatches(isNull(), eq("testuser"), any(), any())).thenReturn(0);
        when(revokedTokenRepository.insertIfAbsent(eq("old-jti"), any(), any())).thenReturn(0);

        // When / Then
        assertFalse(service.rotateRefreshToken("old-jti", "testuser", anHourAgo, 60_000L));
        verify(userRepository).updateTokensRevokedBeforeNative(eq("testuser"), any());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(AuditAction.TOKEN_REPLAY_DETECTED, event.getValue().action());
        assertTrue(event.getValue().isFailureEvent());
    }

    @Test
    @DisplayName("已被整批作廢之使用者：舊 Refresh Token 無法輪替，也不寫入黑名單")
    void rotate_TokenIssuedBeforeUserRevocation_Fails() {
        // Given
        when(revokedTokenRepository.countRevocationMatches(isNull(), eq("testuser"), any(), any())).thenReturn(1);

        // When / Then
        assertFalse(service.rotateRefreshToken("old-jti", "testuser", anHourAgo, 60_000L));
        verify(revokedTokenRepository, never()).insertIfAbsent(anyString(), any(), any());
    }

    @Test
    @DisplayName("jti 為空：無法輪替")
    void rotate_BlankJti_Fails() {
        assertFalse(service.rotateRefreshToken(null, "testuser", anHourAgo, 60_000L));
    }

    @Test
    @DisplayName("定期清理：以現在時間刪除已過期之黑名單列")
    void cleanup_DeletesExpiredRows() {
        when(revokedTokenRepository.deleteExpired(any())).thenReturn(3, 0);

        service.cleanupExpiredTokens();
        service.cleanupExpiredTokens();

        verify(revokedTokenRepository, times(2)).deleteExpired(any());
    }
}
