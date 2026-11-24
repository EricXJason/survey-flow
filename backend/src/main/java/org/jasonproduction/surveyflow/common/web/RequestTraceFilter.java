/**
 * ============================================================================
 * 檔案名稱: RequestTraceFilter.java
 * 所屬模組: Common Web 請求工具 (可觀測性)
 * 責任描述: 每個請求最先經過的過濾器：
 *   1. 產生 traceId (沿用合法之 X-Request-Id，否則隨機 32 字元)，寫入 MDC 並以 X-Trace-Id 回應標頭回傳
 *   2. 解析用戶端 IP 寫入 MDC clientIp
 *   結構化 JSON 日誌會自動帶出 MDC 欄位，同一請求的所有日誌都能以 traceId 串起來；錯誤回應與稽核日誌也記錄同一個 traceId。
 * 架構分層: Common / Web Layer (Servlet Filter，順序最高，排在 Spring Security 之前)
 * 聯動關係:
 *   - 上游依賴: ClientIpResolver
 *   - 下游消費: Logback 結構化日誌、GlobalExceptionHandler (Problem Details traceId)、AuditLogService (trace_id、client_ip)
 * 邊界處理: [Why]: 只接受 8~64 字元之英數與連字號，避免用戶端以 X-Request-Id 注入換行或超長字串污染日誌；
 *           finally 一律清除 MDC，Servlet 執行緒重複使用時不會帶到下一個請求
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestTraceFilter extends OncePerRequestFilter {

    /** MDC 鍵：請求追蹤碼 */
    public static final String MDC_TRACE_ID = "traceId";

    /** MDC 鍵：用戶端 IP */
    public static final String MDC_CLIENT_IP = "clientIp";

    /** 回應標頭：請求追蹤碼 (前端錯誤訊息附上此值，方便回報問題時查日誌) */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 請求標頭：上游 (如閘道) 已產生之請求編號 */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 允許沿用之請求編號格式 */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final ClientIpResolver clientIpResolver;

    /**
     * 設定 traceId 與 clientIp 後放行，請求結束時清除 MDC
     * @param request HTTP 請求
     * @param response HTTP 回應
     * @param filterChain 後續過濾鏈
     * @throws ServletException 後續過濾器錯誤
     * @throws IOException 輸出入錯誤
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_CLIENT_IP, clientIpResolver.resolve(request));
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_CLIENT_IP);
        }
    }

    /**
     * 決定本次請求之 traceId
     * @param incomingRequestId 請求標頭 X-Request-Id，可為 null
     * @return 合法之上游編號；否則為新產生之 32 字元十六進位字串
     */
    static String resolveTraceId(String incomingRequestId) {
        if (incomingRequestId != null && SAFE_REQUEST_ID.matcher(incomingRequestId).matches()) {
            return incomingRequestId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
