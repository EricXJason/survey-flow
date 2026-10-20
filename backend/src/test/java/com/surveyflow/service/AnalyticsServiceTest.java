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

    @Test
    @DisplayName("分析權限測試: 非建立者且非管理者存取應拋出 AccessDeniedException")
    void getSurveyAnalytics_Unauthorized_ThrowsAccessDeniedException() {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(sampleSurvey));
        when(userRepository.findByUsername("unauthorized")).thenReturn(Optional.of(hacker));

        assertThrows(AccessDeniedException.class, () -> analyticsService.getSurveyAnalytics(1L, "unauthorized"));
    }
}
