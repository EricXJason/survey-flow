/**
 * ============================================================================
 * 檔案名稱: AdminControllerTest.java
 * 所屬模組: Test 測試層 - 管理者端點 Web 測試
 * 責任描述: 採用 MockMvc standaloneSetup 測試 /api/admin/** 管理者端點之業務回應，驗證分層隔離
 * 架構分層: Test Layer / Web Test
 * 聯動關係:
 *   - 測試目標: AdminController
 *   - 模擬對象: AdminService
 * ============================================================================
 */

package com.surveyflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surveyflow.common.exception.GlobalExceptionHandler;
import com.surveyflow.service.AdminService;
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

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    /**
     * 初始化 MockMvc 測試環境，掛載全域例外處理器與分頁參數解析器
     */
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

    /**
     * 測試取得全站總體營運指標端點
     * 驗證情境: 呼叫 GET /api/admin/analytics/overview 時，預期成功取得包含會員總數、問卷總數與填答總數之 JSON 回應
     */
    @Test
    @DisplayName("管理端點測試: 請求 /api/admin/analytics/overview 應回傳全站總體營運指標")
    void getPlatformOverview_Returns200() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", 25L);
        stats.put("totalSurveys", 10L);
        stats.put("totalSubmissions", 520L);

        when(adminService.getPlatformOverview()).thenReturn(stats);

        mockMvc.perform(get("/api/admin/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(25))
                .andExpect(jsonPath("$.data.totalSurveys").value(10))
                .andExpect(jsonPath("$.data.totalSubmissions").value(520));
    }
}
