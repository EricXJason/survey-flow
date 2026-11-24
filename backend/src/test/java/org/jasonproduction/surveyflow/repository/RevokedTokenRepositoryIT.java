/**
 * ============================================================================
 * 檔案名稱: RevokedTokenRepositoryIT.java
 * 測試對象: RevokedTokenRepository 與 UserRepository.updateTokensRevokedBeforeNative 之原生 SQL，
 *           以及 TokenRevocationService 存於資料庫後「後端重啟仍有效」的行為
 * 測試方式: 整合測試 + @Transactional (每個測試結束回滾)；以另建一個 TokenRevocationService 實例模擬重啟
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.jasonproduction.surveyflow.security.TokenRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class RevokedTokenRepositoryIT extends IntegrationTestSupport {

    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 建構子注入
     * @param revokedTokenRepository 受測黑名單 Repository
     * @param userRepository 會員 Repository (整批作廢時間點)
     * @param tokenRevocationService Spring 容器中之吊銷服務
     * @param eventPublisher Spring 事件發布器 (模擬重啟時建立新服務實例使用)
     */
    RevokedTokenRepositoryIT(RevokedTokenRepository revokedTokenRepository, UserRepository userRepository,
                             TokenRevocationService tokenRevocationService, ApplicationEventPublisher eventPublisher) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.userRepository = userRepository;
        this.tokenRevocationService = tokenRevocationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 產生一個不與其他測試重複之 jti
     * @return UUID 字串
     */
    private static String newJti() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("INSERT IGNORE：同一 jti 第一次寫入回 1、第二次回 0 (輪替重放偵測之原子依據)")
    void insertIfAbsent_SecondInsertReturnsZero() {
        String jti = newJti();
        LocalDateTime now = LocalDateTime.now();

        assertEquals(1, revokedTokenRepository.insertIfAbsent(jti, now.plusMinutes(10), now));
        assertEquals(0, revokedTokenRepository.insertIfAbsent(jti, now.plusMinutes(10), now));
    }

    @Test
    @DisplayName("判定：未過期黑名單命中；已過期之黑名單列與未知 jti 不命中")
    void countRevocationMatches_Blacklist() {
        String alive = newJti();
        String expired = newJti();
        LocalDateTime now = LocalDateTime.now();
        revokedTokenRepository.insertIfAbsent(alive, now.plusMinutes(10), now);
        revokedTokenRepository.insertIfAbsent(expired, now.minusSeconds(1), now.minusMinutes(5));

        assertEquals(1, revokedTokenRepository.countRevocationMatches(alive, "testuser", null, now));
        assertEquals(0, revokedTokenRepository.countRevocationMatches(expired, "testuser", null, now));
        assertEquals(0, revokedTokenRepository.countRevocationMatches(newJti(), "testuser", null, now));
    }

    @Test
    @DisplayName("判定：簽發時間早於 users.tokens_revoked_before 才命中；同一秒簽發者與其他使用者不受影響")
    void countRevocationMatches_UserCutoff() {
        LocalDateTime cutoff = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        assertEquals(1, userRepository.updateTokensRevokedBeforeNative("testuser", cutoff));

        assertEquals(1, revokedTokenRepository.countRevocationMatches(null, "testuser", cutoff.minusHours(1), LocalDateTime.now()));
        assertEquals(0, revokedTokenRepository.countRevocationMatches(null, "testuser", cutoff, LocalDateTime.now()));
        assertEquals(0, revokedTokenRepository.countRevocationMatches(null, "academic_chen", cutoff.minusHours(1), LocalDateTime.now()));
    }

    @Test
    @DisplayName("清理：只刪除已過期之黑名單列")
    void deleteExpired_RemovesOnlyExpiredRows() {
        String alive = newJti();
        String expired = newJti();
        LocalDateTime now = LocalDateTime.now();
        revokedTokenRepository.insertIfAbsent(alive, now.plusMinutes(10), now);
        revokedTokenRepository.insertIfAbsent(expired, now.minusSeconds(1), now.minusMinutes(5));

        revokedTokenRepository.deleteExpired(now);

        assertTrue(revokedTokenRepository.existsById(alive));
        assertFalse(revokedTokenRepository.existsById(expired));
    }

    @Test
    @DisplayName("模擬後端重啟：以新的服務實例讀同一個資料庫，已登出之 Token、已輪替之舊 Refresh Token 與整批作廢仍然有效")
    void revocationSurvivesRestart() {
        // Given：重啟前登出一顆 Token、輪替一顆 Refresh Token，並整批作廢 academic_chen
        Instant issuedEarlier = Instant.now().minus(1, ChronoUnit.HOURS);
        String loggedOut = newJti();
        String rotated = newJti();
        tokenRevocationService.revokeToken(loggedOut, 600_000L);
        assertTrue(tokenRevocationService.rotateRefreshToken(rotated, "testuser", issuedEarlier, 600_000L));
        tokenRevocationService.revokeAllTokensOf("academic_chen");

        // When：建立全新的服務實例 (記憶體狀態為空，等同重啟)
        TokenRevocationService afterRestart = new TokenRevocationService(revokedTokenRepository, userRepository, eventPublisher);

        // Then
        assertTrue(afterRestart.isRevoked(loggedOut, "testuser", issuedEarlier));
        assertFalse(afterRestart.rotateRefreshToken(rotated, "testuser", issuedEarlier, 600_000L), "舊 Refresh Token 重放仍被擋下");
        assertTrue(afterRestart.isRevoked(newJti(), "academic_chen", issuedEarlier));
    }
}
