/**
 * ============================================================================
 * 檔案名稱: PublicControllerTest.java
 * 所屬模組: Test 測試層 - 公開端點 Web 整合測試
 * 責任描述: 採用 MockMvc standaloneSetup 測試 /api/public/** 公開路由之存取與驗證
 * 架構分層: Test Layer / Web Test
 * 聯動關係:
 *   - 測試目標: AuthController, SurveyController, SubmissionController
 *   - 模擬對象: AuthService, SurveyService, SubmissionService
 * ============================================================================
 */

package com.surveyflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surveyflow.common.exception.GlobalExceptionHandler;
import com.surveyflow.dto.auth.LoginRequest;
import com.surveyflow.dto.submission.SubmissionRequest;
import com.surveyflow.dto.submission.SubmissionResponse;
import com.surveyflow.service.AuthService;
import com.surveyflow.service.SubmissionService;
import com.surveyflow.service.SurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @Mock
    private SurveyService surveyService;

    @Mock
    private SubmissionService submissionService;

    @InjectMocks
    private AuthController authController;

    @InjectMocks
    private SurveyController surveyController;

    @InjectMocks
    private SubmissionController submissionController;

    /**
     * 初始化 MockMvc 與 Standalone 控制器環境，掛載全域例外處理器
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(authController, surveyController, submissionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(messageConverter)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    /**
     * 測試問卷大廳公開分頁清單檢索端點
     * 驗證情境: 呼叫 GET /api/public/surveys/lobby 時，預期成功取得 HTTP 200 與分頁資料
     */
    @Test
    @DisplayName("公開端點測試: 請求大廳列表 /api/public/surveys/lobby 應成功回傳 HTTP 200 與分頁資料")
    void getLobbySurveys_Returns200() throws Exception {
        Page<com.surveyflow.dto.survey.SurveySummaryResponse> emptyPage =
                new PageImpl<>(new ArrayList<>(), org.springframework.data.domain.PageRequest.of(0, 10), 0);
        when(surveyService.searchLobbySurveys(any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/public/surveys/lobby")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 測試登入密碼為空之驗證阻斷
     * 驗證情境: 傳入密碼為空之 LoginRequest 時，預期由 GlobalExceptionHandler 攔截並回傳 HTTP 400
     */
    @Test
    @DisplayName("參數校驗測試: 登入時密碼為空應觸發 GlobalExceptionHandler 回傳 HTTP 400")
    void login_EmptyPassword_Returns400() throws Exception {
        LoginRequest invalidRequest = new LoginRequest("user", "");

        mockMvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 測試受訪者送出問卷作答端點
     * 驗證情境: 傳入正確之 SubmissionRequest 時，預期成功建立填答並回傳包含 submissionId 之確認回應
     */
    @Test
    @DisplayName("填答提交測試: 送出正確 SubmissionRequest 應回傳 HTTP 200")
    void submitSurvey_ValidRequest_Returns200() throws Exception {
        SubmissionRequest request = SubmissionRequest.builder()
                .surveyId(10L)
                .durationSeconds(85)
                .build();

        SubmissionResponse mockResponse = SubmissionResponse.builder()
                .submissionId(999L)
                .surveyId(10L)
                .message("感謝您的填答！")
                .submittedAt(LocalDateTime.now())
                .build();

        when(submissionService.submitSurvey(any(SubmissionRequest.class), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/public/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(999L));
    }
}
