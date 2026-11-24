/**
 * ============================================================================
 * 檔案名稱: HttpClientConfig.java
 * 所屬模組: 系統設定模組 (Configuration)
 * 責任描述: 提供全域共用之 JDK HttpClient Bean，供呼叫外部 HTTP API (Brevo / Resend 郵件服務) 使用
 * 架構分層: Configuration Layer
 * 聯動關係:
 *   - 上游依賴: application.properties (app.http.connect-timeout-seconds)
 *   - 下游消費: MailService
 * 邊界處理: [Why]: HttpClient 以 Bean 注入而非在 MailService 內自行建立，單元測試才能以 Mockito 替換，
 *           不必真的連線到外部郵件服務 (依賴反轉原則 DIP)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * 建立共用 HttpClient (執行緒安全，可全域重複使用)
     * @param connectTimeoutSeconds 建立連線之逾時秒數
     * @return HttpClient 實例
     */
    @Bean
    public HttpClient httpClient(@Value("${app.http.connect-timeout-seconds}") long connectTimeoutSeconds) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }
}
