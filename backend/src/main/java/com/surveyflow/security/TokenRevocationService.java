/**
 * ============================================================================
 * 檔案名稱: TokenRevocationService.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 負責零信任架構下之 Token 即時主動吊銷 (Active Revocation) 與 Refresh Token 輪替防重放狀態管理
 * 架構分層: Security / Service Layer
 * 聯動關係:
 *   - 上游依賴: TokenProvider, application.properties
 *   - 下游消費: JwtAuthenticationFilter, AuthService
 * 邊界處理: 採用高並發執行緒安全記憶體快取維護 JTI 黑名單，若 JTI 為空則安全放行，定期自動淘汰已過期之記錄
 * ============================================================================
 */

package com.surveyflow.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TokenRevocationService {

    /** 已被吊銷之 Access Token / Refresh Token JTI 黑名單 (Key: jti, Value: 過期時間戳毫秒) */
    private final Map<String, Long> blacklistedJtis = new ConcurrentHashMap<>();

    /** 活躍之 Refresh Token 登記表 (Key: jti, Value: 帳號名稱)，用於 RTR 輪替與重放偵測 */
    private final Map<String, String> activeRefreshTokens = new ConcurrentHashMap<>();

    /**
     * 主動吊銷特定 Token (登出或帳號凍結時調用)
     * @param jti Token 唯一識別碼
     * @param remainingTtlMs Token 距離自然過期之剩餘毫秒數
     */
    public void revokeToken(String jti, long remainingTtlMs) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        long expiryTime = System.currentTimeMillis() + Math.max(remainingTtlMs, 0);
        blacklistedJtis.put(jti, expiryTime);
        activeRefreshTokens.remove(jti);
        log.info("Token 已成功加入吊銷黑名單: jti={}, 剩餘存活毫秒={}", jti, remainingTtlMs);
    }

    /**
     * 檢核特定 JTI 是否處於黑名單中
     * @param jti Token 唯一識別碼
     * @return true 若已遭主動吊銷
     */
    public boolean isRevoked(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        Long expiryTime = blacklistedJtis.get(jti);
        if (expiryTime == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiryTime) {
            blacklistedJtis.remove(jti);
            return false;
        }
        return true;
    }

    /**
     * 註冊新簽發之有效 Refresh Token
     * @param jti Refresh Token 之 JTI
     * @param username 使用者帳號
     */
    public void registerRefreshToken(String jti, String username) {
        if (StringUtils.hasText(jti) && StringUtils.hasText(username)) {
            activeRefreshTokens.put(jti, username);
        }
    }

    /**
     * 執行 Refresh Token 輪替 (RTR) 驗證與舊 Token 作廢
     * @param oldJti 當前請求換發之 Refresh Token JTI
     * @param newJti 新簽發之 Refresh Token JTI
     * @param username 使用者帳號
     * @param oldRemainingTtlMs 舊 Token 剩餘存活時間
     * @return true 若輪替成功；false 若偵測到已失效舊憑證 (潛在重放攻擊)
     */
    public boolean rotateRefreshToken(String oldJti, String newJti, String username, long oldRemainingTtlMs) {
        if (!StringUtils.hasText(oldJti)) {
            return false;
        }
        // 若黑名單已有該 JTI，代表該 Refresh Token 已被吊銷或已被輪替過
        if (isRevoked(oldJti)) {
            log.warn("偵測到重放攻擊警告: 已作廢之 Refresh Token 再次嘗試換發! jti={}, username={}", oldJti, username);
            return false;
        }

        // 將舊 Refresh Token 即刻作廢並加入黑名單
        revokeToken(oldJti, oldRemainingTtlMs);

        // 註冊新 Refresh Token
        registerRefreshToken(newJti, username);
        return true;
    }

    /**
     * 定時清理過期之黑名單快取資料 (每 30 分鐘執行一次)
     */
    @Scheduled(fixedRate = 1800000)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        blacklistedJtis.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
