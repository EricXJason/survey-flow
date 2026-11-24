/**
 * ============================================================================
 * 檔案名稱: RateLimitIT.java
 * 測試對象: RateLimitFilter 掛在完整過濾鏈中之行為：超量回 429 Problem Details、Retry-After 與 CORS 標頭皆存在
 * 測試方式: 以 @TestPropertySource 將 AUTH 上限調為 2 (獨立 Spring 內容)；spring.sql.init.mode=never 避免重建共用之測試資料庫
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"app.rate-limit.auth-max-requests=2", "spring.sql.init.mode=never"})
class RateLimitIT extends IntegrationTestSupport {

    private static final String ORIGIN = "https://survey-flow.pages.dev";

    private final MockMvc mockMvc;

    /**
     * 建構子注入
     * @param mockMvc 經過完整過濾鏈之 MockMvc
     */
    RateLimitIT(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("同一 IP 第 3 次呼叫登入：429、code=RATE_LIMITED、Retry-After，且帶 CORS 標頭讓前端讀得到原因")
    void thirdAuthRequest_Gets429WithCors() throws Exception {
        String body = "{\"username\":\"nobody\",\"password\":\"x\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/public/auth/login").header(HttpHeaders.ORIGIN, ORIGIN)
                    .with(request -> { request.setRemoteAddr("198.51.100.200"); return request; })
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/public/auth/login").header(HttpHeaders.ORIGIN, ORIGIN)
                        .with(request -> { request.setRemoteAddr("198.51.100.200"); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, not(emptyOrNullString())))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
    }
}
