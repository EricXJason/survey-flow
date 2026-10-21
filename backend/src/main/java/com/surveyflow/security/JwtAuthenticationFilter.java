/**
 * ============================================================================
 * 檔案名稱: JwtAuthenticationFilter.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 攔截每個 HTTP 請求，提取 Authorization 標頭中之 Bearer JWT 權杖，檢驗有效性、JTI 吊銷黑名單與 Token 類型，注入安全上下文
 * 架構分層: Security / Filter Layer
 * 聯動關係:
 *   - 上游依賴: TokenProvider, TokenRevocationService, CustomUserDetailsService
 *   - 下游消費: SecurityContextHolder, SecurityConfig
 * 邊界處理: 若 Token 缺失、過期、遭黑名單吊銷或為 RefreshToken，則不注入認證上下文，由後續過濾鏈阻斷
 * ============================================================================
 */

package com.surveyflow.security;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String jti = tokenProvider.getJtiFromToken(jwt);

                // 1. 檢查是否遭主動吊銷 (黑名單檢核)
                if (tokenRevocationService.isRevoked(jti)) {
                    log.warn("拒絕存取: 當前 JWT 憑證已被主動吊銷! jti={}", jti);
                } else if (tokenProvider.isRefreshToken(jwt)) {
                    // 2. 防範憑證類型混淆：Refresh Token 不得直接作為業務 API 存取憑證
                    log.warn("拒絕存取: 偵測到以 Refresh Token 嘗試調用業務 API! jti={}", jti);
                } else {
                    String username = tokenProvider.getUsernameFromToken(jwt);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
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
