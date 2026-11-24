/**
 * ============================================================================
 * 檔案名稱: AdminControllerTest.java
 * 測試對象: AdminController (管理員之會員管理、全站問卷、營運總覽與報表端點)
 * 測試方式: MockMvc standalone + AdminService Mock；以 ControllerTestSupport.loginAs 模擬管理員
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import org.jasonproduction.surveyflow.dto.survey.SurveySummaryResponse;
import org.jasonproduction.surveyflow.dto.user.UserProfileResponse;
import org.jasonproduction.surveyflow.service.AdminService;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private AdminService adminService;

    private MockMvc mockMvc;

    /**
     * 建立受測 Controller 並模擬 admin 已登入
     */
    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new AdminController(adminService));
        ControllerTestSupport.loginAs("admin", "ROLE_ADMIN");
    }

    /**
     * 清除模擬登入狀態
     */
    @AfterEach
    void tearDown() {
        ControllerTestSupport.logout();
    }

    @Test
    @DisplayName("GET /api/admin/users：回傳會員分頁")
    void getAllUsers() throws Exception {
        when(adminService.getAllUsers(any(Pageable.class)))
                .thenReturn(ControllerTestSupport.pageOf(List.of(UserProfileResponse.builder().username("testmember").build())));

        mockMvc.perform(get("/api/admin/users?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("testmember"));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{id}/status：以查詢參數傳入新狀態")
    void updateUserStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/status").param("status", "SUSPENDED"))
                .andExpect(status().isOk());

        verify(adminService).updateUserStatus(1L, "SUSPENDED");
    }

    @Test
    @DisplayName("PUT /api/admin/users/{id}/status：動詞不支援 → 405 (狀態切換屬局部修改，只開放 PATCH)")
    void updateUserStatus_WithPut_Returns405() throws Exception {
        mockMvc.perform(put("/api/admin/users/1/status").param("status", "SUSPENDED"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{id}：委派軟刪除")
    void deleteUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/5")).andExpect(status().isOk());

        verify(adminService).deleteUser(5L);
    }

    @Test
    @DisplayName("GET /api/admin/surveys 與 DELETE /api/admin/surveys/{id}：全站問卷清單與強制刪除 (帶入管理員帳號)")
    void surveys() throws Exception {
        when(adminService.getAllSurveys(any(Pageable.class)))
                .thenReturn(ControllerTestSupport.pageOf(List.of(SurveySummaryResponse.builder().title("全站問卷").build())));

        mockMvc.perform(get("/api/admin/surveys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("全站問卷"));
        mockMvc.perform(delete("/api/admin/surveys/7")).andExpect(status().isOk());

        verify(adminService).forceDeleteSurvey(7L, "admin");
    }

    @Test
    @DisplayName("GET /api/admin/analytics/overview 與 /analytics/surveys/{id}：營運總覽與單一問卷報表")
    void analytics() throws Exception {
        when(adminService.getPlatformOverview()).thenReturn(Map.of("totalUsers", 12));
        when(adminService.getSurveyAnalytics(3L, "admin")).thenReturn(SurveyAnalyticsResponse.builder().surveyId(3L).build());

        mockMvc.perform(get("/api/admin/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(12));
        mockMvc.perform(get("/api/admin/analytics/surveys/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.surveyId").value(3));
    }
}
