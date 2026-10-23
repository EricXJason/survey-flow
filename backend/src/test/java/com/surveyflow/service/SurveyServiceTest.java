/**
 * ============================================================================
 * 檔案名稱: SurveyServiceTest.java
 * 所屬模組: Test 測試層 - 問卷業務單元測試
 * 責任描述: 採用 JUnit 5 與 Mockito 測試問卷建立、查詢、權限守衛與軟刪除
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: SurveyService
 *   - 模擬對象: SurveyRepository, UserRepository, SubmissionRepository
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.survey.SurveyCreateRequest;
import com.surveyflow.dto.survey.SurveyResponse;
import com.surveyflow.entity.Survey;
import com.surveyflow.entity.User;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private SurveyService surveyService;

    private User owner;
    private User otherUser;
    private Survey sampleSurvey;

    /**
     * 測試前置資料初始化
     * 構建問卷建立者、第三方無關使用者以及包含題目之範例問卷實體
     */
    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(1L)
                .username("owner")
                .fullName("問卷擁有者")
                .role(RoleType.ROLE_USER)
                .build();

        otherUser = User.builder()
                .id(2L)
                .username("other")
                .fullName("無關路人")
                .role(RoleType.ROLE_USER)
                .build();

        sampleSurvey = Survey.builder()
                .id(100L)
                .creator(owner)
                .title("2026 顧客滿意度調研")
                .category("顧客滿意度")
                .isOpen(true)
                .isPublicToLobby(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(7))
                .viewCount(10)
                .questions(new ArrayList<>())
                .build();
    }

    /**
     * 測試會員建立問卷
     * 驗證正常傳入問卷標題、起訖時間等參數時，能成功持久化並回傳問卷詳情 DTO
     */
    @Test
    @DisplayName("建立問卷測試: 正常傳入時應成功持久化並回傳問卷詳情")
    void createSurvey_Success() {
        SurveyCreateRequest request = SurveyCreateRequest.builder()
                .title("新問卷主題")
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(5))
                .build();

        when(userRepository.findByUsernameNative("owner")).thenReturn(Optional.of(owner));
        when(surveyRepository.save(any(Survey.class))).thenReturn(sampleSurvey);
        when(submissionRepository.countBySurveyIdNative(anyLong())).thenReturn(0L);

        SurveyResponse response = surveyService.createSurvey(request, "owner");

        assertNotNull(response);
        assertEquals(sampleSurvey.getTitle(), response.getTitle());
        verify(surveyRepository, times(1)).save(any(Survey.class));
    }

    /**
     * 測試受訪者/訪客公開查詢問卷
     * 驗證進入填答頁面載入問卷時，系統會自動累加 viewCount 瀏覽計數並持久化
     */
    @Test
    @DisplayName("公開取得問卷測試: 進入填答應自動累加 viewCount 瀏覽計數")
    void getPublicSurvey_IncrementsViewCount() {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(100L)).thenReturn(Optional.of(sampleSurvey));
        when(surveyRepository.save(any(Survey.class))).thenReturn(sampleSurvey);
        when(submissionRepository.countBySurveyIdNative(100L)).thenReturn(5L);

        SurveyResponse response = surveyService.getPublicSurvey(100L);

        assertNotNull(response);
        assertEquals(11, sampleSurvey.getViewCount());
        verify(surveyRepository, times(1)).save(sampleSurvey);
    }

    /**
     * 測試問卷軟刪除權限阻斷
     * 驗證非問卷建立者且非系統管理員嘗試刪除時，應觸發 AccessDeniedException 阻斷
     */
    @Test
    @DisplayName("軟刪除權限測試: 非建立者且非管理員嘗試刪除時應拋出 AccessDeniedException")
    void deleteSurvey_UnauthorizedUser_ThrowsAccessDeniedException() {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(100L)).thenReturn(Optional.of(sampleSurvey));
        when(userRepository.findByUsernameNative("other")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class, () -> surveyService.deleteSurvey(100L, "other"));
        verify(surveyRepository, never()).save(any(Survey.class));
    }
}
