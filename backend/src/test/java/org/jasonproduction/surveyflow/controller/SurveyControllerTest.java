/**
 * ============================================================================
 * 檔案名稱: SurveyControllerTest.java
 * 測試對象: SurveyController (問卷 CRUD、大廳、公開填答 / 預覽、開放設定 PUT 與 PATCH)
 * 測試方式: MockMvc standalone + SurveyService Mock
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.dto.survey.SurveyCreateRequest;
import org.jasonproduction.surveyflow.dto.survey.SurveyResponse;
import org.jasonproduction.surveyflow.dto.survey.SurveySettingsRequest;
import org.jasonproduction.surveyflow.dto.survey.SurveySummaryResponse;
import org.jasonproduction.surveyflow.dto.survey.SurveyUpdateRequest;
import org.jasonproduction.surveyflow.service.SurveyService;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SurveyControllerTest {

    @Mock private SurveyService surveyService;

    private MockMvc mockMvc;
    private final SurveyResponse sample = SurveyResponse.builder().id(1L).title("問卷").build();

    /**
     * 建立受測 Controller (預設未登入，需要登入之測試自行呼叫 loginAs)
     */
    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new SurveyController(surveyService));
    }

    /**
     * 清除模擬登入狀態
     */
    @AfterEach
    void tearDown() {
        ControllerTestSupport.logout();
    }

    @Test
    @DisplayName("POST /api/user/surveys：以登入者為建立者建立問卷")
    void createSurvey() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
        when(surveyService.createSurvey(any(SurveyCreateRequest.class), eq("owner"))).thenReturn(sample);

        mockMvc.perform(post("/api/user/surveys").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(SurveyCreateRequest.builder().title("問卷").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("POST /api/user/surveys：標題空白時 400")
    void createSurvey_BlankTitle_Returns400() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");

        mockMvc.perform(post("/api/user/surveys").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(SurveyCreateRequest.builder().title(" ").build())))
                .andExpect(status().isBadRequest());
        verify(surveyService, never()).createSurvey(any(), any());
    }

    @Test
    @DisplayName("GET /api/public/surveys/lobby：分類與關鍵字傳入 Service")
    void lobby() throws Exception {
        when(surveyService.searchLobbySurveys(eq("教育"), eq("滿意"), any(Pageable.class)))
                .thenReturn(ControllerTestSupport.pageOf(List.of(SurveySummaryResponse.builder().title("大廳問卷").build())));

        mockMvc.perform(get("/api/public/surveys/lobby").param("category", "教育").param("keyword", "滿意"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("大廳問卷"));
    }

    @Test
    @DisplayName("GET /api/public/surveys/{id}：匿名受訪者 → 非預覽、帳號為 null")
    void publicSurvey_Anonymous() throws Exception {
        when(surveyService.getPublicSurvey(eq(1L), eq(false), isNull())).thenReturn(sample);

        mockMvc.perform(get("/api/public/surveys/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/public/surveys/{id}?preview=true：已登入時帶入帳號供擁有者核對")
    void publicSurvey_PreviewByOwner() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
        when(surveyService.getPublicSurvey(1L, true, "owner")).thenReturn(sample);

        mockMvc.perform(get("/api/public/surveys/1").param("preview", "true")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/public/surveys/shared/{shareToken}：路徑中的分享代碼原樣傳入 Service")
    void sharedSurvey_PassesToken() throws Exception {
        when(surveyService.getSharedSurvey("kPL6FRh2idiOuIO-tF3naA")).thenReturn(sample);

        mockMvc.perform(get("/api/public/surveys/shared/kPL6FRh2idiOuIO-tF3naA")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/user/surveys 與 /api/user/surveys/{id}：個人清單與詳情")
    void mySurveysAndDetail() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
        when(surveyService.getUserSurveys(eq("owner"), any(Pageable.class)))
                .thenReturn(ControllerTestSupport.pageOf(List.of(SurveySummaryResponse.builder().userSurveyNumber(1).build())));
        when(surveyService.getSurveyDetail(1L, "owner")).thenReturn(sample);

        mockMvc.perform(get("/api/user/surveys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userSurveyNumber").value(1));
        mockMvc.perform(get("/api/user/surveys/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/user/surveys/{id}：更新問卷內容")
    void updateSurvey() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
        when(surveyService.updateSurvey(eq(1L), any(SurveyUpdateRequest.class), eq("owner"))).thenReturn(sample);

        mockMvc.perform(put("/api/user/surveys/1").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(SurveyUpdateRequest.builder().title("新標題").build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /settings 缺少必填之 isOpen：400 (整體覆蓋必須提供完整設定)")
    void replaceSettings_MissingField_Returns400() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");

        mockMvc.perform(put("/api/user/surveys/1/settings").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(SurveySettingsRequest.builder().isPublicToLobby(true).build())))
                .andExpect(status().isBadRequest());
        verify(surveyService, never()).replaceSurveySettings(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /settings 完整設定：委派整體覆蓋")
    void replaceSettings() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
        when(surveyService.replaceSurveySettings(eq(1L), any(SurveySettingsRequest.class), eq("owner"))).thenReturn(sample);

        mockMvc.perform(put("/api/user/surveys/1/settings").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(SurveySettingsRequest.builder().isOpen(true).isPublicToLobby(false).build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /settings 只帶一個欄位：允許 (不套用 @NotNull)，其餘欄位以 null 傳入 Service")
    void patchSettings_PartialBody() throws Exception {
        // Given
        ControllerTestSupport.loginAs("owner", "ROLE_USER");
        ArgumentCaptor<SurveySettingsRequest> captor = ArgumentCaptor.forClass(SurveySettingsRequest.class);
        when(surveyService.patchSurveySettings(eq(1L), captor.capture(), eq("owner"))).thenReturn(sample);

        // When
        mockMvc.perform(patch("/api/user/surveys/1/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPublicToLobby\": false}"))
                .andExpect(status().isOk());

        // Then
        assertFalse(captor.getValue().getIsPublicToLobby());
        assertNull(captor.getValue().getIsOpen());
    }

    @Test
    @DisplayName("DELETE /api/user/surveys/{id}：以登入者帳號軟刪除")
    void deleteSurvey() throws Exception {
        ControllerTestSupport.loginAs("owner", "ROLE_USER");

        mockMvc.perform(delete("/api/user/surveys/1")).andExpect(status().isOk());

        verify(surveyService).deleteSurvey(1L, "owner");
    }
}
