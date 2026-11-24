/**
 * ============================================================================
 * 檔案名稱: RateLimitFilter.java
 * 所屬模組: Security 安全認證模組 (資安 S 級：全端點速率限制)
 * 責任描述: 依用戶端 IP 與端點等級做固定時間窗限流，超過上限回 429 Problem Details 與 Retry-After：
 *   - AUTH        /api/public/auth/**          (登入、註冊、驗證碼、換發、忘記密碼；防暴力破解與灌信)
 *   - SUBMISSION  POST /api/public/submissions (送出問卷；防灌票)
 *   - GENERAL     其餘 /api/**                  (一般瀏覽與管理操作)
 *   /actuator/** 與 CORS 預檢 (OPTIONS) 不計數。
 * 請求順序: RequestTraceFilter → Spring Security CorsFilter → 本過濾器 → JwtAuthenticationFilter → Controller
 *   [Why]: 排在 CorsFilter 之後，429 回應才帶有 CORS 標頭，前端讀得到原因；排在 JWT 驗證之前，超量請求不必查資料庫。
 * 架構分層: Security / Filter Layer
 * 聯動關係:
 *   - 上游依賴: application.properties (app.rate-limit.*)、ClientIpResolver、HandlerExceptionResolver (轉交 GlobalExceptionHandler)
 *   - 下游消費: SecurityConfig (addFilterAfter CorsFilter)
 * 邊界處理: [Why]: 計數存於 JVM 記憶體 (單一 Render 實例)。重啟只會讓計數歸零、提早解除限制，不會讓任何憑證或資料失效，
 *           故不引入共享儲存；多實例部署時須改為 Redis 等共享計數。過期時間窗由排程每 5 分鐘清除。
 *           登入另有以帳號為鍵之 LoginAttemptLimiter (連續失敗鎖定)，兩者互補：本過濾器擋同一 IP 大量請求，鎖定擋同一帳號猜密碼。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jasonproduction.surveyflow.common.exception.RateLimitExceededException;
import org.jasonproduction.surveyflow.common.web.ClientIpResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** 端點等級 */
    enum Tier { AUTH, SUBMISSION, GENERAL }

    /**
     * 某 IP 在某等級、某時間窗內之請求數 (不可變；每次請求以 compute 原子替換)
     * @param windowStart 時間窗起點 (epoch 秒)
     * @param count 時間窗內已收到之請求數
     */
    record Window(long windowStart, int count) {
        /**
         * @return 計數加一後之新時間窗
         */
        Window increment() {
            return new Window(windowStart, count + 1);
        }
    }

    private static final String AUTH_PREFIX = "/api/public/auth/";
    private static final String SUBMISSION_PATH = "/api/public/submissions";
    private static final String ACTUATOR_PREFIX = "/actuator";

    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final long windowSeconds;
    private final Map<Tier, Integer> maxRequestsByTier;

    /** Key: 等級 + ":" + IP，Value: 目前時間窗 */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 建構子注入限流參數
     * @param clientIpResolver 用戶端 IP 解析器
     * @param handlerExceptionResolver Spring MVC 例外解析器 (把 429 交給 GlobalExceptionHandler 產生 Problem Details)
     * @param windowSeconds 時間窗長度 (秒)
     * @param authMaxRequests AUTH 等級每個時間窗上限
     * @param submissionMaxRequests SUBMISSION 等級每個時間窗上限
     * @param generalMaxRequests GENERAL 等級每個時間窗上限
     */
    public RateLimitFilter(ClientIpResolver clientIpResolver,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
                           @Value("${app.rate-limit.window-seconds}") long windowSeconds,
                           @Value("${app.rate-limit.auth-max-requests}") int authMaxRequests,
                           @Value("${app.rate-limit.submission-max-requests}") int submissionMaxRequests,
                           @Value("${app.rate-limit.general-max-requests}") int generalMaxRequests) {
        this.clientIpResolver = clientIpResolver;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.windowSeconds = Math.max(windowSeconds, 1);
        this.maxRequestsByTier = Map.of(
                Tier.AUTH, authMaxRequests,
                Tier.SUBMISSION, submissionMaxRequests,
                Tier.GENERAL, generalMaxRequests);
    }

    /**
     * actuator 與 CORS 預檢不限流
     * @param request HTTP 請求
     * @return true 表示略過本過濾器
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod()) || request.getRequestURI().startsWith(ACTUATOR_PREFIX);
    }

    /**
     * 第一步：判定端點等級並以「等級 + IP」取得目前時間窗，計數加一 (ConcurrentHashMap.compute 保證原子)。
     * 第二步：時間窗已過則重新起算。
     * 第三步：超過上限 → 交給 GlobalExceptionHandler 回 429 與 Retry-After，不再往下執行；否則放行。
     * @param request HTTP 請求
     * @param response HTTP 回應
     * @param filterChain 後續過濾鏈
     * @throws ServletException 後續過濾器錯誤
     * @throws IOException 輸出入錯誤
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Tier tier = resolveTier(request);
        String clientIp = clientIpResolver.resolve(request);
        long now = currentEpochSecond();
        long windowStart = now - (now % windowSeconds);
        Window window = windows.compute(tier + ":" + clientIp, (key, current) ->
                current == null || current.windowStart() != windowStart ? new Window(windowStart, 1) : current.increment());

        if (window.count() > maxRequestsByTier.get(tier)) {
            long retryAfter = windowStart + windowSeconds - now;
            log.warn("請求頻率超過上限: tier={}, clientIp={}, count={}", tier, clientIp, window.count());
            handlerExceptionResolver.resolveException(request, response, null, new RateLimitExceededException(retryAfter));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 每 5 分鐘清除已結束之時間窗，避免記憶體隨不同 IP 持續成長
     */
    @Scheduled(fixedRate = 300000)
    public void evictExpiredWindows() {
        long currentWindowStart = currentEpochSecond() - (currentEpochSecond() % windowSeconds);
        windows.values().removeIf(window -> window.windowStart() < currentWindowStart);
    }

    /**
     * 取得目前時間 (epoch 秒)；獨立成方法讓測試可以覆寫成固定時間
     * @return 目前 epoch 秒
     */
    long currentEpochSecond() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 依路徑與方法判定端點等級
     * @param request HTTP 請求
     * @return AUTH、SUBMISSION 或 GENERAL
     */
    private static Tier resolveTier(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith(AUTH_PREFIX)) {
            return Tier.AUTH;
        }
        if (HttpMethod.POST.matches(request.getMethod()) && path.equals(SUBMISSION_PATH)) {
            return Tier.SUBMISSION;
        }
        return Tier.GENERAL;
    }
}
