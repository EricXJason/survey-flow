/**
 * ============================================================================
 * 檔案名稱: TokenRevocationService.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 管理 Token 吊銷狀態，提供兩層判定，狀態全部存放於資料庫：
 *           1. 單一 Token 黑名單 (revoked_tokens，以 jti 為主鍵)：登出、Refresh Token 輪替時作廢指定 Token。
 *           2. 使用者層級作廢時間點 (users.tokens_revoked_before)：偵測到 Refresh Token 重放或重設密碼時，
 *              作廢該使用者「此時間點之前簽發」的全部 Token。
 * 架構分層: Security / Service Layer
 * 聯動關係:
 *   - 上游依賴: RevokedTokenRepository、UserRepository、ApplicationEventPublisher (重放時發布稽核事件)；jti、簽發時間、剩餘效期由呼叫端經 TokenProvider 解析後傳入
 *   - 下游消費: JwtAuthenticationFilter (每個請求檢核)、AuthService (換發、登出、重設密碼)
 * 邊界處理: [Why]: 存放於資料庫而非 JVM 記憶體：Render 免費方案閒置即休眠、喚醒等於重啟，
 *           若存在記憶體，重啟後已登出或已輪替之 Refresh Token (效期 7 天) 會重新可用，重放偵測也會失效。
 *           寫入方法以 REQUIRED 傳播加入呼叫端交易；AuthService.refreshToken 對 BadCredentialsException 不回滾，
 *           確保「偵測到重放 → 整批作廢」在拒絕換發後仍被保存。過期黑名單列由排程每 30 分鐘刪除。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.jasonproduction.surveyflow.repository.RevokedTokenRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 將單一 Token 加入黑名單，直到其原本到期時間為止
     * 第一步：jti 為空時略過 (無法識別之 Token 不寫入，避免黑名單被灌入無效資料)。
     * 第二步：以「現在 + 剩餘效期」作為到期時間寫入；已在黑名單者維持原狀 (等冪)。
     * @param jti Token 唯一識別碼
     * @param remainingTtlMs Token 剩餘存活毫秒數 (負值視為 0)
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeToken(String jti, long remainingTtlMs) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        insertIntoBlacklist(jti, remainingTtlMs);
        log.info("Token 已加入吊銷黑名單: jti={}, 剩餘存活毫秒={}", jti, remainingTtlMs);
    }

    /**
     * 作廢指定使用者在「現在」之前簽發的所有 Token (Access 與 Refresh 皆適用)
     * [Why]: JWT 的 iat (簽發時間) 只精確到秒，故作廢時間點截斷至整秒；
     *        同一秒內稍後重新登入簽發之新 Token (iat 等於作廢時間點) 不會被誤擋。
     * @param username 使用者帳號
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllTokensOf(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        userRepository.updateTokensRevokedBeforeNative(username, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        log.warn("已作廢使用者全部既有 Token: username={}", username);
    }

    /**
     * 判定 Token 是否已失效：jti 在黑名單中 (且未過期)，或簽發時間早於該使用者之作廢時間點
     * @param jti Token 唯一識別碼，可為 null
     * @param username Token 所屬帳號
     * @param issuedAt Token 簽發時間，可為 null (null 時僅檢查黑名單)
     * @return true 表示必須拒絕此 Token
     */
    @Transactional(readOnly = true)
    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        return revokedTokenRepository.countRevocationMatches(
                StringUtils.hasText(jti) ? jti : null, username, toLocalDateTime(issuedAt), LocalDateTime.now()) > 0;
    }

    /**
     * Refresh Token 輪替 (Refresh Token Rotation)：每次換發都作廢舊 Refresh Token
     * 第一步：舊 Token 簽發時間早於使用者作廢時間點 → 已被整批作廢，回傳 false。
     * 第二步：以 INSERT IGNORE 將舊 jti 寫入黑名單。寫入 0 筆代表這顆 Token 先前已被用過，現在又被拿來換發，
     *         視為「遭竊重放」，作廢該使用者全部 Token (攻擊者與受害者手上的都失效，受害者需重新登入)，回傳 false。
     * 第三步：寫入 1 筆 → 輪替成功，回傳 true，呼叫端才可簽發新 Token。
     * @param oldJti 本次用來換發之 Refresh Token jti
     * @param username Token 所屬帳號
     * @param issuedAt 舊 Refresh Token 簽發時間
     * @param oldRemainingTtlMs 舊 Refresh Token 剩餘存活毫秒數
     * @return true 表示輪替成功、可簽發新 Token；false 表示必須拒絕換發
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean rotateRefreshToken(String oldJti, String username, Instant issuedAt, long oldRemainingTtlMs) {
        if (!StringUtils.hasText(oldJti)) {
            return false;
        }
        if (isRevoked(null, username, issuedAt)) {
            return false;
        }
        if (!insertIntoBlacklist(oldJti, oldRemainingTtlMs)) {
            log.warn("偵測到 Refresh Token 重放，作廢該使用者全部 Token: jti={}, username={}", oldJti, username);
            revokeAllTokensOf(username);
            eventPublisher.publishEvent(new AuditEvent(AuditAction.TOKEN_REPLAY_DETECTED, username, null, null,
                    "已作廢之 Refresh Token 再次被使用，已作廢該使用者全部 Token"));
            return false;
        }
        return true;
    }

    /**
     * 每 30 分鐘刪除已過期之黑名單列 (Token 本身已過期，驗簽即會失敗，不需再記錄)
     */
    @Scheduled(fixedRate = 1800000)
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredTokens() {
        int deleted = revokedTokenRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("已清除過期黑名單 {} 筆", deleted);
        }
    }

    /**
     * 以「現在 + 剩餘效期」為到期時間，將 jti 原子寫入黑名單
     * @param jti Token 唯一識別碼
     * @param remainingTtlMs Token 剩餘存活毫秒數 (負值視為 0)
     * @return true 表示本次新寫入；false 表示 jti 先前已在黑名單
     */
    private boolean insertIntoBlacklist(String jti, long remainingTtlMs) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(Math.max(remainingTtlMs, 0), ChronoUnit.MILLIS);
        return revokedTokenRepository.insertIfAbsent(jti, expiresAt, now) > 0;
    }

    /**
     * 將 JWT 的 Instant 轉為 JVM 時區 (Asia/Taipei) 之 LocalDateTime，與資料庫 DATETIME 欄位比較
     * @param instant 時間點，可為 null
     * @return 對應之 LocalDateTime；輸入為 null 時回傳 null
     */
    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
