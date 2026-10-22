/**
 * ============================================================================
 * 檔案名稱: SubmissionAnswerRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 管理題目作答明細，支援選項統計與平均分計算，全面採用原生 SQL (Native Query)
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: SubmissionAnswer, Spring Data JPA
 *   - 下游消費: AnalyticsService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {

    /**
     * 原生 SQL 統計某題各選項之勾選次數 (單選/多選題適用)
     * @param questionId 題目 ID
     * @return 包含 option_id 與 count 之聚合清單
     */
    @Query(value = "SELECT option_id AS optionId, COUNT(id) AS selectedCount " +
                   "FROM submission_answers WHERE question_id = :questionId AND option_id IS NOT NULL " +
                   "GROUP BY option_id", nativeQuery = true)
    List<Map<String, Object>> countSelectionsByQuestionNative(@Param("questionId") Long questionId);

    /**
     * 原生 SQL 計算評分量表題之平均分與總評分次數
     * @param questionId 題目 ID
     * @return 包含 avgRating 與 totalCount 之聚合數據
     */
    @Query(value = "SELECT AVG(rating_value) AS avgRating, COUNT(id) AS totalCount " +
                   "FROM submission_answers WHERE question_id = :questionId AND rating_value IS NOT NULL", nativeQuery = true)
    Map<String, Object> calculateRatingStatsNative(@Param("questionId") Long questionId);

    /**
     * 原生 SQL 統計評分量表題之各評分值的次數分佈 (例如 1分幾人、2分幾人...)
     * @param questionId 題目 ID
     * @return 包含 ratingValue 與 ratingCount 之聚合數據
     */
    @Query(value = "SELECT rating_value AS ratingValue, COUNT(id) AS ratingCount " +
                   "FROM submission_answers WHERE question_id = :questionId AND rating_value IS NOT NULL " +
                   "GROUP BY rating_value ORDER BY rating_value ASC", nativeQuery = true)
    List<Map<String, Object>> countRatingDistributionByQuestionNative(@Param("questionId") Long questionId);

    /**
     * 原生 SQL 查詢某開放式文字題之所有有效文字反饋清單 (舊相容)
     * @param questionId 題目 ID
     * @return 文字回答清單
     */
    @Query(value = "SELECT text_answer FROM submission_answers " +
                   "WHERE question_id = :questionId AND text_answer IS NOT NULL AND TRIM(text_answer) <> ''", nativeQuery = true)
    List<String> findTextAnswersByQuestionNative(@Param("questionId") Long questionId);

    /**
     * 原生 SQL 查詢開放式文字題之結構化文字反饋紀錄 (關聯 submissions 取出全量填答者，包含訪客與真實姓名)
     * @param questionId 題目 ID
     * @return 結構化映射清單 (包含 content, respondentName, submittedAt)
     */
    @Query(value = "SELECT " +
                   "  COALESCE(NULLIF(TRIM(sa.text_answer), ''), '（未填寫文字回覆）') AS content, " +
                   "  COALESCE(NULLIF(TRIM(sbi.name), ''), CONCAT('訪客 ', s.id)) AS respondentName, " +
                   "  DATE_FORMAT(s.submitted_at, '%Y-%m-%d %H:%i') AS submittedAt " +
                   "FROM submissions s " +
                   "LEFT JOIN submission_answers sa ON sa.submission_id = s.id AND sa.question_id = :questionId " +
                   "LEFT JOIN submission_basic_infos sbi ON sbi.submission_id = s.id " +
                   "WHERE s.survey_id = (SELECT survey_id FROM survey_questions WHERE id = :questionId) " +
                   "ORDER BY s.submitted_at ASC, s.id ASC", nativeQuery = true)
    List<Map<String, Object>> findStructuredTextAnswersByQuestionNative(@Param("questionId") Long questionId);

    /**
     * 原生 SQL 查詢特定選項被勾選之總次數
     * @param optionId 選項 ID
     * @return 勾選次數
     */
    @Query(value = "SELECT COUNT(id) FROM submission_answers WHERE option_id = :optionId", nativeQuery = true)
    Long countSelectionsByOptionIdNative(@Param("optionId") Long optionId);
}
