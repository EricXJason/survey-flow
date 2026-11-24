/**
 * ============================================================================
 * 檔案名稱: AnalyticsServiceTest.java
 * 測試對象: AnalyticsService (報表權限、KPI、近 7 日趨勢、受訪者輪廓、各題型統計、跳題條件、全站總覽)
 * 測試方式: JUnit 5 + Mockito；Repository 回傳之原生 SQL 結果以 Map 模擬 (欄位名稱大小寫不一亦須相容)。
 *           數字皆可手算驗證，測試內以註解寫出計算過程，方便對照統計公式學習。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.dto.analytics.DemographicsDto;
import org.jasonproduction.surveyflow.dto.analytics.QuestionStatDto;
import org.jasonproduction.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import org.jasonproduction.surveyflow.entity.Submission;
import org.jasonproduction.surveyflow.entity.SubmissionBasicInfo;
import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.SurveyOption;
import org.jasonproduction.surveyflow.entity.SurveyQuestion;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.repository.SubmissionAnswerRepository;
import org.jasonproduction.surveyflow.repository.SubmissionRepository;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// [Why]: 每個測試只驗證報表的一部分，但 getSurveyAnalytics 會一次呼叫所有 Repository；
//        共用 Stub 寫在 setUp，未被個別測試用到時不視為錯誤
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock private SurveyRepository surveyRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionAnswerRepository answerRepository;
    @Mock private UserRepository userRepository;

    private AnalyticsService analyticsService;
    private User owner;
    private Survey survey;
    private final LocalDateTime now = LocalDateTime.now();

    /**
     * 建立受測物件、問卷建立者、管理員、無關會員，以及一份瀏覽數 8 之空白問卷 (id 1)
     */
    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(surveyRepository, submissionRepository, answerRepository, userRepository);
        owner = User.builder().id(1L).username("owner").role(RoleType.ROLE_USER).build();
        survey = Survey.builder().id(1L).creator(owner).title("報表測試").category("教育").viewCount(8).build();

        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(survey));
        when(userRepository.findByUsernameNative("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findByUsernameNative("admin")).thenReturn(Optional.of(
                User.builder().id(9L).username("admin").role(RoleType.ROLE_ADMIN).build()));
        when(userRepository.findByUsernameNative("other")).thenReturn(Optional.of(
                User.builder().id(2L).username("other").role(RoleType.ROLE_USER).build()));
    }

    /**
     * 建立填答主記錄
     * @param submittedAt 送出時間
     * @param info 受訪者基本資料，可為 null
     * @return 填答實體
     */
    private Submission submission(LocalDateTime submittedAt, SubmissionBasicInfo info) {
        Submission submission = Submission.builder().submittedAt(submittedAt).build();
        if (info != null) {
            submission.assignBasicInfo(info);
        }
        return submission;
    }

    /**
     * 建立題目並綁定選項
     * @param id 題目 ID
     * @param code 題目代碼
     * @param type 題型
     * @param options 選項
     * @return 題目實體
     */
    private SurveyQuestion question(Long id, String code, QuestionType type, SurveyOption... options) {
        SurveyQuestion question = SurveyQuestion.builder().id(id).questionCode(code).type(type).questionText("題目" + code).build();
        question.replaceOptions(new ArrayList<>(Arrays.asList(options)));
        return question;
    }

    /**
     * 建立選項
     * @param id 選項 ID
     * @param text 選項文字
     * @param sortOrder 排序
     * @return 選項實體
     */
    private SurveyOption option(Long id, String text, int sortOrder) {
        return SurveyOption.builder().id(id).optionText(text).sortOrder(sortOrder).build();
    }

    /**
     * 模擬問卷共有 n 筆填答 (皆為今天送出、無基本資料)
     * @param n 填答筆數
     */
    private void givenSubmissions(int n) {
        List<Submission> submissions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            submissions.add(submission(now, null));
        }
        when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(submissions);
    }

    /**
     * 產生一份報表並取出第一題之統計
     * @return 第一題統計
     */
    private QuestionStatDto firstQuestionStat() {
        return analyticsService.getSurveyAnalytics(1L, "owner").getQuestionStats().get(0);
    }

    @Nested
    @DisplayName("權限核對")
    class Permission {

        @Test
        @DisplayName("建立者與管理員可查看報表")
        void ownerAndAdmin_Allowed() {
            assertNotNull(analyticsService.getSurveyAnalytics(1L, "owner"));
            assertNotNull(analyticsService.getSurveyAnalytics(1L, "admin"));
        }

        @Test
        @DisplayName("無關會員：拋出 AccessDeniedException")
        void otherUser_Denied() {
            assertThrows(AccessDeniedException.class, () -> analyticsService.getSurveyAnalytics(1L, "other"));
        }

        @Test
        @DisplayName("問卷或使用者不存在：拋出 IllegalArgumentException")
        void notFound_Throws() {
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(99L)).thenReturn(Optional.empty());
            when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> analyticsService.getSurveyAnalytics(99L, "owner"));
            assertThrows(IllegalArgumentException.class, () -> analyticsService.getSurveyAnalytics(1L, "ghost"));
        }
    }

    @Nested
    @DisplayName("KPI、趨勢與最新回覆時間")
    class KpiAndTrend {

        @Test
        @DisplayName("KPI：總數、平均秒數、完成率 (回收 / 瀏覽)、今日新增；沒有評分題時 NPS 為 null (不填假值)")
        void kpi_IsCalculatedFromRealData() {
            // Given：4 筆填答 (今天 2 筆、3 天前 1 筆、10 天前 1 筆)，瀏覽數 8
            when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(List.of(
                    submission(now, null), submission(now, null),
                    submission(now.minusDays(3), null), submission(now.minusDays(10), null)));
            when(submissionRepository.getAverageDurationSecondsNative(1L)).thenReturn(120.6);

            // When
            SurveyAnalyticsResponse report = analyticsService.getSurveyAnalytics(1L, "owner");

            // Then
            assertEquals(4L, report.getKpi().getTotalResponses());
            assertEquals(120, report.getKpi().getAverageDurationSeconds());
            assertEquals(50.0, report.getKpi().getCompletionRate(), "4 / 8 = 50%");
            assertEquals(2L, report.getKpi().getTodayResponses());
            assertNull(report.getKpi().getNetPromoterScore());
            assertEquals("owner", report.getAuthorUsername());
        }

        @Test
        @DisplayName("趨勢：固定 7 天、最後一天為今天；7 天前以外之填答不列入")
        void trend_CoversLastSevenDays() {
            // Given
            when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(List.of(
                    submission(now, null), submission(now.minusDays(3), null), submission(now.minusDays(10), null)));

            // When
            SurveyAnalyticsResponse report = analyticsService.getSurveyAnalytics(1L, "owner");

            // Then
            assertEquals(7, report.getTrends().size());
            assertEquals(1L, report.getTrends().get(6).getCount(), "今天 1 筆");
            assertEquals(1L, report.getTrends().get(3).getCount(), "3 天前 1 筆");
            assertEquals(2L, report.getTrends().stream().mapToLong(t -> t.getCount()).sum());
        }

        @Test
        @DisplayName("沒有瀏覽紀錄但有填答：完成率為 100%；完全沒有填答：0% 且最新回覆時間為 null")
        void completionRate_EdgeCases() {
            // Given
            Survey noViews = Survey.builder().id(2L).creator(owner).viewCount(0).build();
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(2L)).thenReturn(Optional.of(noViews));
            when(submissionRepository.findBySurveyIdNative(2L)).thenReturn(List.of(submission(now, null)));

            // Then
            assertEquals(100.0, analyticsService.getSurveyAnalytics(2L, "owner").getKpi().getCompletionRate());
            SurveyAnalyticsResponse empty = analyticsService.getSurveyAnalytics(1L, "owner");
            assertEquals(0.0, empty.getKpi().getCompletionRate());
            assertNull(empty.getLastResponseAt());
        }

        @Test
        @DisplayName("最新回覆時間：取所有填答中最晚者，格式 yyyy/MM/dd HH:mm:ss")
        void lastResponseAt_IsLatestSubmission() {
            // Given
            LocalDateTime latest = LocalDateTime.of(2026, 11, 10, 9, 30, 15);
            when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(List.of(
                    submission(latest.minusDays(1), null), submission(latest, null)));

            // Then
            assertEquals("2026/11/10 09:30:15", analyticsService.getSurveyAnalytics(1L, "owner").getLastResponseAt());
        }
    }

    @Nested
    @DisplayName("受訪者輪廓")
    class Demographics {

        @Test
        @DisplayName("性別、年齡 (標準化分組)、產業分別統計")
        void demographics_AreAggregated() {
            // Given
            when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(List.of(
                    submission(now, SubmissionBasicInfo.builder().gender("男").ageGroup("22").industry("資訊").build()),
                    submission(now, SubmissionBasicInfo.builder().gender("女").ageGroup("25-34").industry("資訊").build()),
                    submission(now, SubmissionBasicInfo.builder().gender("男").ageGroup("60歲").build()),
                    submission(now, SubmissionBasicInfo.builder().ageGroup("未滿18").build()),
                    submission(now, SubmissionBasicInfo.builder().ageGroup("秘密").build()),
                    submission(now, null)));

            // When
            DemographicsDto demographics = analyticsService.getSurveyAnalytics(1L, "owner").getDemographics();

            // Then
            assertEquals(Map.of("男", 2L, "女", 1L), demographics.getGenderDistribution());
            assertEquals(1L, demographics.getAgeGroupDistribution().get("18-24歲"), "數字 22 歸入 18-24 歲");
            assertEquals(1L, demographics.getAgeGroupDistribution().get("25-34歲"));
            assertEquals(1L, demographics.getAgeGroupDistribution().get("45歲以上"), "60 歲歸入 45 歲以上");
            assertEquals(1L, demographics.getAgeGroupDistribution().get("18歲以下"));
            assertEquals(1L, demographics.getAgeGroupDistribution().get("秘密"), "無法辨識時保留原字串");
            assertEquals(Map.of("資訊", 2L), demographics.getIndustryDistribution());
        }

        @Test
        @DisplayName("完全匿名 (沒有任何輪廓資料)：輪廓為 null，前端不繪製假圖表")
        void anonymousSurvey_ReturnsNull() {
            givenSubmissions(3);

            assertNull(analyticsService.getSurveyAnalytics(1L, "owner").getDemographics());
        }
    }

    @Nested
    @DisplayName("選擇題統計")
    class ChoiceQuestions {

        @Test
        @DisplayName("單選題：以選項 ID 統計票數與百分比 (欄位名稱大小寫不同也能讀取)")
        void single_CountsByOptionId() {
            // Given：4 份填答，A 3 票、B 1 票
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE, option(101L, "A", 1), option(102L, "B", 2))));
            givenSubmissions(4);
            when(answerRepository.countSelectionsByQuestionNative(10L)).thenReturn(List.of(
                    Map.of("optionId", 101L, "selectedCount", 3L),
                    Map.of("OPTIONID", 102, "SELECTEDCOUNT", 1)));

            // When
            QuestionStatDto stat = firstQuestionStat();

            // Then：A = 3 / 4 = 75%，B = 1 / 4 = 25%
            assertEquals(4L, stat.getTotalAnswers());
            assertEquals(75.0, stat.getOptionsList().get(0).getPercentage());
            assertEquals(25.0, stat.getOptionsList().get(1).getPercentage());
            assertEquals(3L, stat.getOptionCounts().get("A"));
        }

        @Test
        @DisplayName("是非題沒有選項 ID 統計：改由文字作答補全 (true / yes → 是，否 → 否，空白略過)")
        void boolean_BackfillsFromText() {
            // Given
            survey.replaceQuestions(List.of(question(30L, "Q3", QuestionType.BOOLEAN)));
            givenSubmissions(4);
            when(answerRepository.findTextAnswersByQuestionNative(30L)).thenReturn(Arrays.asList("true", "yes", "否", " ", null));

            // When
            QuestionStatDto stat = firstQuestionStat();

            // Then
            assertEquals(3L, stat.getTotalAnswers());
            assertEquals(2L, stat.getOptionCounts().get("是"));
            assertEquals(1L, stat.getOptionCounts().get("否"));
            assertEquals(50.0, stat.getOptionPercentages().get("是"), "2 / 4 = 50%");
        }

        @Test
        @DisplayName("多選題文字補全：逗號拆分後比對選項；總作答數可超過填答份數")
        void multi_BackfillsCommaSeparatedText() {
            // Given：2 份填答，共勾選 3 次
            survey.replaceQuestions(List.of(question(20L, "Q2", QuestionType.MULTI, option(201L, "紅", 1), option(202L, "藍", 2))));
            givenSubmissions(2);
            when(answerRepository.findTextAnswersByQuestionNative(20L)).thenReturn(List.of("紅, 藍", "藍"));

            // When
            QuestionStatDto stat = firstQuestionStat();

            // Then
            assertEquals(3L, stat.getTotalAnswers());
            assertEquals(2L, stat.getOptionsList().get(1).getCount(), "藍 2 票");
        }

        @Test
        @DisplayName("單選題文字補全：比對不到之文字另列為新項目")
        void single_BackfillKeepsUnmatchedText() {
            // Given
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE, option(101L, "A", 1))));
            givenSubmissions(2);
            when(answerRepository.findTextAnswersByQuestionNative(10L)).thenReturn(List.of("a", "其他意見"));

            // When
            QuestionStatDto stat = firstQuestionStat();

            // Then
            assertEquals(1L, stat.getOptionCounts().get("A"));
            assertEquals(1L, stat.getOptionCounts().get("其他意見"));
        }

        @Test
        @DisplayName("跳題條件：被解鎖之題目標記觸發來源，作答率 = 作答數 / 前置選項被選次數")
        void triggeredQuestion_HasResponseRate() {
            // Given：選了 Q1 的 A (4 次) 才會出現 Q5，Q5 有 2 筆作答 → 作答率 50%
            SurveyOption trigger = SurveyOption.builder().id(101L).optionText("A").sortOrder(1).triggerQuestionUid("Q5").build();
            survey.replaceQuestions(List.of(
                    question(10L, "Q1", QuestionType.SINGLE, trigger),
                    question(50L, "Q5", QuestionType.TEXT)));
            givenSubmissions(4);
            when(answerRepository.countSelectionsByOptionIdNative(101L)).thenReturn(4L);
            when(answerRepository.findStructuredTextAnswersByQuestionNative(50L)).thenReturn(List.of(
                    Map.of("content", "回饋一"), Map.of("content", "回饋二")));

            // When
            QuestionStatDto q5 = analyticsService.getSurveyAnalytics(1L, "owner").getQuestionStats().get(1);

            // Then
            assertTrue(q5.getIsConditionTriggered());
            assertTrue(q5.getTriggerConditionDescription().contains("題目Q1"));
            assertEquals(4L, q5.getTriggeredEligibleCount());
            assertEquals(50.0, q5.getTriggeredResponseRate());
            assertEquals(2, q5.getTextFeedbacks().size());
        }
    }

    @Nested
    @DisplayName("評分題統計")
    class RatingQuestions {

        @Test
        @DisplayName("5 分量表：平均、中位數、眾數、標準差、題目 NPS 與問卷整體 NPS")
        void rating_Statistics() {
            // Given：分數為 5、5、2 (5 分量表：≥4 推薦者、3 被動者、≤2 批評者)
            survey.replaceQuestions(List.of(question(40L, "Q4", QuestionType.RATING)));
            givenSubmissions(3);
            when(answerRepository.calculateRatingStatsNative(40L)).thenReturn(Map.of("avgRating", 4.0, "totalCount", 3L));
            when(answerRepository.countRatingDistributionByQuestionNative(40L)).thenReturn(List.of(
                    Map.of("ratingValue", 5, "ratingCount", 2L),
                    Map.of("ratingValue", 2, "ratingCount", 1L)));

            // When
            SurveyAnalyticsResponse report = analyticsService.getSurveyAnalytics(1L, "owner");
            QuestionStatDto stat = report.getQuestionStats().get(0);

            // Then
            assertEquals(4.0, stat.getAverageRating());
            assertEquals(5.0, stat.getMedianRating(), "排序後 [2, 5, 5] 中間值為 5");
            assertEquals(5, stat.getModeRating(), "5 出現 2 次最多");
            assertEquals(1.4, stat.getStandardDeviation(), "√((1² + 1² + 2²) / 3) = √2 ≈ 1.4");
            assertEquals(33, stat.getNpsScore(), "(推薦 2 − 批評 1) / 3 × 100 ≈ 33");
            assertEquals(Map.of("detractor", 1L, "passive", 0L, "promoter", 2L), stat.getNpsBreakdown());
            assertEquals(5, stat.getRatingDistribution().size(), "1~5 分每一級都要列出 (沒人選者為 0)");
            assertEquals(33, report.getKpi().getNetPromoterScore());
        }

        @Test
        @DisplayName("10 分量表：≥9 推薦者、7~8 被動者、≤6 批評者")
        void rating_TenPointScale() {
            // Given：分數 10、8、3
            survey.replaceQuestions(List.of(SurveyQuestion.builder().id(41L).questionCode("Q4").type(QuestionType.RATING).maxRating(10).build()));
            givenSubmissions(3);
            when(answerRepository.calculateRatingStatsNative(41L)).thenReturn(Map.of("avgRating", 7.0, "totalCount", 3L));
            when(answerRepository.countRatingDistributionByQuestionNative(41L)).thenReturn(List.of(
                    Map.of("ratingValue", 10, "ratingCount", 1L),
                    Map.of("ratingValue", 8, "ratingCount", 1L),
                    Map.of("ratingValue", 3, "ratingCount", 1L)));

            // Then：(1 − 1) / 3 = 0
            assertEquals(0, firstQuestionStat().getNpsScore());
        }

        @Test
        @DisplayName("沒有數字分數時：從文字作答解析分數補全 (例如「非常滿意」= 5)")
        void rating_BackfillsFromText() {
            // Given
            survey.replaceQuestions(List.of(question(40L, "Q4", QuestionType.RATING)));
            givenSubmissions(2);
            when(answerRepository.findTextAnswersByQuestionNative(40L)).thenReturn(List.of("非常滿意", "普通", "看心情"));

            // When
            QuestionStatDto stat = firstQuestionStat();

            // Then：(5 + 3) / 2 = 4.0，無法辨識之「看心情」不計入
            assertEquals(2L, stat.getTotalAnswers());
            assertEquals(4.0, stat.getAverageRating());
        }
    }

    @Test
    @DisplayName("全站總覽：會員數、問卷數、填答數與伺服器時間")
    void platformOverview() {
        // Given
        when(userRepository.countActiveUsersNative()).thenReturn(12L);
        when(surveyRepository.countActiveSurveysNative()).thenReturn(24L);
        when(submissionRepository.countTotalSubmissionsNative()).thenReturn(300L);

        // When
        Map<String, Object> overview = analyticsService.getPlatformOverview();

        // Then
        assertEquals(12L, overview.get("totalUsers"));
        assertEquals(24L, overview.get("totalSurveys"));
        assertEquals(300L, overview.get("totalSubmissions"));
        assertNotNull(overview.get("serverTime"));
    }

    @Nested
    @DisplayName("邊界情境")
    class EdgeCases {

        @Test
        @DisplayName("年齡分組：每個區間之數字與文字寫法都歸入正確分組")
        void ageGroups_AllBuckets() {
            // Given
            List<String> ages = List.of("17", "30", "40", "35~44", "45以上", "18至24");
            List<Submission> submissions = ages.stream()
                    .map(age -> submission(now, SubmissionBasicInfo.builder().ageGroup(age).gender(" ").industry("").build()))
                    .toList();
            when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(submissions);

            // When
            Map<String, Long> distribution = analyticsService.getSurveyAnalytics(1L, "owner").getDemographics().getAgeGroupDistribution();

            // Then
            assertEquals(Map.of("18歲以下", 1L, "25-34歲", 1L, "35-44歲", 2L, "45歲以上", 1L, "18-24歲", 1L), distribution);
        }

        @Test
        @DisplayName("報表日期：起訖時間以 yyyy-MM-dd 回傳；未設定時為 null")
        void reportDates() {
            assertNull(analyticsService.getSurveyAnalytics(1L, "owner").getStartDate());

            survey.patchSettings(LocalDateTime.of(2026, 1, 2, 9, 0), LocalDateTime.of(2026, 3, 4, 18, 0), null, null);
            SurveyAnalyticsResponse report = analyticsService.getSurveyAnalytics(1L, "owner");

            assertEquals("2026-01-02", report.getStartDate());
            assertEquals("2026-03-04", report.getEndDate());
        }

        @Test
        @DisplayName("送出時間為 null 之填答：不計入今日與趨勢，也不拋出例外")
        void submissionWithoutTime_IsIgnored() {
            when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(List.of(submission(null, null)));

            SurveyAnalyticsResponse report = analyticsService.getSurveyAnalytics(1L, "owner");

            assertEquals(0L, report.getKpi().getTodayResponses());
            assertEquals(0L, report.getTrends().stream().mapToLong(t -> t.getCount()).sum());
        }

        @Test
        @DisplayName("是非題文字補全：false / no / 1 / 0 都能辨識；無法辨識之文字不計入")
        void booleanBackfill_AllLiterals() {
            survey.replaceQuestions(List.of(question(30L, "Q3", QuestionType.BOOLEAN)));
            givenSubmissions(5);
            when(answerRepository.findTextAnswersByQuestionNative(30L)).thenReturn(List.of("false", "NO", "1", "0", "也許"));

            QuestionStatDto stat = firstQuestionStat();

            assertEquals(1L, stat.getOptionCounts().get("是"));
            assertEquals(3L, stat.getOptionCounts().get("否"));
        }

        @Test
        @DisplayName("是非題有自訂選項：選項依排序列出，是 / 否 之外的自訂文字也會保留")
        void booleanWithCustomOptions() {
            survey.replaceQuestions(List.of(question(30L, "Q3", QuestionType.BOOLEAN, option(302L, "否", 2), option(301L, "是", 1))));
            givenSubmissions(1);

            QuestionStatDto stat = firstQuestionStat();

            assertEquals(List.of("是", "否"), stat.getOptionsList().stream().map(o -> o.getText()).toList());
        }

        @Test
        @DisplayName("沒有任何填答時：百分比以 1 為分母避免除以 0，全部為 0%")
        void noResponses_AvoidsDivisionByZero() {
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE, option(101L, "A", 1))));

            assertEquals(0.0, firstQuestionStat().getOptionsList().get(0).getPercentage());
        }

        @Test
        @DisplayName("單選題作答數大於填答份數 (資料異常) 時，以填答份數為上限；多選題則不設上限")
        void singleChoiceTotal_IsCappedByResponses() {
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE, option(101L, "A", 1))));
            givenSubmissions(2);
            when(answerRepository.countSelectionsByQuestionNative(10L)).thenReturn(List.of(Map.of("optionId", 101L, "selectedCount", 5L)));

            assertEquals(2L, firstQuestionStat().getTotalAnswers());
        }

        @Test
        @DisplayName("選項統計列含 NULL 之 option_id 或不屬於本題之選項：只累計總數，不計入任何選項")
        void choiceRowsWithUnknownOption() {
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE, option(101L, "A", 1))));
            givenSubmissions(3);
            HashMap<String, Object> nullOption = new HashMap<>();
            nullOption.put("optionId", null);
            nullOption.put("selectedCount", 1L);
            when(answerRepository.countSelectionsByQuestionNative(10L)).thenReturn(List.of(
                    nullOption, Map.of("optionId", 999L, "selectedCount", 1L), Map.of("optionId", 101L, "selectedCount", 1L)));

            QuestionStatDto stat = firstQuestionStat();

            assertEquals(1L, stat.getOptionsList().get(0).getCount());
            assertEquals(3L, stat.getTotalAnswers());
        }

        @Test
        @DisplayName("多選題文字補全：空白片段略過，比對不到之片段不計入")
        void multiBackfill_SkipsBlankAndUnknownParts() {
            survey.replaceQuestions(List.of(question(20L, "Q2", QuestionType.MULTI, option(201L, "紅", 1))));
            givenSubmissions(1);
            when(answerRepository.findTextAnswersByQuestionNative(20L)).thenReturn(List.of("紅, ,綠"));

            assertEquals(1L, firstQuestionStat().getTotalAnswers());
        }

        @Test
        @DisplayName("沒有選項之單選題：文字補全不新增項目 (無選項可比對)")
        void singleWithoutOptions_BackfillAddsNothing() {
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE)));
            givenSubmissions(1);
            when(answerRepository.findTextAnswersByQuestionNative(10L)).thenReturn(List.of("隨便"));

            assertEquals(0L, firstQuestionStat().getTotalAnswers());
        }

        @Test
        @DisplayName("評分題未設定上下限且平均 > 5：推定為 10 分量表；實際分數超出範圍時自動擴大")
        void rating_InfersScaleAndExtendsRange() {
            survey.replaceQuestions(List.of(SurveyQuestion.builder().id(42L).questionCode("Q4").type(QuestionType.RATING)
                    .minRating(null).maxRating(null).build()));
            givenSubmissions(2);
            when(answerRepository.calculateRatingStatsNative(42L)).thenReturn(Map.of("avgRating", 6.0, "totalCount", 2L));
            when(answerRepository.countRatingDistributionByQuestionNative(42L)).thenReturn(List.of(
                    Map.of("ratingValue", 0, "ratingCount", 1L), Map.of("ratingValue", 12, "ratingCount", 1L)));

            QuestionStatDto stat = firstQuestionStat();

            assertEquals(0, stat.getMinRating());
            assertEquals(12, stat.getMaxRating());
        }

        @Test
        @DisplayName("評分題只有 1 筆：標準差為 0；分佈列缺少欄位時略過")
        void rating_SingleAnswerAndIncompleteRows() {
            survey.replaceQuestions(List.of(question(40L, "Q4", QuestionType.RATING)));
            givenSubmissions(1);
            when(answerRepository.calculateRatingStatsNative(40L)).thenReturn(Map.of("avgRating", 3.0, "totalCount", 1L));
            when(answerRepository.countRatingDistributionByQuestionNative(40L)).thenReturn(List.of(
                    Map.of("ratingValue", 3, "ratingCount", 1L), Map.of("ratingCount", 1L)));

            QuestionStatDto stat = firstQuestionStat();

            assertEquals(0.0, stat.getStandardDeviation());
            assertEquals(Map.of("detractor", 0L, "passive", 1L, "promoter", 0L), stat.getNpsBreakdown());
        }

        @Test
        @DisplayName("評分題完全沒有作答 (數字與文字皆無)：平均 0、中位數 0、NPS 0")
        void rating_NoAnswers() {
            survey.replaceQuestions(List.of(question(40L, "Q4", QuestionType.RATING)));
            givenSubmissions(1);
            when(answerRepository.calculateRatingStatsNative(40L)).thenReturn(Map.of("totalCount", 0L));

            QuestionStatDto stat = firstQuestionStat();

            assertEquals(0.0, stat.getAverageRating());
            assertEquals(0.0, stat.getMedianRating());
            assertEquals(0, stat.getNpsScore());
        }

        @Test
        @DisplayName("中位數：偶數筆時取中間兩數平均 ([2, 4] → 3.0)")
        void rating_MedianOfEvenCount() {
            survey.replaceQuestions(List.of(question(40L, "Q4", QuestionType.RATING)));
            givenSubmissions(2);
            when(answerRepository.calculateRatingStatsNative(40L)).thenReturn(Map.of("avgRating", 3.0, "totalCount", 2L));
            when(answerRepository.countRatingDistributionByQuestionNative(40L)).thenReturn(List.of(
                    Map.of("ratingValue", 2, "ratingCount", 1L), Map.of("ratingValue", 4, "ratingCount", 1L)));

            assertEquals(3.0, firstQuestionStat().getMedianRating());
        }

        @Test
        @DisplayName("問卷整體 NPS：先看完全部分數再決定量表 (出現 9 分 → 10 分量表)，結果不受資料順序影響")
        void surveyNps_DefaultsAndExtendsScale() {
            survey.replaceQuestions(List.of(SurveyQuestion.builder().id(43L).type(QuestionType.RATING).maxRating(null).build()));
            givenSubmissions(2);
            HashMap<String, Object> incomplete = new HashMap<>();
            incomplete.put("ratingValue", null);
            incomplete.put("ratingCount", 1L);
            when(answerRepository.countRatingDistributionByQuestionNative(43L)).thenReturn(List.of(
                    Map.of("ratingValue", 9, "ratingCount", 1L), Map.of("ratingValue", 6, "ratingCount", 1L), incomplete));

            // 9 分 → 推薦、6 分 → 批評 (10 分量表)，(1 − 1) / 2 = 0
            assertEquals(0, analyticsService.getSurveyAnalytics(1L, "owner").getKpi().getNetPromoterScore());
        }

        @Test
        @DisplayName("跳題條件：前置選項從未被選 (0 次) 時作答率為 100%；選取次數為 null 時以 0 計")
        void triggeredQuestion_ZeroEligible() {
            SurveyOption trigger = SurveyOption.builder().id(101L).optionText("A").sortOrder(1).triggerQuestionUid(" Q5 ").build();
            SurveyOption blankTrigger = SurveyOption.builder().id(102L).optionText("B").sortOrder(2).triggerQuestionUid(" ").build();
            survey.replaceQuestions(List.of(question(10L, "Q1", QuestionType.SINGLE, trigger, blankTrigger), question(50L, "Q5", QuestionType.DATE)));
            givenSubmissions(1);
            when(answerRepository.countSelectionsByOptionIdNative(101L)).thenReturn(null);

            QuestionStatDto q5 = analyticsService.getSurveyAnalytics(1L, "owner").getQuestionStats().get(1);

            assertEquals(0L, q5.getTriggeredEligibleCount());
            assertEquals(100.0, q5.getTriggeredResponseRate());
        }

        @Test
        @DisplayName("平均作答秒數為 null (資料庫沒有任何作答時間) 時以 0 計")
        void averageDuration_NullIsZero() {
            when(submissionRepository.getAverageDurationSecondsNative(1L)).thenReturn(null);

            assertEquals(0, analyticsService.getSurveyAnalytics(1L, "owner").getKpi().getAverageDurationSeconds());
        }

        @Test
        @DisplayName("完成率上限 100%：回收數大於瀏覽數 (例如直接以連結填答) 時不超過 100")
        void completionRate_IsCappedAt100() {
            survey = Survey.builder().id(1L).creator(owner).viewCount(1).build();
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(survey));
            givenSubmissions(3);

            assertEquals(100.0, analyticsService.getSurveyAnalytics(1L, "owner").getKpi().getCompletionRate());
        }
    }
}
