/**
 * ============================================================================
 * 檔案名稱: ClientIpResolver.java
 * 所屬模組: Common Web 請求工具 (資安 S 級：速率限制與稽核依賴之來源 IP)
 * 責任描述: 解析請求之真實用戶端 IP，供速率限制、填答紀錄與稽核日誌使用
 * 架構分層: Common / Web Layer
 * 聯動關係:
 *   - 上游依賴: application.properties (app.request.trusted-proxy-count ← 環境變數 TRUSTED_PROXY_COUNT)
 *   - 下游消費: RequestTraceFilter (寫入 MDC clientIp)、RateLimitFilter、SubmissionController
 * 邊界處理: [Why]: X-Forwarded-For 的「最左邊」可由用戶端任意偽造；每經過一層可信任代理，代理會在「最右邊」附加它看到的來源。
 *           因此由右往左數第 N 個 (N = 可信任代理層數) 才是可信任代理實際看到的用戶端 IP。
 *           N = 0 時完全忽略 X-Forwarded-For (本機直連)；標頭項目不足 N 個時取最左邊一筆。
 *           部署後應比對結構化日誌中之 clientIp 是否為真實來源，再調整 N。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Component
public class ClientIpResolver {

    /** 反向代理附加來源 IP 之標頭 */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** IPv6 最長 45 字元，與 submissions.ip_address 欄位長度一致 */
    private static final int MAX_IP_LENGTH = 45;

    private final int trustedProxyCount;

    /**
     * @param trustedProxyCount 應用程式前方可信任反向代理之層數 (Render 為 1；本機直連為 0)
     */
    public ClientIpResolver(@Value("${app.request.trusted-proxy-count}") int trustedProxyCount) {
        this.trustedProxyCount = Math.max(trustedProxyCount, 0);
    }

    /**
     * 解析用戶端 IP
     * 第一步：未設定可信任代理或請求沒有 X-Forwarded-For → 使用 TCP 連線來源 (getRemoteAddr)。
     * 第二步：拆解 X-Forwarded-For，由右往左取第 N 筆 (不足時取最左邊)。
     * 第三步：截斷至 45 字元，避免超長偽造值寫入資料庫失敗。
     * @param request HTTP 請求
     * @return 用戶端 IP 字串
     */
    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (trustedProxyCount == 0 || !StringUtils.hasText(forwardedFor)) {
            return truncate(request.getRemoteAddr());
        }
        String[] hops = Arrays.stream(forwardedFor.split(",")).map(String::trim).filter(StringUtils::hasText).toArray(String[]::new);
        if (hops.length == 0) {
            return truncate(request.getRemoteAddr());
        }
        int index = Math.max(hops.length - trustedProxyCount, 0);
        return truncate(hops[index]);
    }

    /**
     * 截斷過長字串
     * @param ip 原始 IP 字串，可為 null
     * @return 最多 45 字元之字串；輸入為 null 時回傳 null
     */
    private static String truncate(String ip) {
        return ip != null && ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }
}
