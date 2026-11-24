/**
 * ============================================================================
 * 檔案名稱: SubmissionRepositoryIT.java
 * 測試對象: SubmissionRepository 與 SubmissionAnswerRepository 之統計原生 SQL
 * 測試方式: 整合測試 + @Transactional；以種子問卷 1 (10 筆填答) 驗證，並以 JdbcTemplate 取題目 ID 與比對值
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.Submission;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class SubmissionRepositoryIT extends IntegrationTestSupport {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 建構子注入
     * @param submissionRepository 填答主檔 Repository
     * @param answerRepository 作答明細 Repository
     * @param jdbcTemplate 取得題目 ID 與比對值
     */
    SubmissionRepositoryIT(SubmissionRepository submissionRepository, SubmissionAnswerRepository answerRepository,
                           JdbcTemplate jdbcTemplate) {
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 取得問卷 1 中指定題型之第一題 ID
     * @param type 題型名稱
     * @return 題目 ID
     */
    private Long questionIdOf(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM survey_questions WHERE survey_id = 1 AND type = ? ORDER BY sort_order LIMIT 1", Long.class, type);
    }

    @Test
    @DisplayName("回收數與填答清單：問卷 1 有 10 筆，清單依送出時間由舊到新")
    void countAndList() {
        List<Submission> submissions = submissionRepository.findBySurveyIdNative(1L);
        List<LocalDateTime> times = submissions.stream().map(Submission::getSubmittedAt).toList();

        assertEquals(10L, submissionRepository.countBySurveyIdNative(1L));
        assertEquals(10, submissions.size());
        assertEquals(times.stream().sorted().toList(), times);
    }

    @Test
    @DisplayName("平均作答秒數與全站總填答數：可正確計算 (全站至少包含種子之 100 筆)")
    void averageDurationAndTotal() {
        assertNotNull(submissionRepository.getAverageDurationSecondsNative(1L));
        assertTrue(submissionRepository.countTotalSubmissionsNative() >= 100);
    }

    @Test
    @DisplayName("單選題統計：各選項票數加總 = 該題綁定選項之作答數；單一選項計數與分組結果一致")
    void choiceCounts_AreConsistent() {
        // Given
        Long questionId = questionIdOf("SINGLE");
        Long expectedTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_answers WHERE question_id = ? AND option_id IS NOT NULL", Long.class, questionId);

        // When
        List<Map<String, Object>> rows = answerRepository.countSelectionsByQuestionNative(questionId);

        // Then：別名 optionId / selectedCount 可被讀取
        long sum = rows.stream().mapToLong(row -> ((Number) row.get("selectedCount")).longValue()).sum();
        assertEquals(expectedTotal, sum);
        Map<String, Object> firstRow = rows.get(0);
        Long optionId = ((Number) firstRow.get("optionId")).longValue();
        assertEquals(((Number) firstRow.get("selectedCount")).longValue(), answerRepository.countSelectionsByOptionIdNative(optionId));
    }

    @Test
    @DisplayName("評分題統計：totalCount = 分佈加總；平均分介於最小與最大分數之間")
    void ratingStats_AreConsistent() {
        // Given
        Long questionId = questionIdOf("RATING");

        // When
        Map<String, Object> stats = answerRepository.calculateRatingStatsNative(questionId);
        List<Map<String, Object>> distribution = answerRepository.countRatingDistributionByQuestionNative(questionId);

        // Then
        long total = ((Number) stats.get("totalCount")).longValue();
        double average = ((Number) stats.get("avgRating")).doubleValue();
        assertEquals(total, distribution.stream().mapToLong(row -> ((Number) row.get("ratingCount")).longValue()).sum());
        int min = ((Number) distribution.get(0).get("ratingValue")).intValue();
        int max = ((Number) distribution.get(distribution.size() - 1).get("ratingValue")).intValue();
        assertTrue(average >= min && average <= max);
    }

    @Test
    @DisplayName("文字題：純文字清單排除空白作答；結構化清單每份填答一列 (含受訪者名稱與時間)")
    void textAnswers() {
        // Given
        Long questionId = questionIdOf("TEXT");

        // When
        List<String> texts = answerRepository.findTextAnswersByQuestionNative(questionId);
        List<Map<String, Object>> structured = answerRepository.findStructuredTextAnswersByQuestionNative(questionId);

        // Then
        assertFalse(texts.isEmpty());
        assertTrue(texts.stream().noneMatch(String::isBlank));
        assertEquals(10, structured.size(), "LEFT JOIN：問卷 1 之 10 份填答各一列 (未作答者顯示預設文字)");
        assertNotNull(structured.get(0).get("respondentName"));
        assertTrue(String.valueOf(structured.get(0).get("submittedAt")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }
}
