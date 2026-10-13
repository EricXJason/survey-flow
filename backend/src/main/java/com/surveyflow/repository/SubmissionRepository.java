/**
 * ============================================================================
 * 檔案名稱: SubmissionRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 管理填答主表記錄，提供填答回收數統計、作答時間範圍查詢
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: Submission, Survey, Spring Data JPA
 *   - 下游消費: SubmissionService, AnalyticsService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.Submission;
import com.surveyflow.entity.Survey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * 統計特定問卷之填答回收總數
     * @param survey 所屬問卷
     * @return 回收總數
     */
    long countBySurvey(Survey survey);

    /**
     * 依問卷分頁查詢所有填答主記錄
     * @param survey 所屬問卷
     * @param pageable 分頁參數
     * @return 填答紀錄分頁
     */
    Page<Submission> findBySurveyOrderBySubmittedAtDesc(Survey survey, Pageable pageable);

    /**
     * 查詢特定問卷之所有填答紀錄 (供統計匯出使用)
     * @param survey 所屬問卷
     * @return 所有填答主表記錄
     */
    List<Submission> findBySurvey(Survey survey);

    /**
     * 計算特定問卷填答之平均作答秒數
     * @param surveyId 問卷 ID
     * @return 平均秒數 (Double)
     */
    @Query("SELECT AVG(s.durationSeconds) FROM Submission s WHERE s.survey.id = :surveyId")
    Double getAverageDurationSeconds(@Param("surveyId") Long surveyId);
}
