/**
 * ============================================================================
 * 檔案名稱: HttpClientConfigTest.java
 * 測試對象: HttpClientConfig (共用 HttpClient Bean)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpClientConfigTest {

    @Test
    @DisplayName("連線逾時秒數依設定值建立")
    void httpClient_UsesConfiguredTimeout() {
        HttpClient client = new HttpClientConfig().httpClient(7);

        assertEquals(Duration.ofSeconds(7), client.connectTimeout().orElseThrow());
    }
}
