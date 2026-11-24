/**
 * ============================================================================
 * 檔案名稱: AnalyticsControllerTest.java
 * 測試對象: AnalyticsController (單一問卷統計報表端點)
 * 測試方式: MockMvc standalone + AnalyticsService Mock
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import org.jasonproduction.surveyflow.service.AnalyticsService;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock private AnalyticsService analyticsService;

    private MockMvc mockMvc;

    /**
     * 建立受測 Controller 並模擬 owner 已登入
     */
    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AnalyticsController(analyticsService));
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
    }

    /**
     * 清除模擬登入狀態
     */
    @AfterEach
    void tearDown() {
        ControllerTestSupport.logout();
    }

    @Test
    @DisplayName("GET /api/analytics/surveys/{id}：以登入者帳號查詢報表")
    void getReport() throws Exception {
        when(analyticsService.getSurveyAnalytics(1L, "owner")).thenReturn(SurveyAnalyticsResponse.builder().surveyTitle("報表").build());

        mockMvc.perform(get("/api/analytics/surveys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.surveyTitle").value("報表"));
    }

    @Test
    @DisplayName("非建立者查詢：Service 拋出 AccessDeniedException → 403")
    void notOwner_Returns403() throws Exception {
        when(analyticsService.getSurveyAnalytics(1L, "owner")).thenThrow(new AccessDeniedException("無權限"));

        mockMvc.perform(get("/api/analytics/surveys/1")).andExpect(status().isForbidden());
    }
}
