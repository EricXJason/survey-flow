/**
 * ============================================================================
 * 檔案名稱: RateLimitFilterTest.java
 * 測試對象: RateLimitFilter (端點分級、固定時間窗計數、超量交給例外解析器、時間窗重置與清理)
 * 測試方式: 以子類別覆寫 currentEpochSecond() 固定時間；HandlerExceptionResolver 以 Mock 取代並驗證收到之例外
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import jakarta.servlet.FilterChain;
import org.jasonproduction.surveyflow.common.exception.RateLimitExceededException;
import org.jasonproduction.surveyflow.common.web.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    /** 時間窗 60 秒；AUTH 上限 2、SUBMISSION 上限 3、GENERAL 上限 4 */
    private static final long WINDOW = 60;

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private long now;
    private RateLimitFilter filter;

    /**
     * 建立時間可控之受測過濾器 (起始時間落在時間窗第 10 秒)
     */
    @BeforeEach
    void setUp() {
        now = 6_000_010L;
        filter = new RateLimitFilter(new ClientIpResolver(0), resolver, WINDOW, 2, 3, 4) {
            @Override
            long currentEpochSecond() {
                return now;
            }
        };
    }

    /**
     * 以指定 IP 發出一次請求
     * @param method HTTP 方法
     * @param path 請求路徑
     * @param ip 連線來源 IP
     * @param chain 後續過濾鏈 (Mock，用來確認是否放行)
     * @throws Exception 過濾器錯誤
     */
    private void send(String method, String path, String ip, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    @DisplayName("AUTH 等級：同一 IP 第 3 次被擋，429 交給例外解析器且 Retry-After 為時間窗剩餘秒數；不往下執行")
    void authTier_BlocksAfterLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            send("POST", "/api/public/auth/login", "203.0.113.5", chain);
        }

        verify(chain, times(2)).doFilter(any(), any());
        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(any(), any(), isNull(), ex.capture());
        assertEquals(50, ((RateLimitExceededException) ex.getValue()).getRetryAfterSeconds());
    }

    @Test
    @DisplayName("不同 IP、不同等級各自計數：AUTH 用完不影響一般 API 與其他 IP")
    void countersAreIndependent() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);

        send("GET", "/api/public/surveys/lobby", "203.0.113.5", chain);
        send("POST", "/api/public/auth/login", "198.51.100.1", chain);

        verify(chain, times(4)).doFilter(any(), any());
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SUBMISSION 只限 POST /api/public/submissions；同路徑其他方法算 GENERAL")
    void submissionTier_OnlyPost() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 4; i++) {
            send("POST", "/api/public/submissions", "203.0.113.5", chain);
        }
        verify(resolver, times(1)).resolveException(any(), any(), isNull(), any(RateLimitExceededException.class));

        send("GET", "/api/public/submissions", "203.0.113.5", chain);
        verify(chain, times(4)).doFilter(any(), any());
    }

    @Test
    @DisplayName("進入下一個時間窗後重新計數")
    void nextWindow_ResetsCount() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);

        now += WINDOW;
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);

        verify(chain, times(3)).doFilter(any(), any());
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("actuator 與 CORS 預檢不限流：大量請求皆放行")
    void actuatorAndPreflight_AreNotLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 10; i++) {
            send("GET", "/actuator/health", "203.0.113.5", chain);
            send("OPTIONS", "/api/public/auth/login", "203.0.113.5", chain);
        }
        verify(chain, times(20)).doFilter(any(), any());
    }

    @Test
    @DisplayName("清理排程：移除舊時間窗後，新時間窗從 1 開始計數")
    void evictExpiredWindows_RemovesOldWindows() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);

        now += WINDOW;
        filter.evictExpiredWindows();
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);
        send("POST", "/api/public/auth/login", "203.0.113.5", chain);

        verify(chain, times(4)).doFilter(any(), any());
        verify(resolver, never()).resolveException(any(), any(), eq(null), any());
    }
}
