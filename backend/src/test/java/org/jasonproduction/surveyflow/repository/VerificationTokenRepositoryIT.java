/**
 * ============================================================================
 * 檔案名稱: VerificationTokenRepositoryIT.java
 * 測試對象: VerificationTokenRepository.findValidTokenForUpdate (悲觀鎖查詢之條件正確性)
 * 測試方式: 整合測試 + @Transactional；鎖的併發行為另見 ConcurrencyIT
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.common.enums.VerificationTokenType;
import org.jasonproduction.surveyflow.entity.VerificationToken;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class VerificationTokenRepositoryIT extends IntegrationTestSupport {

    private final VerificationTokenRepository tokenRepository;

    /**
     * 建構子注入
     * @param tokenRepository 受測 Repository
     */
    VerificationTokenRepositoryIT(VerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 寫入一筆驗證碼
     * @param code 驗證碼
     * @param type 用途
     * @return 已寫入之驗證碼
     */
    private VerificationToken saveToken(String code, VerificationTokenType type) {
        return tokenRepository.saveAndFlush(VerificationToken.builder().email("token@it.test").code(code)
                .tokenType(type).expiresAt(LocalDateTime.now().plusMinutes(15)).isUsed(false).build());
    }

    @Test
    @DisplayName("同信箱、同驗證碼有多筆時，回傳最新一筆 (ORDER BY id DESC LIMIT 1)")
    void returnsLatestToken() {
        saveToken("111111", VerificationTokenType.REGISTRATION);
        VerificationToken latest = saveToken("111111", VerificationTokenType.REGISTRATION);

        assertEquals(latest.getId(), tokenRepository.findValidTokenForUpdate("token@it.test", "111111", "REGISTRATION").orElseThrow().getId());
    }

    @Test
    @DisplayName("已核銷或用途不同之驗證碼不會被取出")
    void excludesUsedOrOtherType() {
        // Given
        VerificationToken used = saveToken("222222", VerificationTokenType.REGISTRATION);
        used.markUsed();
        tokenRepository.saveAndFlush(used);
        saveToken("333333", VerificationTokenType.FORGOT_PASSWORD);

        // Then
        assertTrue(tokenRepository.findValidTokenForUpdate("token@it.test", "222222", "REGISTRATION").isEmpty());
        assertTrue(tokenRepository.findValidTokenForUpdate("token@it.test", "333333", "REGISTRATION").isEmpty());
    }
}
