/**
 * ============================================================================
 * 檔案名稱: RequestTraceFilterTest.java
 * 測試對象: RequestTraceFilter (traceId 產生 / 沿用、MDC 寫入與清除、X-Trace-Id 回應標頭)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter(new ClientIpResolver(0));

    @Test
    @DisplayName("請求期間 MDC 有 traceId 與 clientIp，回應帶 X-Trace-Id；請求結束後 MDC 被清除")
    void putsTraceIdAndClientIpDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInChain = new AtomicReference<>();
        AtomicReference<String> ipInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            traceInChain.set(MDC.get(RequestTraceFilter.MDC_TRACE_ID));
            ipInChain.set(MDC.get(RequestTraceFilter.MDC_CLIENT_IP));
        });

        assertTrue(traceInChain.get().matches("[0-9a-f]{32}"));
        assertEquals(traceInChain.get(), response.getHeader(RequestTraceFilter.TRACE_ID_HEADER));
        assertEquals("198.51.100.7", ipInChain.get());
        assertNull(MDC.get(RequestTraceFilter.MDC_TRACE_ID));
        assertNull(MDC.get(RequestTraceFilter.MDC_CLIENT_IP));
    }

    @Test
    @DisplayName("合法之 X-Request-Id 沿用；含換行、過短或過長者改為新產生")
    void resolveTraceId_AcceptsOnlySafeIds() {
        assertEquals("gateway-req-0001", RequestTraceFilter.resolveTraceId("gateway-req-0001"));
        assertNotEquals("bad\nid-123456", RequestTraceFilter.resolveTraceId("bad\nid-123456"));
        assertTrue(RequestTraceFilter.resolveTraceId("short").matches("[0-9a-f]{32}"));
        assertTrue(RequestTraceFilter.resolveTraceId("a".repeat(65)).matches("[0-9a-f]{32}"));
        assertTrue(RequestTraceFilter.resolveTraceId(null).matches("[0-9a-f]{32}"));
    }
}
