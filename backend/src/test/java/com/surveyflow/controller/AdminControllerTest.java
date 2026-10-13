/**
 * ============================================================================
 * 檔案名稱: AdminControllerTest.java
 * 所屬模組: Test 測試層 - 管理者端點 Web 測試
 * 責任描述: 採用 MockMvc standaloneSetup 測試 /api/admin/** 管理者端點之業務回應
 * 架構分層: Test Layer / Web Test
 * 聯動關係:
 *   - 測試目標: AdminController
 *   - 模擬對象: UserRepository, SurveyRepository, SubmissionRepository, SurveyService, AnalyticsService
 * ============================================================================
 */

package com.surveyflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surveyflow.common.GlobalExceptionHandler;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import com.surveyflow.repository.UserRepository;
import com.surveyflow.service.AnalyticsService;
import com.surveyflow.service.SurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SurveyService surveyService;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(messageConverter)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("管理端點測試: 請求 /api/admin/analytics/overview 應回傳全站總體營運指標")
    void getPlatformOverview_Returns200() throws Exception {
        when(userRepository.count()).thenReturn(25L);
        when(surveyRepository.count()).thenReturn(10L);
        when(submissionRepository.countTotalSubmissionsNative()).thenReturn(520L);

        mockMvc.perform(get("/api/admin/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(25))
                .andExpect(jsonPath("$.data.totalSurveys").value(10))
                .andExpect(jsonPath("$.data.totalSubmissions").value(520));
    }
}
