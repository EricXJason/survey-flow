/**
 * ============================================================================
 * 檔案名稱: SubmissionRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 管理填答主表記錄，提供填答回收數統計、作答時間查詢，全面採用原生 SQL (Native Query)
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: Submission, Spring Data JPA
 *   - 下游消費: SubmissionService, AnalyticsService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.Submission;
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
     * 原生 SQL 統計特定問卷之填答回收總數
     * @param surveyId 所屬問卷 ID
     * @return 回收總數
     */
    @Query(value = "SELECT COUNT(*) FROM submissions WHERE survey_id = :surveyId", nativeQuery = true)
    long countBySurveyIdNative(@Param("surveyId") Long surveyId);

    /**
     * 原生 SQL 依問卷分頁查詢所有填答主記錄
     * @param surveyId 所屬問卷 ID
     * @param pageable 分頁參數
     * @return 填答紀錄分頁
     */
    @Query(value = "SELECT * FROM submissions WHERE survey_id = :surveyId ORDER BY submitted_at DESC",
           countQuery = "SELECT COUNT(*) FROM submissions WHERE survey_id = :surveyId",
           nativeQuery = true)
    Page<Submission> findBySurveyIdOrderBySubmittedAtDescNative(@Param("surveyId") Long surveyId, Pageable pageable);

    /**
     * 原生 SQL 查詢特定問卷之所有填答紀錄 (供統計匯出使用)
     * @param surveyId 所屬問卷 ID
     * @return 所有填答主表記錄
     */
    @Query(value = "SELECT * FROM submissions WHERE survey_id = :surveyId ORDER BY submitted_at ASC", nativeQuery = true)
    List<Submission> findBySurveyIdNative(@Param("surveyId") Long surveyId);

    /**
     * 原生 SQL 計算特定問卷填答之平均作答秒數
     * @param surveyId 問卷 ID
     * @return 平均秒數 (Double)
     */
    @Query(value = "SELECT AVG(duration_seconds) FROM submissions WHERE survey_id = :surveyId", nativeQuery = true)
    Double getAverageDurationSecondsNative(@Param("surveyId") Long surveyId);

    /**
     * 原生 SQL 統計全站總填答數 (管理員總覽使用)
     * @return 全站總填答次數
     */
    @Query(value = "SELECT COUNT(*) FROM submissions", nativeQuery = true)
    long countTotalSubmissionsNative();
}
