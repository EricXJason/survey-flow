/**
 * ============================================================================
 * 檔案名稱: JwtAuthenticationFilter.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 攔截每個 HTTP 請求，提取 Authorization 標頭中之 Bearer JWT 權杖並驗證上下文身分
 * 架構分層: Security / Filter Layer
 * 聯動關係:
 *   - 上游依賴: TokenProvider, CustomUserDetailsService
 *   - 下游消費: SecurityContextHolder, SecurityConfig
 * 邊界處理: 若 Token 缺失或無效，則放行讓後續 SecurityFilterChain 決定是否需要認證
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
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
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
