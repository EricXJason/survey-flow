/**
 * ============================================================================
 * 檔案名稱: LoginAttemptLimiter.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 記錄各帳號連續登入失敗次數，達上限後於冷卻期間拒絕登入，防禦線上暴力猜測密碼
 * 架構分層: Security / Component (供 AuthService 向下呼叫之純狀態元件)
 * 聯動關係:
 *   - 上游依賴: application.properties (app.security.login.max-failures、app.security.login.lock-minutes)
 *   - 下游消費: AuthService.login()
 * 邊界處理: 以帳號 (去空白、轉小寫) 為鍵，不論帳號是否存在一律計數，避免由鎖定行為推測帳號存在與否；
 *           計數存於 JVM 記憶體 (單機部署)，服務重啟即歸零；定期清除已過冷卻期之紀錄防止記憶體增長
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoginAttemptLimiter {

    /**
     * 單一帳號之失敗紀錄
     * @param failures 冷卻視窗內連續失敗次數
     * @param lastFailureAt 最近一次失敗時間
     * @param lockedUntil 鎖定解除時間，未鎖定時為 null
     */
    private record Attempt(int failures, Instant lastFailureAt, Instant lockedUntil) {
    }

    private final int maxFailures;
    private final Duration lockDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 建構子注入鎖定門檻與冷卻時間
     * @param maxFailures 連續失敗幾次後鎖定
     * @param lockMinutes 鎖定分鐘數 (同時作為失敗次數之計算視窗)
     */
    public LoginAttemptLimiter(@Value("${app.security.login.max-failures}") int maxFailures,
                               @Value("${app.security.login.lock-minutes}") long lockMinutes) {
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    /**
     * 帳號鎖定中則拒絕登入
     * @param username 登入帳號
     * @throws LockedException 仍在冷卻期間，訊息含剩餘分鐘數
     */
    public void ensureNotLocked(String username) {
        Attempt attempt = attempts.get(normalize(username));
        Instant now = Instant.now();
        if (attempt != null && attempt.lockedUntil() != null && now.isBefore(attempt.lockedUntil())) {
            long remainingMinutes = Math.max(1, (Duration.between(now, attempt.lockedUntil()).toSeconds() + 59) / 60);
            throw new LockedException("登入失敗次數過多，請於 " + remainingMinutes + " 分鐘後再試");
        }
    }

    /**
     * 記錄一次登入失敗；距上次失敗超過冷卻時間或前次鎖定已解除時重新計數，達門檻即鎖定
     * @param username 登入帳號
     */
    public void recordFailure(String username) {
        Instant now = Instant.now();
        Attempt updated = attempts.compute(normalize(username), (key, previous) -> {
            int failures = isStale(previous, now) ? 1 : previous.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockDuration) : null;
            return new Attempt(failures, now, lockedUntil);
        });
        if (updated.lockedUntil() != null) {
            log.warn("帳號登入失敗達 {} 次，暫時鎖定: username={}", maxFailures, username);
        }
    }

    /**
     * 登入成功後清除失敗紀錄
     * @param username 登入帳號
     */
    public void recordSuccess(String username) {
        attempts.remove(normalize(username));
    }

    /**
     * 每 30 分鐘清除已過冷卻期之紀錄，避免任意帳號字串累積佔用記憶體
     */
    @Scheduled(fixedRate = 1800000)
    public void cleanupExpiredAttempts() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
    }

    /**
     * 判定紀錄是否已失效 (無紀錄、鎖定已解除，或未鎖定且距上次失敗已超過冷卻時間)
     * @param attempt 失敗紀錄，可為 null
     * @param now 當前時間
     * @return true 表示應重新計數
     */
    private boolean isStale(Attempt attempt, Instant now) {
        if (attempt == null) {
            return true;
        }
        if (attempt.lockedUntil() != null) {
            return !now.isBefore(attempt.lockedUntil());
        }
        return now.isAfter(attempt.lastFailureAt().plus(lockDuration));
    }

    /**
     * 帳號鍵值正規化 (去空白、轉小寫)
     * @param username 原始帳號
     * @return 正規化後之鍵值
     */
    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
