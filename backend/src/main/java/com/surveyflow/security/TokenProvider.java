/**
 * ============================================================================
 * 檔案名稱: TokenProvider.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 負責 HMAC-SHA256 簽署、產生 Access Token 與 Refresh Token，以及解析與檢驗 JWT
 * 架構分層: Security / Service Layer
 * 聯動關係:
 *   - 上游依賴: application.yml (jwt.secret, jwt.expiration)
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

@Slf4j
@Component
public class TokenProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public TokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * 簽發短期 Access Token (15 分鐘)
     * @param username 使用者帳號
     * @param role 角色名稱
     * @return JWT 字串
     */
    public String generateAccessToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 簽發長效 Refresh Token (7 天)
     * @param username 使用者帳號
     * @return JWT 字串
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 從 JWT 解析使用者帳號 (Subject)
     * @param token JWT 字串
     * @return 使用者帳號
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * 驗證 JWT 簽名與有效性
     * @param token 待檢核之 JWT 字串
     * @return true 若有效
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
}
