/**
 * ============================================================================
 * 檔案名稱: SecurityConfig.java
 * 所屬模組: Security 安全認證模組 (資安 S 級)
 * 責任描述: 全系統安全設定之唯一來源：過濾鏈、URL 授權規則、未認證 / 權限不足回應、Argon2id 密碼雜湊器、CORS 白名單。
 *
 * 一個請求經過的順序：
 *   0. RequestTraceFilter (過濾鏈之外、順序最高)：產生 traceId、解析用戶端 IP 寫入 MDC。
 *   1. CorsFilter：檢查 Origin 是否在白名單；預檢 (OPTIONS) 請求在此直接回應。
 *   2. RateLimitFilter：依 IP 與端點等級限流，超量回 429 (Retry-After)。
 *   3. JwtAuthenticationFilter：解析 Authorization: Bearer <Access Token>，驗簽、檢查黑名單與帳號狀態，
 *      通過才把登入者寫入 SecurityContext；任何失敗都只是「不寫入」，請求以匿名身分繼續往下走。
 *   4. AuthorizationFilter：依下方 authorizeHttpRequests 規則判斷能否進入 Controller：
 *        - 匿名者存取需登入之路徑 → authenticationEntryPoint 回 401 (前端收到後自動換發 Token)
 *        - 已登入但角色不足 (如一般會員打 /api/admin/**) → accessDeniedHandler 回 403
 *   5. Controller → Service：問卷擁有者等「資料層級」權限於 Service 內再核對一次。
 *   過濾鏈內產生之 401 / 403 / 429 皆經 HandlerExceptionResolver 交給 GlobalExceptionHandler，
 *   與 Controller 例外同為 RFC 9457 Problem Details 格式。
 *
 * 架構分層: Security / Configuration Layer
 * 聯動關係:
 *   - 上游依賴: JwtAuthenticationFilter、RateLimitFilter、HandlerExceptionResolver、application.properties (Argon2id 參數、CORS 來源 / 方法 / 標頭)
 *   - 下游消費: Spring Security 過濾流程、AuthService / UserService (注入 PasswordEncoder)
 * 邊界處理: 不使用 Session 與表單登入 (登入由 AuthService 自行比對密碼並簽發 JWT)，
 *           故不註冊 AuthenticationManager / DaoAuthenticationProvider。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.jasonproduction.surveyflow.common.web.RequestTraceFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    /**
     * 密碼雜湊器：Argon2id (抗 GPU / ASIC 暴力破解，每次雜湊自動產生隨機鹽)
     * 參數全部由 application.properties 注入，可依伺服器效能調整而不必改程式。
     * @param saltLength 隨機鹽長度 (bytes)
     * @param hashLength 雜湊結果長度 (bytes)
     * @param parallelism 平行計算執行緒數
     * @param memory 記憶體用量 (KB)，越大越難以硬體暴力破解
     * @param iterations 迭代次數
     * @return PasswordEncoder Bean
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${app.security.argon2.salt-length}") int saltLength,
            @Value("${app.security.argon2.hash-length}") int hashLength,
            @Value("${app.security.argon2.parallelism}") int parallelism,
            @Value("${app.security.argon2.memory}") int memory,
            @Value("${app.security.argon2.iterations}") int iterations) {
        return new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory, iterations);
    }

    /**
     * 安全過濾鏈：設定 CORS、CSRF、Session、未認證 / 權限不足回應、URL 授權規則，並把限流與 JWT 過濾器插入鏈中
     * @param http Spring Security 設定建構器
     * @param handlerExceptionResolver Spring MVC 例外解析器 (把過濾鏈內之錯誤交給 GlobalExceptionHandler)
     * @return SecurityFilterChain
     * @throws Exception 設定錯誤
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) throws Exception {
        http
                // [Why]: 前端 (pages.dev) 與後端 (onrender.com) 不同網域，必須開啟 CORS，白名單見 corsConfigurationSource
                .cors(Customizer.withDefaults())
                // [Why]: 業務 API 以 Authorization 標頭攜帶 Token，瀏覽器不會自動夾帶，CSRF 攻擊無從利用，故關閉 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // [Why]: 每個請求都以 JWT 自證身分，伺服器不保存登入狀態，水平擴充時無需共享 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // [Why]: 未認證一律回 401；若不設定，Spring 預設回 403，前端無法區分「要換發 Token」與「沒有權限」。
                //        401 / 403 都轉交 GlobalExceptionHandler，與其他錯誤同為 Problem Details 格式，而非各自手寫 JSON
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, authException) -> handlerExceptionResolver.resolveException(
                                request, response, null, new InsufficientAuthenticationException(authException.getMessage())))
                        .accessDeniedHandler((request, response, accessDeniedException) -> handlerExceptionResolver.resolveException(
                                request, response, null, accessDeniedException)))
                .authorizeHttpRequests(auth -> auth
                        // 規則由上而下比對，第一個符合者生效
                        // 1. 公開端點：登入註冊、問卷大廳、公開填答與提交 (預覽另於 SurveyService 核對擁有者)
                        .requestMatchers("/api/public/**").permitAll()
                        // 2. 管理員端點：除此規則外，AdminController 類別上另有 @PreAuthorize 雙重把關
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 3. 健康檢查探針：供 Render 與 UptimeRobot 保活，僅開放 health 且不顯示細節
                        .requestMatchers("/actuator/**").permitAll()
                        // 4. 其餘 (/api/user/**、/api/analytics/**) 皆需登入
                        .anyRequest().authenticated()
                )
                // [Why]: 限流排在 CorsFilter 之後 (429 也帶 CORS 標頭)、JWT 驗證之前 (超量請求不查資料庫)
                .addFilterAfter(rateLimitFilter, CorsFilter.class)
                // [Why]: 必須排在 UsernamePasswordAuthenticationFilter 之前，授權判斷時 SecurityContext 才已有登入者
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 白名單：只允許 FRONTEND_BASE_URL 與 CORS_ALLOWED_ORIGINS，程式內不寫死任何來源
     * [Why]: allowCredentials=true (換發 Token 需帶 Cookie) 時，白名單過寬會讓任意網站以使用者身分呼叫 API 並讀取回應
     * @param allowedOrigins 額外允許之來源樣式 (逗號分隔，支援 http://localhost:* 之萬用埠寫法)
     * @param frontendBaseUrl 正式前端網址
     * @param allowedMethods 允許之 HTTP 方法 (逗號分隔)
     * @param allowedHeaders 允許之請求標頭 (逗號分隔)
     * @param maxAgeSeconds 預檢結果快取秒數 (減少 OPTIONS 請求)
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.cors.allowed-methods}") String allowedMethods,
            @Value("${app.cors.allowed-headers}") String allowedHeaders,
            @Value("${app.cors.max-age-seconds}") long maxAgeSeconds) {
        Set<String> origins = new LinkedHashSet<>();
        origins.addAll(splitCsv(frontendBaseUrl));
        origins.addAll(splitCsv(allowedOrigins));

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(new ArrayList<>(origins));
        configuration.setAllowedMethods(splitCsv(allowedMethods));
        configuration.setAllowedHeaders(splitCsv(allowedHeaders));
        // [Why]: 跨網域時瀏覽器預設不讓前端讀取自訂標頭；開放追蹤碼與重試秒數，前端錯誤訊息才能附上追蹤碼
        configuration.setExposedHeaders(List.of(RequestTraceFilter.TRACE_ID_HEADER, HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 將逗號分隔之設定值拆成清單 (去除空白與空項目)
     * @param csv 逗號分隔字串，可為 null
     * @return 清單，輸入為空時回傳空清單
     */
    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
