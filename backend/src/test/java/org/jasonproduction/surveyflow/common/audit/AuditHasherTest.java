/**
 * ============================================================================
 * 檔案名稱: AuditHasherTest.java
 * 測試對象: AuditHasher (HMAC-SHA256 雜湊之確定性、金鑰與欄位敏感度、null 與空字串區分、金鑰長度檢查)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditHasherTest {

    private static final String SECRET = "unit-test-audit-hmac-secret-at-least-32-bytes";
    private static final String GENESIS = "0".repeat(64);

    private final AuditHasher hasher = new AuditHasher(SECRET);

    @Test
    @DisplayName("相同輸入得到相同 64 字元小寫十六進位雜湊")
    void sameInput_SameHash() {
        String first = hasher.hash(GENESIS, "LOGIN_SUCCEEDED", "testuser");
        assertEquals(first, hasher.hash(GENESIS, "LOGIN_SUCCEEDED", "testuser"));
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("任一欄位、前一筆雜湊或金鑰不同，雜湊都不同")
    void anyChange_ChangesHash() {
        String base = hasher.hash(GENESIS, "LOGIN_SUCCEEDED", "testuser");

        assertNotEquals(base, hasher.hash(GENESIS, "LOGIN_SUCCEEDED", "testuser2"));
        assertNotEquals(base, hasher.hash("1".repeat(64), "LOGIN_SUCCEEDED", "testuser"));
        assertNotEquals(base, new AuditHasher(SECRET + "x").hash(GENESIS, "LOGIN_SUCCEEDED", "testuser"));
    }

    @Test
    @DisplayName("null 與空字串可區分；移動欄位邊界 (ab|c 與 a|bc) 不會得到相同雜湊")
    void canonicalEncoding_IsUnambiguous() {
        assertNotEquals(hasher.hash(GENESIS, (String) null), hasher.hash(GENESIS, ""));
        assertNotEquals(hasher.hash(GENESIS, "ab", "c"), hasher.hash(GENESIS, "a", "bc"));
    }

    @Test
    @DisplayName("金鑰不足 32 bytes：建立時即失敗 (應用程式無法啟動)")
    void shortSecret_FailsFast() {
        assertThrows(IllegalStateException.class, () -> new AuditHasher("too-short"));
    }
}
