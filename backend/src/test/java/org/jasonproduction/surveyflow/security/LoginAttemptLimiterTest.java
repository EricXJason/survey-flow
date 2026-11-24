/**
 * ============================================================================
 * 檔案名稱: LoginAttemptLimiterTest.java
 * 所屬模組: Test 測試層 - 登入失敗鎖定單元測試
 * 責任描述: 驗證 LoginAttemptLimiter 之失敗計數、達門檻鎖定、成功清除與帳號鍵值正規化
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: LoginAttemptLimiter
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptLimiterTest {

    /**
     * 測試達門檻後鎖定
     * 驗證門檻 3 次時，前 2 次失敗不鎖定，第 3 次失敗後鎖定
     */
    @Test
    @DisplayName("鎖定測試: 連續失敗達門檻後應鎖定")
    void recordFailure_ReachesThreshold_Locks() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, 15);
        limiter.recordFailure("testuser");
        limiter.recordFailure("testuser");
        assertDoesNotThrow(() -> limiter.ensureNotLocked("testuser"));

        limiter.recordFailure("testuser");
        assertThrows(LockedException.class, () -> limiter.ensureNotLocked("testuser"));
    }

    /**
     * 測試成功登入清除紀錄
     * 驗證失敗未達門檻時登入成功，失敗次數歸零重新計算
     */
    @Test
    @DisplayName("鎖定測試: 登入成功應清除失敗次數")
    void recordSuccess_ResetsFailures() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 15);
        limiter.recordFailure("testuser");
        limiter.recordSuccess("testuser");
        limiter.recordFailure("testuser");

        assertDoesNotThrow(() -> limiter.ensureNotLocked("testuser"));
    }

    /**
     * 測試帳號鍵值正規化
     * 驗證大小寫與前後空白不同之帳號視為同一帳號計數，無法以變換大小寫繞過鎖定
     */
    @Test
    @DisplayName("鎖定測試: 帳號大小寫與空白不同仍視為同一帳號")
    void recordFailure_NormalizesUsername() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 15);
        limiter.recordFailure("TestUser");
        limiter.recordFailure(" testuser ");

        assertThrows(LockedException.class, () -> limiter.ensureNotLocked("testuser"));
    }

    /**
     * 測試未設定任何失敗紀錄之帳號
     * 驗證全新帳號不會被判定鎖定
     */
    @Test
    @DisplayName("鎖定測試: 沒有失敗紀錄之帳號不會被鎖定")
    void ensureNotLocked_NoRecord_Passes() {
        assertDoesNotThrow(() -> new LoginAttemptLimiter(3, 15).ensureNotLocked("newuser"));
    }

    /**
     * 測試鎖定期滿後重新計數
     * 驗證鎖定時間為 0 分鐘時，鎖定立即解除，下一次失敗從 1 開始重新計算
     */
    @Test
    @DisplayName("鎖定測試: 鎖定期滿後失敗次數重新計算")
    void recordFailure_AfterLockExpires_RestartsCount() {
        // Given：門檻 2 次、鎖定 0 分鐘 (鎖定立即到期)
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 0);
        limiter.recordFailure("testuser");
        limiter.recordFailure("testuser");

        // When：鎖定已到期，再失敗一次
        limiter.recordFailure("testuser");

        // Then：重新計數為 1 次，未達門檻
        assertDoesNotThrow(() -> limiter.ensureNotLocked("testuser"));
    }

    /**
     * 測試定期清理
     * 驗證過期紀錄被清除後，帳號不再被鎖定
     */
    @Test
    @DisplayName("鎖定測試: 定期清理移除已過期之紀錄")
    void cleanupExpiredAttempts_RemovesStaleRecords() {
        // Given
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 0);
        limiter.recordFailure("testuser");

        // When
        limiter.cleanupExpiredAttempts();

        // Then
        assertDoesNotThrow(() -> limiter.ensureNotLocked("testuser"));
    }

    /**
     * 測試帳號為 null 之防禦
     * 驗證 null 帳號不拋出例外 (以空字串為鍵)
     */
    @Test
    @DisplayName("鎖定測試: 帳號為 null 時不拋出例外")
    void nullUsername_IsHandled() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 15);
        limiter.recordFailure(null);

        assertThrows(LockedException.class, () -> limiter.ensureNotLocked(null));
    }

    /**
     * 測試鎖定到期後可再登入
     * 驗證鎖定時間為 0 分鐘時，鎖定立即解除
     */
    @Test
    @DisplayName("鎖定測試: 鎖定到期後不再阻擋")
    void ensureNotLocked_AfterExpiry_Passes() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, 0);
        limiter.recordFailure("testuser");

        assertDoesNotThrow(() -> limiter.ensureNotLocked("testuser"));
    }

    /**
     * 測試多執行緒同時記錄失敗
     * 驗證 ConcurrentHashMap.compute 為原子操作：20 條執行緒同時失敗，計數不遺失 (門檻 20 時恰好鎖定)
     * @throws Exception 執行緒等待失敗
     */
    @Test
    @DisplayName("鎖定測試: 20 條執行緒同時記錄失敗，計數不遺失")
    void concurrentFailures_AreAllCounted() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(20, 15);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                limiter.recordFailure("testuser");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThrows(LockedException.class, () -> limiter.ensureNotLocked("testuser"));
    }
}
