/**
 * ============================================================================
 * 檔案名稱: SurveyTest.java
 * 測試對象: Survey 實體之業務方法 (擁有者判定、填答時窗檢核、內容與設定更新、瀏覽數、軟刪除、子物件綁定)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import jakarta.persistence.OptimisticLockException;
import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 13, 12, 0);

    /**
     * 建立開放中、無時窗限制之問卷
     * @return 問卷實體
     */
    private Survey openSurvey() {
        return Survey.builder()
                .id(1L)
                .creator(User.builder().id(7L).username("owner").build())
                .title("原標題")
                .isOpen(true)
                .isPublicToLobby(true)
                .build();
    }

    @Test
    @DisplayName("Builder 預設值：分類「綜合調查」、開放、公開、瀏覽數 0、題目清單為空、版本號未指派 (由 JPA 於新增時給 0)")
    void builderDefaults() {
        Survey survey = Survey.builder().title("t").build();

        assertEquals("綜合調查", survey.getCategory());
        assertTrue(survey.getIsOpen());
        assertTrue(survey.getIsPublicToLobby());
        assertEquals(0, survey.getViewCount());
        assertTrue(survey.getQuestions().isEmpty());
        assertNull(survey.getVersion());
    }

    @Test
    @DisplayName("assignShareTokenIfAbsent：未指派時產生 22 字元 Base64URL 代碼，每份不同；已有代碼者保留不變")
    void assignShareTokenIfAbsent() {
        Survey first = openSurvey();
        Survey second = openSurvey();
        first.assignShareTokenIfAbsent();
        second.assignShareTokenIfAbsent();

        assertTrue(first.getShareToken().matches("[A-Za-z0-9_-]{22}"));
        assertNotEquals(first.getShareToken(), second.getShareToken());

        String kept = first.getShareToken();
        first.assignShareTokenIfAbsent();
        assertEquals(kept, first.getShareToken());
    }

    @Nested
    @DisplayName("ensureReachableBy 存取檢核")
    class EnsureReachableBy {

        /**
         * 建立未公開於大廳、分享代碼為 SECRET 之問卷
         * @return 僅限連結存取之問卷
         */
        private Survey linkOnlySurvey() {
            return Survey.builder().title("t").isPublicToLobby(false).shareToken("SECRET").build();
        }

        @Test
        @DisplayName("公開於大廳：不帶代碼也可存取")
        void publicToLobby_AllowsWithoutToken() {
            assertDoesNotThrow(() -> openSurvey().ensureReachableBy(null));
        }

        @Test
        @DisplayName("僅限連結且代碼相符：允許")
        void linkOnly_MatchingToken_Allows() {
            assertDoesNotThrow(() -> linkOnlySurvey().ensureReachableBy("SECRET"));
        }

        @Test
        @DisplayName("僅限連結但未帶代碼、代碼不符或大小寫不同：一律回「問卷不存在或已下架」，不透露問卷存在")
        void linkOnly_MissingOrWrongToken_Throws() {
            for (String wrong : new String[] {null, "", "WRONG", "secret"}) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> linkOnlySurvey().ensureReachableBy(wrong));
                assertEquals("問卷不存在或已下架", ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("isOwnedBy：只有建立者 ID 相符才為 true")
    void isOwnedBy() {
        assertTrue(openSurvey().isOwnedBy(7L));
        assertFalse(openSurvey().isOwnedBy(8L));
        assertFalse(Survey.builder().build().isOwnedBy(7L), "沒有建立者時一律 false");
    }

    @Nested
    @DisplayName("ensureAcceptingSubmissions 填答時窗檢核")
    class EnsureAcceptingSubmissions {

        @Test
        @DisplayName("開放中且在時窗內：不拋出例外")
        void withinWindow_Passes() {
            Survey survey = openSurvey();
            survey.replaceSettings(now.minusDays(1), now.plusDays(1), true, true);

            assertDoesNotThrow(() -> survey.ensureAcceptingSubmissions(now));
        }

        @Test
        @DisplayName("起訖時間皆為 null (不限期)：不拋出例外")
        void noWindow_Passes() {
            assertDoesNotThrow(() -> openSurvey().ensureAcceptingSubmissions(now));
        }

        @Test
        @DisplayName("手動關閉：拋出例外並說明已關閉")
        void closed_Throws() {
            Survey survey = openSurvey();
            survey.patchSettings(null, null, false, null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> survey.ensureAcceptingSubmissions(now));
            assertTrue(ex.getMessage().contains("手動關閉"));
        }

        @Test
        @DisplayName("尚未開始：訊息包含開放時間 (yyyy/MM/dd HH:mm)")
        void notStarted_Throws() {
            Survey survey = openSurvey();
            survey.patchSettings(now.plusHours(1), null, null, null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> survey.ensureAcceptingSubmissions(now));
            assertTrue(ex.getMessage().contains("2026/11/13 13:00"));
        }

        @Test
        @DisplayName("已截止：訊息包含截止時間")
        void ended_Throws() {
            Survey survey = openSurvey();
            survey.patchSettings(null, now.minusHours(1), null, null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> survey.ensureAcceptingSubmissions(now));
            assertTrue(ex.getMessage().contains("2026/11/13 11:00"));
        }
    }

    @Test
    @DisplayName("updateContent：只覆寫非 null 欄位")
    void updateContent_PartialUpdate() {
        Survey survey = openSurvey();

        survey.updateContent("新標題", null, "cover.png", null);

        assertEquals("新標題", survey.getTitle());
        assertEquals("cover.png", survey.getCoverImageUrl());
        assertEquals("綜合調查", survey.getCategory());
    }

    @Test
    @DisplayName("replaceSettings (PUT)：起訖時間可清為 null，開關為 null 時視為 false")
    void replaceSettings_OverwritesEverything() {
        Survey survey = openSurvey();
        survey.patchSettings(now, now.plusDays(1), null, null);

        survey.replaceSettings(null, null, null, null);

        assertNull(survey.getStartDate());
        assertNull(survey.getEndDate());
        assertFalse(survey.getIsOpen());
        assertFalse(survey.getIsPublicToLobby());
    }

    @Test
    @DisplayName("patchSettings (PATCH)：只覆寫非 null 欄位，其餘保持原值")
    void patchSettings_KeepsOtherFields() {
        Survey survey = openSurvey();
        survey.patchSettings(now, now.plusDays(1), null, null);

        survey.patchSettings(null, null, null, false);

        assertEquals(now, survey.getStartDate());
        assertTrue(survey.getIsOpen());
        assertFalse(survey.getIsPublicToLobby());
    }

    @Test
    @DisplayName("ensureVersionMatches：版本相同或未提供版本時通過；版本不同時拋出 OptimisticLockException")
    void ensureVersionMatches() {
        Survey survey = openSurvey();
        ReflectionTestUtils.setField(survey, "version", 3L);

        assertDoesNotThrow(() -> survey.ensureVersionMatches(3L));
        assertDoesNotThrow(() -> survey.ensureVersionMatches(null));
        OptimisticLockException ex = assertThrows(OptimisticLockException.class, () -> survey.ensureVersionMatches(2L));
        assertTrue(ex.getMessage().contains("已被其他人修改"));
    }

    @Test
    @DisplayName("softDelete：寫入刪除時間")
    void softDelete() {
        Survey survey = openSurvey();

        survey.softDelete(now);

        assertEquals(now, survey.getDeletedAt());
    }

    @Test
    @DisplayName("assignBasicInfoConfig：同時建立雙向關聯 (設定 → 問卷)")
    void assignBasicInfoConfig_BindsBothSides() {
        Survey survey = openSurvey();
        SurveyBasicInfoConfig config = SurveyBasicInfoConfig.builder().build();

        survey.assignBasicInfoConfig(config);

        assertSame(config, survey.getBasicInfoConfig());
        assertSame(survey, config.getSurvey());
    }

    @Test
    @DisplayName("replaceQuestions / replaceOptions：以新清單取代舊清單 (orphanRemoval 才能刪除舊資料)")
    void replaceQuestionsAndOptions() {
        Survey survey = openSurvey();
        SurveyQuestion question = SurveyQuestion.builder().survey(survey).type(QuestionType.SINGLE).questionText("Q1").build();
        question.replaceOptions(List.of(SurveyOption.builder().question(question).optionText("A").build()));

        survey.replaceQuestions(List.of(question));
        survey.replaceQuestions(List.of(question));

        assertEquals(1, survey.getQuestions().size());
        assertEquals("A", survey.getQuestions().get(0).getOptions().get(0).getOptionText());
    }

    @Test
    @DisplayName("updateContent：四個欄位都有值時全部覆寫")
    void updateContent_AllFields() {
        Survey survey = openSurvey();

        survey.updateContent("T", "D", "C", "教育");

        assertEquals("D", survey.getDescription());
        assertEquals("教育", survey.getCategory());
    }
}
