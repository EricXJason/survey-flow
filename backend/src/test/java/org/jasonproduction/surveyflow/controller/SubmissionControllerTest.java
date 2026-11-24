/**
 * ============================================================================
 * 檔案名稱: SubmissionControllerTest.java
 * 測試對象: SubmissionController (公開填答提交端點、傳入解析後之客戶端 IP、參數驗證)
 * 測試方式: MockMvc standalone + SubmissionService Mock
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.common.web.ClientIpResolver;
import org.jasonproduction.surveyflow.dto.submission.SubmissionRequest;
import org.jasonproduction.surveyflow.dto.submission.SubmissionResponse;
import org.jasonproduction.surveyflow.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerTest {

    @Mock private SubmissionService submissionService;

    private MockMvc mockMvc;
    private String validBody;

    /**
     * 建立受測 Controller 與一份合法之填答內容 (問卷 1)
     * @throws Exception JSON 序列化失敗
     */
    @BeforeEach
    void setUp() throws Exception {
        mockMvc = ControllerTestSupport.mockMvcFor(new SubmissionController(submissionService, new ClientIpResolver(1)));
        validBody = ControllerTestSupport.json(SubmissionRequest.builder().surveyId(1L).build());
    }

    /**
     * 清除模擬登入狀態
     */
    @AfterEach
    void tearDown() {
        ControllerTestSupport.logout();
    }

    @Test
    @DisplayName("經過一層可信任代理：以 ClientIpResolver 解析之 IP (X-Forwarded-For 最右邊一筆) 傳給 Service")
    void forwardedFor_PassesResolvedIp() throws Exception {
        when(submissionService.submitSurvey(any(SubmissionRequest.class), eq("203.0.113.5")))
                .thenReturn(SubmissionResponse.builder().submissionId(9L).build());

        mockMvc.perform(post("/api/public/submissions").contentType(MediaType.APPLICATION_JSON).content(validBody)
                        .header("X-Forwarded-For", "198.51.100.66, 203.0.113.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(9));
    }

    @Test
    @DisplayName("缺少 surveyId：400，不呼叫 Service")
    void missingSurveyId_Returns400() throws Exception {
        mockMvc.perform(post("/api/public/submissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(submissionService, never()).submitSurvey(any(), any());
    }

    @Test
    @DisplayName("問卷已截止：Service 拋出 IllegalArgumentException → 400 並回傳原因")
    void closedSurvey_Returns400WithReason() throws Exception {
        when(submissionService.submitSurvey(any(SubmissionRequest.class), any()))
                .thenThrow(new IllegalArgumentException("此問卷已於 2026/11/01 00:00 截止填答，已無法再進行填寫"));

        mockMvc.perform(post("/api/public/submissions").contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("此問卷已於 2026/11/01 00:00 截止填答，已無法再進行填寫"));
    }
}
