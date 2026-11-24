/**
 * ============================================================================
 * 檔案名稱: SurveyRepositoryIT.java
 * 測試對象: SurveyRepository 之原生 SQL (大廳條件查詢、個人清單、軟刪除、原子遞增瀏覽數、樂觀鎖版本號)
 * 測試方式: 整合測試 + @Transactional 回滾；大廳查詢以固定之「現在時間」參數執行，結果不受實際日期影響
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class SurveyRepositoryIT extends IntegrationTestSupport {

    /** 種子問卷之有效期間為 2026 全年；以期間內之固定時間查詢 */
    private static final LocalDateTime IN_SEED_WINDOW = LocalDateTime.of(2026, 6, 1, 12, 0);

    private final SurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 建構子注入
     * @param surveyRepository 受測 Repository
     * @param userRepository 取得種子會員
     * @param jdbcTemplate 直接讀取資料表欄位 (驗證 updated_at、version 等)
     */
    SurveyRepositoryIT(SurveyRepository surveyRepository, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.surveyRepository = surveyRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 執行大廳查詢並取出問卷 ID
     * @param category 分類
     * @param keyword 關鍵字
     * @param now 判定時窗之時間
     * @return 問卷 ID 清單
     */
    private List<Long> lobbyIds(String category, String keyword, LocalDateTime now) {
        return surveyRepository.searchPublishedSurveysNative(category, keyword, now, PageRequest.of(0, 500))
                .getContent().stream().map(Survey::getId).toList();
    }

    @Test
    @DisplayName("大廳：只列出公開且開放之問卷 (種子第 4 份為私人且關閉，不得出現)")
    void lobby_ExcludesPrivateOrClosed() {
        List<Long> ids = lobbyIds(null, null, IN_SEED_WINDOW);

        assertTrue(ids.contains(1L));
        assertFalse(ids.contains(4L));
        assertTrue(ids.containsAll(List.of(5L, 24L)));
    }

    @Test
    @DisplayName("大廳：開始時間之前 (2025 年) 種子問卷全部不顯示")
    void lobby_ExcludesSurveysBeforeStart() {
        List<Long> ids = lobbyIds(null, null, LocalDateTime.of(2025, 6, 1, 0, 0));

        assertTrue(ids.stream().noneMatch(id -> id <= 27));
    }

    @Test
    @DisplayName("大廳截止時間：問卷 26 已截止；2/3 與偶數號問卷顯示到 2027 年底；其餘不設截止 (end_date NULL) 者多年後仍顯示")
    void lobby_EndDatesSpanNextYear() {
        assertTrue(lobbyIds(null, null, IN_SEED_WINDOW).contains(26L), "截止前應可見");

        List<Long> endOf2027 = lobbyIds(null, null, LocalDateTime.of(2027, 12, 31, 12, 0));
        assertFalse(endOf2027.contains(26L));
        assertTrue(endOf2027.containsAll(List.of(1L, 2L, 3L, 5L, 6L, 23L, 24L)), "2027 年底前全部大廳問卷都還在");

        List<Long> later = lobbyIds(null, null, LocalDateTime.of(2030, 1, 1, 0, 0));
        assertTrue(later.containsAll(List.of(1L, 5L, 7L, 23L)), "不設截止者永久顯示");
        assertTrue(later.stream().noneMatch(id -> List.of(2L, 3L, 6L, 24L).contains(id)), "2027 年底截止者不再顯示");
    }

    @Test
    @DisplayName("分享代碼查詢：每份種子問卷都有代碼；比對區分大小寫 (ascii_bin)，不存在之代碼回空")
    void findByShareToken_CaseSensitive() {
        assertEquals(27L, surveyRepository.findByShareTokenAndDeletedAtIsNullNative("LPOQP2Z38hvCM9OFiMM84Q").orElseThrow().getId());
        assertTrue(surveyRepository.findByShareTokenAndDeletedAtIsNullNative("lpoqp2z38hvcm9ofimm84q").isEmpty());
        assertTrue(surveyRepository.findByShareTokenAndDeletedAtIsNullNative("not-a-token").isEmpty());
    }

    @Test
    @DisplayName("大廳：排程中之問卷 25 開始前不顯示；僅限連結之問卷 27 永遠不顯示")
    void lobby_ExcludesScheduledAndLinkOnly() {
        List<Long> ids = lobbyIds(null, null, LocalDateTime.now());

        assertFalse(ids.contains(25L));
        assertFalse(ids.contains(27L));
    }

    @Test
    @DisplayName("大廳：分類精確比對、關鍵字不分大小寫比對標題或說明；空字串視為不篩選")
    void lobby_CategoryAndKeywordFilters() {
        assertEquals(List.of(5L, 18L), lobbyIds("學術專題研究", "", IN_SEED_WINDOW).stream().filter(id -> id <= 24).sorted().toList());
        assertTrue(lobbyIds(null, "咖啡", IN_SEED_WINDOW).contains(24L));
        assertTrue(lobbyIds(null, "esg", IN_SEED_WINDOW).contains(12L), "關鍵字 esg 應比對到標題中之 ESG");
    }

    @Test
    @DisplayName("大廳分頁：countQuery 計算之總數與逐頁取回之筆數一致，且可依 created_at 排序")
    void lobby_PagingIsConsistent() {
        Page<Survey> first = surveyRepository.searchPublishedSurveysNative(null, null, IN_SEED_WINDOW,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "created_at")));

        assertEquals(5, first.getContent().size());
        assertEquals(lobbyIds(null, null, IN_SEED_WINDOW).size(), first.getTotalElements());
    }

    @Test
    @DisplayName("個人清單：testuser (id 2) 擁有種子問卷 1~4 與狀態示範問卷 25~27，依建立順序回傳 ID")
    void userSurveys_SeedOwner() {
        Long testUserId = userRepository.findByUsernameNative("testuser").orElseThrow().getId();

        assertEquals(7, surveyRepository.findByUserIdAndDeletedAtIsNullNative(testUserId, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(List.of(1L, 2L, 3L, 4L, 25L, 26L, 27L), surveyRepository.findSurveyIdsByUserIdOrderByCreatedAtAscNative(testUserId));
    }

    @Test
    @DisplayName("軟刪除：查詢單筆、個人清單與全站計數都不再包含該問卷")
    void softDelete_HidesSurvey() {
        // Given
        long before = surveyRepository.countActiveSurveysNative();
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(1L).orElseThrow();

        // When
        survey.softDelete(LocalDateTime.now());
        surveyRepository.saveAndFlush(survey);

        // Then
        assertTrue(surveyRepository.findByIdAndDeletedAtIsNullNative(1L).isEmpty());
        assertEquals(before - 1, surveyRepository.countActiveSurveysNative());
        assertFalse(surveyRepository.findAllSurveysForAdminNative(PageRequest.of(0, 500)).getContent().stream()
                .anyMatch(s -> s.getId() == 1L));
    }

    @Test
    @DisplayName("原子遞增瀏覽數：view_count +1，但 updated_at 與樂觀鎖 version 都不變")
    void incrementViewCount_DoesNotTouchUpdatedAtOrVersion() {
        // Given
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT view_count, updated_at, version FROM surveys WHERE id = 2");

        // When
        int affected = surveyRepository.incrementViewCountNative(2L);

        // Then
        Map<String, Object> after = jdbcTemplate.queryForMap("SELECT view_count, updated_at, version FROM surveys WHERE id = 2");
        assertEquals(1, affected);
        assertEquals(((Number) before.get("view_count")).intValue() + 1, ((Number) after.get("view_count")).intValue());
        assertEquals(before.get("updated_at"), after.get("updated_at"));
        assertEquals(before.get("version"), after.get("version"));
    }

    @Test
    @DisplayName("原子遞增：不存在或已刪除之問卷影響 0 筆")
    void incrementViewCount_MissingSurvey() {
        assertEquals(0, surveyRepository.incrementViewCountNative(999_999L));
    }

    @Test
    @DisplayName("樂觀鎖：每次經由 Entity 修改並寫入，version 自動 +1")
    void entityUpdate_IncrementsVersion() {
        // Given
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(3L).orElseThrow();
        long before = survey.getVersion();

        // When
        survey.updateContent("新標題", null, null, null);
        Survey saved = surveyRepository.saveAndFlush(survey);

        // Then
        assertEquals(before + 1, saved.getVersion());
    }

    @Test
    @DisplayName("新建問卷：version 由 JPA 初始化為 0")
    void newSurvey_StartsAtVersionZero() {
        User owner = userRepository.findByUsernameNative("academic_chen").orElseThrow();

        Survey saved = surveyRepository.saveAndFlush(Survey.builder().creator(owner).title("版本測試").build());

        assertEquals(0L, saved.getVersion());
    }
}
