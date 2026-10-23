/**
 * ============================================================================
 * 檔案名稱: AdminServiceTest.java
 * 所屬模組: Test 測試層 - 管理者業務服務單元測試
 * 責任描述: 測試 AdminService 對於會員治理、問卷審查與全站 KPI 之流程編排與代理委託
 * 架構分層: Test Layer / Service Test
 * 聯動關係:
 *   - 測試目標: AdminService
 *   - 模擬對象: UserService, SurveyService, AnalyticsService
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import com.surveyflow.dto.survey.SurveySummaryResponse;
import com.surveyflow.dto.user.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private SurveyService surveyService;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AdminService adminService;

    /**
     * 測試查詢全站會員列表委託
     * 驗證情境: 呼叫 getAllUsers 應正確委託至 userService.getAllUsersForAdmin
     */
    @Test
    @DisplayName("管理業務測試: 查詢全站會員應正確委託 UserService")
    void getAllUsers_DelegatesToUserService() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserProfileResponse> mockPage = new PageImpl<>(Collections.emptyList());
        when(userService.getAllUsersForAdmin(pageable)).thenReturn(mockPage);

        Page<UserProfileResponse> result = adminService.getAllUsers(pageable);

        assertNotNull(result);
        verify(userService, times(1)).getAllUsersForAdmin(pageable);
    }

    /**
     * 測試修改會員帳號狀態委託
     * 驗證情境: 呼叫 updateUserStatus 應正確委託至 userService.updateUserStatus
     */
    @Test
    @DisplayName("管理業務測試: 更新會員狀態應正確委託 UserService")
    void updateUserStatus_DelegatesToUserService() {
        Long userId = 1L;
        String status = "SUSPENDED";

        adminService.updateUserStatus(userId, status);

        verify(userService, times(1)).updateUserStatus(userId, status);
    }

    /**
     * 測試軟刪除會員帳號委託
     * 驗證情境: 呼叫 deleteUser 應正確委託至 userService.deleteUserByAdmin
     */
    @Test
    @DisplayName("管理業務測試: 軟刪除會員應正確委託 UserService")
    void deleteUser_DelegatesToUserService() {
        Long userId = 2L;

        adminService.deleteUser(userId);

        verify(userService, times(1)).deleteUserByAdmin(userId);
    }

    /**
     * 測試查詢全站問卷列表委託
     * 驗證情境: 呼叫 getAllSurveys 應正確委託至 surveyService.getAllSurveysForAdmin
     */
    @Test
    @DisplayName("管理業務測試: 查詢全站問卷應正確委託 SurveyService")
    void getAllSurveys_DelegatesToSurveyService() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SurveySummaryResponse> mockPage = new PageImpl<>(Collections.emptyList());
        when(surveyService.getAllSurveysForAdmin(pageable)).thenReturn(mockPage);

        Page<SurveySummaryResponse> result = adminService.getAllSurveys(pageable);

        assertNotNull(result);
        verify(surveyService, times(1)).getAllSurveysForAdmin(pageable);
    }

    /**
     * 測試管理員強制刪除問卷委託
     * 驗證情境: 呼叫 forceDeleteSurvey 應正確委託至 surveyService.deleteSurvey
     */
    @Test
    @DisplayName("管理業務測試: 強制刪除問卷應正確委託 SurveyService")
    void forceDeleteSurvey_DelegatesToSurveyService() {
        Long surveyId = 10L;
        String admin = "admin";

        adminService.forceDeleteSurvey(surveyId, admin);

        verify(surveyService, times(1)).deleteSurvey(surveyId, admin);
    }

    /**
     * 測試全站營運總覽統計指標委託
     * 驗證情境: 呼叫 getPlatformOverview 應正確委託至 analyticsService.getPlatformOverview
     */
    @Test
    @DisplayName("管理業務測試: 獲取全站指標應正確委託 AnalyticsService")
    void getPlatformOverview_DelegatesToAnalyticsService() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", 50L);
        when(analyticsService.getPlatformOverview()).thenReturn(stats);

        Map<String, Object> result = adminService.getPlatformOverview();

        assertEquals(50L, result.get("totalUsers"));
        verify(analyticsService, times(1)).getPlatformOverview();
    }

    /**
     * 測試管理員調閱問卷完整報表委託
     * 驗證情境: 呼叫 getSurveyAnalytics 應正確委託至 analyticsService.getSurveyAnalytics
     */
    @Test
    @DisplayName("管理業務測試: 調閱問卷分析報表應正確委託 AnalyticsService")
    void getSurveyAnalytics_DelegatesToAnalyticsService() {
        Long surveyId = 99L;
        String admin = "admin";
        SurveyAnalyticsResponse mockResponse = SurveyAnalyticsResponse.builder().surveyId(surveyId).build();
        when(analyticsService.getSurveyAnalytics(surveyId, admin)).thenReturn(mockResponse);

        SurveyAnalyticsResponse result = adminService.getSurveyAnalytics(surveyId, admin);

        assertNotNull(result);
        assertEquals(surveyId, result.getSurveyId());
        verify(analyticsService, times(1)).getSurveyAnalytics(surveyId, admin);
    }
}
