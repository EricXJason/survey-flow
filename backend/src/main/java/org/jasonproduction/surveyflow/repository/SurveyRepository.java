/**
 * ============================================================================
 * 檔案名稱: SurveyRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 提供問卷主表之條件篩選、大廳公開檢索與分頁，全面採用原生 SQL (Native Query)
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: Survey, User, Spring Data JPA
 *   - 下游消費: SurveyService
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.Survey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {

    /**
     * 原生 SQL 大廳多條件模糊搜尋公開問卷 (嚴格過濾未開始或已過期截止之問卷)
     * @param category 分類 (若為 null 則不限)
     * @param keyword 關鍵字 (標題或說明)
     * @param now 臺灣當前時間 (Asia/Taipei)
     * @param pageable 分頁參數
     * @return 符合條件之分頁列表
     */
    /**
     * 原生 SQL 問卷大廳多條件分頁檢索：僅列出開放中、公開於大廳、未刪除且當下位於起訖時窗內之問卷
     * @param category 分類篩選，null 或空字串表示不篩選
     * @param keyword 標題 / 說明模糊關鍵字，null 或空字串表示不篩選
     * @param now 判定時窗之當前時間
     * @param pageable 分頁與排序參數
     * @return 符合條件之問卷分頁
     */
    @Query(value = "SELECT * FROM surveys s WHERE s.is_open = 1 AND s.is_public_to_lobby = 1 AND s.deleted_at IS NULL " +
                   "AND (s.start_date IS NULL OR s.start_date <= :now) " +
                   "AND (s.end_date IS NULL OR s.end_date >= :now) " +
                   "AND (:category IS NULL OR :category = '' OR s.category = :category) " +
                   "AND (:keyword IS NULL OR :keyword = '' OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "     OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(*) FROM surveys s WHERE s.is_open = 1 AND s.is_public_to_lobby = 1 AND s.deleted_at IS NULL " +
                        "AND (s.start_date IS NULL OR s.start_date <= :now) " +
                        "AND (s.end_date IS NULL OR s.end_date >= :now) " +
                        "AND (:category IS NULL OR :category = '' OR s.category = :category) " +
                        "AND (:keyword IS NULL OR :keyword = '' OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "     OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           nativeQuery = true)
    Page<Survey> searchPublishedSurveysNative(@Param("category") String category,
                                              @Param("keyword") String keyword,
                                              @Param("now") LocalDateTime now,
                                              Pageable pageable);

    /**
     * 原生 SQL 查詢特定使用者建立之所有問卷 (排除軟刪除)
     * @param userId 建立者 ID
     * @param pageable 分頁參數
     * @return 該使用者的問卷分頁列表
     */
    @Query(value = "SELECT * FROM surveys WHERE user_id = :userId AND deleted_at IS NULL",
           countQuery = "SELECT COUNT(*) FROM surveys WHERE user_id = :userId AND deleted_at IS NULL",
           nativeQuery = true)
    Page<Survey> findByUserIdAndDeletedAtIsNullNative(@Param("userId") Long userId, Pageable pageable);

    /**
     * 原生 SQL 依 ID 查詢且排除軟刪除之單一問卷
     * @param id 問卷 ID
     * @return 匹配之 Survey Optional
     */
    @Query(value = "SELECT * FROM surveys WHERE id = :id AND deleted_at IS NULL LIMIT 1", nativeQuery = true)
    Optional<Survey> findByIdAndDeletedAtIsNullNative(@Param("id") Long id);

    /**
     * 原生 SQL 依分享代碼查詢未軟刪除之問卷 (受訪者由專屬填答連結進入時使用)
     * @param shareToken 連結中的分享代碼 (區分大小寫，欄位為 ascii_bin)
     * @return 匹配之 Survey Optional；代碼不存在或問卷已刪除時為空
     */
    @Query(value = "SELECT * FROM surveys WHERE share_token = :shareToken AND deleted_at IS NULL LIMIT 1", nativeQuery = true)
    Optional<Survey> findByShareTokenAndDeletedAtIsNullNative(@Param("shareToken") String shareToken);

    /**
     * 原生 SQL 管理者查詢全站所有問卷 (包含公開與非公開/私人專案，排除已軟刪除，預設依建立時間倒序)
     * @param pageable 分頁參數
     * @return 全站所有問卷分頁列表
     */
    @Query(value = "SELECT * FROM surveys WHERE deleted_at IS NULL ORDER BY created_at DESC",
           countQuery = "SELECT COUNT(*) FROM surveys WHERE deleted_at IS NULL",
           nativeQuery = true)
    Page<Survey> findAllSurveysForAdminNative(Pageable pageable);

    /**
     * 原生 SQL 查詢特定使用者所有未刪除問卷 ID 清單 (依建立時間由舊至新升冪排序，供計算該使用者個人問卷序號)
     * @param userId 建立者 ID
     * @return 問卷 ID 升冪清單
     */
    @Query(value = "SELECT id FROM surveys WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at ASC, id ASC", nativeQuery = true)
    List<Long> findSurveyIdsByUserIdOrderByCreatedAtAscNative(@Param("userId") Long userId);

    /**
     * 原生 SQL 統計全站未刪除問卷總數 (維運概覽專用)
     * @return 全站有效問卷總數
     */
    @Query(value = "SELECT COUNT(*) FROM surveys WHERE deleted_at IS NULL", nativeQuery = true)
    long countActiveSurveysNative();

    /**
     * 原子遞增瀏覽次數 (高併發安全)
     * [Why]: 若先讀出 viewCount 再 +1 存回，兩個同時進來的請求都會讀到 10 並寫回 11 (更新遺失)；
     *        交給資料庫在單一 UPDATE 內完成「讀取 + 加一 + 寫回」，不需要任何鎖也不會遺失。
     *        updated_at = updated_at 用來抵銷 ON UPDATE CURRENT_TIMESTAMP，瀏覽不應改變「最後修改時間」；
     *        此語句也不經過 @Version，瀏覽行為不會讓編輯者遇到樂觀鎖衝突。
     *        clearAutomatically 清除持久化內容，之後再查詢才會讀到最新的瀏覽數。
     * @param id 問卷 ID
     * @return 受影響筆數 (問卷不存在或已刪除時為 0)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE surveys SET view_count = view_count + 1, updated_at = updated_at WHERE id = :id AND deleted_at IS NULL",
           nativeQuery = true)
    int incrementViewCountNative(@Param("id") Long id);
}
