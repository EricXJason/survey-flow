/**
 * ============================================================================
 * 檔案名稱: ConcurrencyIT.java
 * 測試對象: 高併發情境下之資料一致性 (樂觀鎖、悲觀鎖、原子更新、唯一索引)
 * 測試方式: 整合測試；以多條執行緒「同時」呼叫 Service，對真實 MySQL 驗證結果。
 *           CountDownLatch 讓所有執行緒準備好後同一瞬間出發，盡可能製造衝突。
 *
 * 四種併發問題與本專案之對策 (學習對照表)：
 *   1. 兩人同時編輯同一份問卷 → 樂觀鎖 @Version：只有一人成功，其餘 409，不會互相覆蓋
 *   2. 同一組驗證碼被同時送出 → 悲觀鎖 SELECT ... FOR UPDATE：驗證碼只能被使用一次
 *   3. 大量受訪者同時瀏覽 → 原子 UPDATE view_count = view_count + 1：一次都不會少算
 *   4. 同一帳號同時註冊 → 資料庫 UNIQUE KEY：最後只會有一筆
 * ============================================================================
 */

package org.jasonproduction.surveyflow.concurrency;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.common.enums.VerificationTokenType;
import org.jasonproduction.surveyflow.dto.auth.RegisterRequest;
import org.jasonproduction.surveyflow.dto.auth.ResetPasswordRequest;
import org.jasonproduction.surveyflow.dto.survey.SurveySettingsRequest;
import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.entity.VerificationToken;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.jasonproduction.surveyflow.integration.TestUserFactory;
import org.jasonproduction.surveyflow.repository.VerificationTokenRepository;
import org.jasonproduction.surveyflow.service.AuthService;
import org.jasonproduction.surveyflow.service.SurveyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyIT extends IntegrationTestSupport {

    private final SurveyService surveyService;
    private final AuthService authService;
    private final VerificationTokenRepository tokenRepository;
    private final TestUserFactory factory;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 建構子注入
     * @param surveyService 問卷服務 (樂觀鎖、瀏覽數)
     * @param authService 認證服務 (悲觀鎖、註冊)
     * @param tokenRepository 建立驗證碼
     * @param factory 建立已提交之會員與問卷
     * @param jdbcTemplate 直接讀取資料表確認最終結果
     */
    ConcurrencyIT(SurveyService surveyService, AuthService authService, VerificationTokenRepository tokenRepository,
                  TestUserFactory factory, JdbcTemplate jdbcTemplate) {
        this.surveyService = surveyService;
        this.authService = authService;
        this.tokenRepository = tokenRepository;
        this.factory = factory;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 同時執行多個相同任務，回傳每個任務之結果 (成功為 null，失敗為例外)
     * @param threads 執行緒數量
     * @param task 要同時執行之任務
     * @return 各任務拋出之例外 (成功者為 null)
     * @throws Exception 等待逾時或被中斷
     */
    private List<Throwable> runConcurrently(int threads, Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.call();
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            }));
        }
        ready.await();
        start.countDown();
        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    /**
     * 計算成功次數
     * @param results runConcurrently 之結果
     * @return 成功 (無例外) 之次數
     */
    private long successCount(List<Throwable> results) {
        return results.stream().filter(result -> result == null).count();
    }

    @Test
    @DisplayName("樂觀鎖：5 人同時以版本 0 修改同一份問卷 → 只有 1 人成功，其餘皆因版本衝突失敗，最終版本為 1")
    void optimisticLock_OnlyOneEditorWins() throws Exception {
        // Given
        User owner = factory.createUser(uniqueName("it_opt"), RoleType.ROLE_USER);
        Survey survey = factory.createOpenSurvey(owner);
        SurveySettingsRequest request = SurveySettingsRequest.builder()
                .isOpen(false).isPublicToLobby(false).version(0L).build();

        // When
        List<Throwable> results = runConcurrently(5,
                () -> surveyService.replaceSurveySettings(survey.getId(), request, owner.getUsername()));

        // Then
        assertEquals(1, successCount(results));
        assertTrue(results.stream().filter(result -> result != null).allMatch(result ->
                result instanceof OptimisticLockException || result instanceof ObjectOptimisticLockingFailureException));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT version FROM surveys WHERE id = ?", Long.class, survey.getId()));
    }

    @Test
    @DisplayName("悲觀鎖：同一組重設密碼驗證碼被 8 個請求同時送出 → 只有 1 次成功，驗證碼不會被重複使用")
    void pessimisticLock_CodeUsedOnce() throws Exception {
        // Given
        User user = factory.createUser(uniqueName("it_pess"), RoleType.ROLE_USER);
        tokenRepository.save(VerificationToken.builder().email(user.getEmail()).code("654321")
                .tokenType(VerificationTokenType.FORGOT_PASSWORD).expiresAt(LocalDateTime.now().plusMinutes(15)).isUsed(false).build());
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email(user.getEmail()).code("654321").newPassword("NewPassword1!").build();

        // When
        List<Throwable> results = runConcurrently(8, () -> {
            authService.processResetPassword(request);
            return null;
        });

        // Then
        assertEquals(1, successCount(results));
        assertTrue(results.stream().filter(result -> result != null).allMatch(result -> result instanceof IllegalArgumentException));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verification_tokens WHERE email = ? AND is_used = 1", Integer.class, user.getEmail()));
    }

    @Test
    @DisplayName("原子更新：50 位受訪者同時開啟問卷 → 瀏覽數剛好 +50，一次都沒有遺失")
    void atomicViewCount_NoLostUpdates() throws Exception {
        // Given
        User owner = factory.createUser(uniqueName("it_view"), RoleType.ROLE_USER);
        Survey survey = factory.createOpenSurvey(owner);

        // When
        List<Throwable> results = runConcurrently(50, () -> surveyService.getPublicSurvey(survey.getId(), false, null));

        // Then
        assertEquals(50, successCount(results));
        assertEquals(50, jdbcTemplate.queryForObject("SELECT view_count FROM surveys WHERE id = ?", Integer.class, survey.getId()));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT version FROM surveys WHERE id = ?", Long.class, survey.getId()),
                "瀏覽不應觸發樂觀鎖版本號");
    }

    @Test
    @DisplayName("唯一索引：同一帳號被 6 個請求同時註冊 → 資料庫最後只有 1 筆，失敗者為重複或唯一索引衝突")
    void concurrentRegistration_SingleRow() throws Exception {
        // Given
        String username = uniqueName("it_reg");
        RegisterRequest request = RegisterRequest.builder().username(username).email(username + "@it.test")
                .password("Password1!").fullName("併發註冊").build();

        // When
        List<Throwable> results = runConcurrently(6, () -> authService.register(request));

        // Then
        assertTrue(successCount(results) >= 1);
        assertTrue(results.stream().filter(result -> result != null).allMatch(result ->
                result instanceof IllegalArgumentException || result instanceof DataIntegrityViolationException));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username));
        assertEquals(UserStatus.PENDING_VERIFICATION.name(),
                jdbcTemplate.queryForObject("SELECT status FROM users WHERE username = ?", String.class, username));
    }
}
