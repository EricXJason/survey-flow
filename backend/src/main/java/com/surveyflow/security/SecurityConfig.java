/**
 * ============================================================================
 * 檔案名稱: SecurityConfig.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 配置 Spring Security 6+ 核心架構，包含安全過濾鏈 (SecurityFilterChain)、Argon2id 密碼雜湊器、身分認證提供者 (DaoAuthenticationProvider)、端點白名單與 CORS 跨來源規則
 * 架構分層: Security / Configuration Layer (全系統安全配置 SSOT)
 * 聯動關係:
 *   - 上游依賴: JwtAuthenticationFilter, CustomUserDetailsService
 *   - 下游消費: Spring Security 核心過濾流程、AuthService (注入 PasswordEncoder)
 * 邊界處理: 採用無狀態 (STATELESS) 策略，禁用 Session Cookie，全靠 Bearer JWT 授權
 * ============================================================================
 */

package com.surveyflow.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * 配置全域密碼雜湊器 (採用密碼學最高標準之 Argon2id 演算法)
     * 支援從 application.properties 動態注入參數，兼顧極高安全性與伺服器效能
     * @param saltLength 隨機鹽值長度 (bytes)
     * @param hashLength 雜湊位元長度 (bytes)
     * @param parallelism 平行計算執行緒數
     * @param memory 記憶體佔用量 (KB)
     * @param iterations 迭代計算輪數
     * @return PasswordEncoder Bean 實例
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @org.springframework.beans.factory.annotation.Value("${app.security.argon2.salt-length:16}") int saltLength,
            @org.springframework.beans.factory.annotation.Value("${app.security.argon2.hash-length:32}") int hashLength,
            @org.springframework.beans.factory.annotation.Value("${app.security.argon2.parallelism:1}") int parallelism,
            @org.springframework.beans.factory.annotation.Value("${app.security.argon2.memory:4096}") int memory,
            @org.springframework.beans.factory.annotation.Value("${app.security.argon2.iterations:3}") int iterations) {
        return new org.springframework.security.crypto.argon2.Argon2PasswordEncoder(saltLength, hashLength, parallelism, memory, iterations);
    }

    /**
     * 配置身分認證提供者 (DaoAuthenticationProvider)
     * 結合 CustomUserDetailsService 與 Argon2id PasswordEncoder
     * @param passwordEncoder 密碼編碼器
     * @return AuthenticationProvider 實例
     */
    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * 配置安全過濾鏈 (SecurityFilterChain)
     * @param http HttpSecurity 建構器
     * @param authenticationProvider 身分認證提供者
     * @return SecurityFilterChain 實例
     * @throws Exception 配置例外
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. 公開端點 (免認證)：公開認證、問卷大廳、公開填答與提交
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/surveys/lobby/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/surveys/*/public").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/submissions/**").permitAll()
                        // 2. 系統管理員專屬端點 (需 ROLE_ADMIN)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 3. Actuator 監控與健康檢查 (AWS 部署健康檢查)
                        .requestMatchers("/actuator/**").permitAll()
                        // 4. 其他會員端點皆需 Bearer JWT 認證
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置 AuthenticationManager
     * @param config 認證設定
     * @return AuthenticationManager 實例
     * @throws Exception 初始化例外
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置 CORS 跨來源規則，由 application.properties 動態注入，支援環境變數自訂來源
     * @param allowedOrigins 允許之前端來源列表 (逗號分隔)
     * @param allowedMethods 允許之 HTTP 請求方法
     * @param allowedHeaders 允許之 HTTP 請求標頭
     * @param maxAgeSeconds 預檢請求快取秒數
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}") String allowedOrigins,
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Requested-With,Accept}") String allowedHeaders,
            @org.springframework.beans.factory.annotation.Value("${app.cors.max-age-seconds:3600}") long maxAgeSeconds) {
        CorsConfiguration configuration = new CorsConfiguration();
        
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        configuration.setAllowedOriginPatterns(origins.isEmpty() ? List.of("*") : origins);

        List<String> methods = Arrays.stream(allowedMethods.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        configuration.setAllowedMethods(methods);

        List<String> headers = Arrays.stream(allowedHeaders.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        configuration.setAllowedHeaders(headers);

        configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
