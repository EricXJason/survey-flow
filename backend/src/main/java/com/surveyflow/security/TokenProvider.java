/**
 * ============================================================================
 * 檔案名稱: TokenProvider.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 負責 HMAC-SHA256 簽署、產生短效 Access Token 與長效 Refresh Token，以及解析與檢驗 JWT
 * 架構分層: Security / Service Layer
 * 聯動關係:
 *   - 上游依賴: application.properties (jwt.secret, jwt.access-token-expiration-ms, jwt.refresh-token-expiration-ms)
 *   - 下游消費: JwtAuthenticationFilter, AuthService
 * ============================================================================
 */

package com.surveyflow.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class TokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public TokenProvider(
            @Value("${jwt.secret:SurveyFlowSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong2026!}") String secret,
            @Value("${jwt.access-token-expiration-ms:${jwt.expiration-ms:900000}}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * 簽發短效 Access Token (預設 15 分鐘，大幅限縮外洩威脅窗口)
     * @param username 使用者帳號
     * @param role 角色名稱 (ROLE_USER, ROLE_ADMIN)
     * @return 簽署完成之 Access Token JWT 字串
     */
    public String generateAccessToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("role", role)
                .claim("token_type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 簽發長效 Refresh Token (預設 7 天，用於無感輪替換發 Access Token)
     * @param username 使用者帳號
     * @return 簽署完成之 Refresh Token JWT 字串
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("token_type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 向下相容簽發 Token 方法 (委派短效 Access Token)
     * @param username 使用者帳號
     * @param role 角色名稱
     * @return Access Token
     */
    public String generateToken(String username, String role) {
        return generateAccessToken(username, role);
    }

    /**
     * 從 JWT 解析使用者帳號 (Subject)
     * @param token JWT 字串
     * @return 使用者帳號
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 從 JWT 提取唯一識別碼 (JTI)
     * @param token JWT 字串
     * @return JTI 識別碼
     */
    public String getJtiFromToken(String token) {
        try {
            return parseClaims(token).getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 計算 Token 距離自然過期之剩餘毫秒數
     * @param token JWT 字串
     * @return 剩餘毫秒數 (已過期回傳 0)
     */
    public long getRemainingTtlMs(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long diff = expiration.getTime() - System.currentTimeMillis();
            return Math.max(diff, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 檢查 Token 是否為 Refresh Token
     * @param token JWT 字串
     * @return true 若 token_type 為 refresh
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equalsIgnoreCase(claims.get("token_type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 驗證 JWT 簽名與有效性
     * @param token 待檢核之 JWT 字串
     * @return true 若簽名有效且未過期
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("無效或過期之 JWT 憑證: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 內部解析 JWT 聲明 (Claims)
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
