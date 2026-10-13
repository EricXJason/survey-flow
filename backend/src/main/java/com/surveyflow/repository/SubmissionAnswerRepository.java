/**
 * ============================================================================
 * 檔案名稱: SubmissionAnswerRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 管理題目作答明細，支援單選/多選選項次數聚合、量表均分計算與文字答案抽樣
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: SubmissionAnswer, SurveyQuestion, SurveyOption
 *   - 下游消費: AnalyticsService, ExportService
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
     * 統計某題各選項之勾選次數 (單選/多選題適用)
     * @param questionId 題目 ID
     * @return 包含 option_id 與 count 之聚合清單
     */
    @Query("SELECT a.option.id AS optionId, COUNT(a.id) AS selectedCount " +
           "FROM SubmissionAnswer a WHERE a.question.id = :questionId AND a.option IS NOT NULL " +
           "GROUP BY a.option.id")
    List<Map<String, Object>> countSelectionsByQuestion(@Param("questionId") Long questionId);

    /**
     * 計算評分量表題之平均分與總評分次數
     * @param questionId 題目 ID
     * @return 包含 avgRating, minRating, maxRating, totalCount 之聚合數據
     */
    @Query("SELECT AVG(a.ratingValue) AS avgRating, COUNT(a.id) AS totalCount " +
           "FROM SubmissionAnswer a WHERE a.question.id = :questionId AND a.ratingValue IS NOT NULL")
    Map<String, Object> calculateRatingStats(@Param("questionId") Long questionId);

    /**
     * 查詢某開放式文字題之所有有效文字反饋清單
     * @param questionId 題目 ID
     * @return 文字回答清單
     */
    @Query("SELECT a.textAnswer FROM SubmissionAnswer a " +
           "WHERE a.question.id = :questionId AND a.textAnswer IS NOT NULL AND TRIM(a.textAnswer) <> ''")
    List<String> findTextAnswersByQuestion(@Param("questionId") Long questionId);
}
