/**
 * ============================================================================
 * 檔案名稱: SecurityConfig.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 配置 Spring Security 6+ 過濾鏈條 (SecurityFilterChain)、CORS 跨來源共用、CSRF 防護與端點白名單
 * 架構分層: Security / Configuration Layer
 * 聯動關係:
 *   - 上游依賴: JwtAuthenticationFilter, CustomUserDetailsService, PasswordEncoder
 *   - 下游消費: Spring Security 核心過濾流程
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
    private final PasswordEncoder passwordEncoder;

    /**
     * 配置安全過濾鏈
     * @param http HttpSecurity 建構器
     * @return SecurityFilterChain 實例
     * @throws Exception 配置例外
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置身分認證提供者 (DaoAuthenticationProvider)
     * @return AuthenticationProvider 實例
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
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
     * 配置 CORS 跨來源規則，允許前端 (Angular http://localhost:4200) 存取
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:4200", "http://127.0.0.1:4200", "*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
