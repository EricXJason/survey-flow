/**
 * ============================================================================
 * 檔案名稱: SurveyRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 提供問卷主表之條件篩選、狀態過濾、大廳公開查詢與分頁檢索
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: Survey, User, Spring Data JPA
 *   - 下游消費: SurveyService, LobbyService, ManagementService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.common.enums.SurveyStatus;
import com.surveyflow.entity.Survey;
import com.surveyflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {

    /**
     * 查詢所有未被軟刪除且處於特定狀態之問卷 (大廳查詢)
     * @param status 問卷狀態 (例如 PUBLISHED)
     * @param pageable 分頁參數
     * @return 分頁結果
     */
    Page<Survey> findByStatusAndDeletedAtIsNull(SurveyStatus status, Pageable pageable);

    /**
     * 依分類與關鍵字搜尋公開中之問卷
     * @param category 分類 (若為 null 則不限)
     * @param keyword 關鍵字 (標題或說明)
     * @param pageable 分頁參數
     * @return 符合條件之分頁列表
     */
    @Query("SELECT s FROM Survey s WHERE s.status = 'PUBLISHED' AND s.deletedAt IS NULL " +
           "AND (:category IS NULL OR s.category = :category) " +
           "AND (:keyword IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Survey> searchPublishedSurveys(@Param("category") String category,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

    /**
     * 查詢特定使用者建立之所有問卷 (管理後台使用)
     * @param creator 建立者
     * @param pageable 分頁參數
     * @return 該使用者的問卷分頁列表
     */
    Page<Survey> findByCreatorAndDeletedAtIsNull(User creator, Pageable pageable);

    /**
     * 依 ID 查詢且排除軟刪除之單一問卷
     * @param id 問卷 ID
     * @return 匹配之 Survey Optional
     */
    Optional<Survey> findByIdAndDeletedAtIsNull(Long id);
}
