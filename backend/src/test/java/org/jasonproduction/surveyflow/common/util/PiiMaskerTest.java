/**
 * ============================================================================
 * 檔案名稱: PiiMaskerTest.java
 * 測試對象: PiiMasker (手機、身分證遮罩規則與邊界值)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PiiMaskerTest {

    @Test
    @DisplayName("手機：保留前 4 碼與後 3 碼")
    void maskPhone() {
        assertEquals("0912***678", PiiMasker.maskPhone("0912345678"));
    }

    @Test
    @DisplayName("身分證：保留第 1 碼與後 3 碼")
    void maskIdentityCard() {
        assertEquals("A******789", PiiMasker.maskIdentityCard("A123456789"));
    }

    @Test
    @DisplayName("null 與空白原樣回傳；長度不足時全部遮罩，不洩漏任何字元")
    void edgeCases() {
        assertNull(PiiMasker.maskPhone(null));
        assertEquals(" ", PiiMasker.maskPhone(" "));
        assertEquals("*******", PiiMasker.maskPhone("0912345"));
        assertEquals("****", PiiMasker.maskIdentityCard("A123"));
    }
}
