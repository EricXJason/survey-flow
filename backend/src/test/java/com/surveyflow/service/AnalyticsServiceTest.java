/**
 * ============================================================================
 * 檔案名稱: AnalyticsServiceTest.java
 * 所屬模組: Test 測試層 - 數據分析業務單元測試
 * 責任描述: 採用 JUnit 5 與 Mockito 測試數據分析 KPI 運算、權限阻斷與趨勢聚合
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: AnalyticsService
 *   - 模擬對象: SurveyRepository, SubmissionRepository, SubmissionAnswerRepository, UserRepository
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import com.surveyflow.entity.Submission;
import com.surveyflow.entity.Survey;
import com.surveyflow.entity.User;
import com.surveyflow.repository.SubmissionAnswerRepository;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import com.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionAnswerRepository answerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private User owner;
    private User hacker;
    private Survey sampleSurvey;

    /**
     * 測試前置資料初始化
     * 構建問卷擁有者、非授權使用者以及範例問卷實體
     */
    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(1L)
                .username("analyst")
                .role(RoleType.ROLE_USER)
                .build();

        hacker = User.builder()
                .id(99L)
                .username("unauthorized")
                .role(RoleType.ROLE_USER)
                .build();

        sampleSurvey = Survey.builder()
                .id(1L)
                .creator(owner)
                .title("平台滿意度調查")
                .isOpen(true)
                .isPublicToLobby(true)
                .viewCount(100)
                .questions(new ArrayList<>())
                .build();
    }

    /**
     * 測試問卷擁有者查詢統計報表
     * 驗證問卷建立者登入查詢時，能精確運算回收份數、填答耗時等 5 大 KPI 與聚合數據
     */
    @Test
    @DisplayName("分析報表測試: 擁有者存取應成功產出 5 大 KPI 與聚合數據")
    void getSurveyAnalytics_OwnerAccess_Success() {
        List<Submission> submissions = new ArrayList<>();
        submissions.add(Submission.builder()
                .id(1L)
                .survey(sampleSurvey)
                .durationSeconds(120)
                .submittedAt(LocalDateTime.now())
                .build());

        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(sampleSurvey));
        when(userRepository.findByUsername("analyst")).thenReturn(Optional.of(owner));
        when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(submissions);
        when(submissionRepository.getAverageDurationSecondsNative(1L)).thenReturn(120.0);

        SurveyAnalyticsResponse response = analyticsService.getSurveyAnalytics(1L, "analyst");

        assertNotNull(response);
        assertEquals(1L, response.getSurveyId());
        assertEquals(1L, response.getKpi().getTotalResponses());
        assertEquals(120, response.getKpi().getAverageDurationSeconds());
    }

    /**
     * 測試非授權使用者查詢統計報表阻斷
     * 驗證非問卷建立者且非系統管理員嘗試讀取數據時，拋出 AccessDeniedException
     */
    @Test
    @DisplayName("分析權限測試: 非建立者且非管理者存取應拋出 AccessDeniedException")
    void getSurveyAnalytics_Unauthorized_ThrowsAccessDeniedException() {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(sampleSurvey));
        when(userRepository.findByUsername("unauthorized")).thenReturn(Optional.of(hacker));

        assertThrows(AccessDeniedException.class, () -> analyticsService.getSurveyAnalytics(1L, "unauthorized"));
    }

    /**
     * 測試是非題後備文本匹配與選項統計
     * 驗證是非題在缺少選項 ID 關聯時，透過文字答案後備機制精準聚合「是」與「否」票數與百分比
     */
    @Test
    @DisplayName("分析報表測試: 是非題在無 optionId 時透過 text_answer 後備補全，應正確聚合「是」與「否」且總得票數大於0")
    void getSurveyAnalytics_WithBooleanFallbackAndOrderedOptions_Success() {
        com.surveyflow.entity.SurveyQuestion boolQ = com.surveyflow.entity.SurveyQuestion.builder()
                .id(10L)
                .questionCode("Q1")
                .questionText("ww")
                .type(com.surveyflow.common.enums.QuestionType.BOOLEAN)
                .sortOrder(1)
                .options(new ArrayList<>())
                .build();
        sampleSurvey.getQuestions().add(boolQ);

        List<Submission> submissions = List.of(
                Submission.builder().id(1L).survey(sampleSurvey).durationSeconds(60).submittedAt(LocalDateTime.now()).build()
        );

        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(sampleSurvey));
        when(userRepository.findByUsername("analyst")).thenReturn(Optional.of(owner));
        when(submissionRepository.findBySurveyIdNative(1L)).thenReturn(submissions);
        when(submissionRepository.getAverageDurationSecondsNative(1L)).thenReturn(60.0);

        // 模擬 native option 統計為 0
        when(answerRepository.countSelectionsByQuestionNative(10L)).thenReturn(new ArrayList<>());
        // 模擬 text_answer 存有 "false"
        when(answerRepository.findTextAnswersByQuestionNative(10L)).thenReturn(List.of("false"));

        SurveyAnalyticsResponse response = analyticsService.getSurveyAnalytics(1L, "analyst");
        assertNotNull(response);
        assertFalse(response.getQuestionStats().isEmpty());

        var qStat = response.getQuestionStats().get(0);
        assertEquals(10L, qStat.getQuestionId());
        assertEquals("ww", qStat.getQuestionText());
        assertEquals(1L, qStat.getTotalAnswers());
        assertEquals(1L, qStat.getOptionCounts().get("否"));
        assertEquals(0L, qStat.getOptionCounts().get("是"));
        assertEquals(100.0, qStat.getOptionPercentages().get("否"));
        assertEquals(0.0, qStat.getOptionPercentages().get("是"));
    }
}
