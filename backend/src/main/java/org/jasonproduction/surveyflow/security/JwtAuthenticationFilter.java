/**
 * ============================================================================
 * 檔案名稱: JwtAuthenticationFilter.java
 * 所屬模組: Security 安全認證模組 (資安 S 級)
 * 責任描述: 每個 HTTP 請求執行一次，依序：
 *   1. 從 Authorization 標頭取出 Bearer Token (沒有就直接放行，視為匿名)
 *   2. TokenProvider 驗簽與效期 (失敗 → 匿名)
 *   3. TokenRevocationService 檢查是否已登出或被整批作廢 (是 → 匿名)
 *   4. 拒絕以 Refresh Token 呼叫業務 API (防止長效 Token 被當成通行證)
 *   5. 載入帳號，確認仍為 ACTIVE (停權 / 待驗證 → 匿名)
 *   6. 全部通過才寫入 SecurityContext，Controller 才能以 @AuthenticationPrincipal 取得登入者
 *   任何一步失敗都只是「不寫入登入者」，請求仍往下走，由 SecurityConfig 的授權規則決定回 401 或放行公開端點。
 * 架構分層: Security / Filter
 * 聯動關係:
 *   - 上游依賴: TokenProvider、TokenRevocationService、CustomUserDetailsService
 *   - 下游消費: SecurityConfig (插入於 UsernamePasswordAuthenticationFilter 之前)
 * 邊界處理: 例外一律記錄日誌後吞下，不讓單一壞 Token 造成 500
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;
    private final TokenRevocationService tokenRevocationService;
    private final CustomUserDetailsService userDetailsService;

    /**
     * 每個請求執行一次：解析 Bearer Token → 驗簽與效期 → 黑名單檢核 → 拒絕 Refresh Token 冒用 →
     * 載入帳號並確認為啟用狀態後寫入 SecurityContext；任一環節失敗僅記錄日誌並以匿名身分續行，由授權規則回應 401
     * @param request HTTP 請求
     * @param response HTTP 回應
     * @param filterChain 後續過濾鏈
     * @throws ServletException Servlet 例外
     * @throws IOException I/O 例外
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String jti = tokenProvider.getJtiFromToken(jwt);
                String tokenOwner = tokenProvider.getUsernameFromToken(jwt);

                // 1. 檢查是否遭主動吊銷 (單一 Token 黑名單，或該使用者已整批作廢)
                if (tokenRevocationService.isRevoked(jti, tokenOwner, tokenProvider.getIssuedAt(jwt))) {
                    log.warn("拒絕存取: 當前 JWT 憑證已被主動吊銷! jti={}", jti);
                } else if (tokenProvider.isRefreshToken(jwt)) {
                    // 2. 防範憑證類型混淆：Refresh Token 不得直接作為業務 API 存取憑證
                    log.warn("拒絕存取: 偵測到以 Refresh Token 嘗試調用業務 API! jti={}", jti);
                } else {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(tokenOwner);

                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        // 3. 帳號已停權或尚未驗證：即使 Token 未過期也不建立認證，後續由授權規則回應 401
                        log.warn("拒絕存取: 帳號非啟用狀態! username={}", tokenOwner);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("無法於 Security 上下文建立使用者認證: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 從 HTTP 請求標頭中提取 Bearer Token 字串
     * @param request HTTP 請求物件
     * @return 乾淨之 JWT 字串，若無則回傳 null
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
