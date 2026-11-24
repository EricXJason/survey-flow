/**
 * ============================================================================
 * 檔案名稱: ApiResponseTest.java
 * 測試對象: ApiResponse (成功回應封裝之靜態工廠方法)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    @DisplayName("ok(data, message)：status 200、success true、帶資料與時間戳記")
    void ok_WithData() {
        ApiResponse<String> response = ApiResponse.ok("payload", "成功");

        assertEquals(200, response.getStatus());
        assertTrue(response.isSuccess());
        assertEquals("payload", response.getData());
        assertEquals("成功", response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("ok(message)：data 為 null")
    void ok_MessageOnly() {
        assertNull(ApiResponse.ok("成功").getData());
    }
}
