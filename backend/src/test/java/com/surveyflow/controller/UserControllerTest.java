/**
 * ============================================================================
 * 檔案名稱: UserControllerTest.java
 * 所屬模組: Test 測試層 - 會員個人端點 Web 測試
 * 責任描述: 採用 MockMvc standaloneSetup 測試 /api/user/** 會員端點之請求、校驗與回應
 * 架構分層: Test Layer / Web Test
 * 聯動關係:
 *   - 測試目標: UserController
 *   - 模擬對象: UserService
 * ============================================================================
 */

package com.surveyflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surveyflow.common.GlobalExceptionHandler;
import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.user.PasswordChangeRequest;
import com.surveyflow.dto.user.UserProfileResponse;
import com.surveyflow.dto.user.UserProfileUpdateRequest;
import com.surveyflow.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private Principal mockPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockPrincipal = () -> "testmember";
    }

    @Test
    @DisplayName("GET /api/user/profile 成功取得個人檔案")
    void getProfile_Success() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(1L)
                .username("testmember")
                .fullName("林小安")
                .email("test@example.com")
                .role(RoleType.ROLE_USER)
                .build();

        when(userService.getProfile("testmember")).thenReturn(profile);

        mockMvc.perform(get("/api/user/profile")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("testmember"))
                .andExpect(jsonPath("$.data.fullName").value("林小安"));
    }

    @Test
    @DisplayName("PUT /api/user/profile 成功更新個人檔案")
    void updateProfile_Success() throws Exception {
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .fullName("林安安")
                .phone("0912345678")
                .build();

        UserProfileResponse updatedProfile = UserProfileResponse.builder()
                .id(1L)
                .username("testmember")
                .fullName("林安安")
                .phone("0912345678")
                .build();

        when(userService.updateProfile(eq("testmember"), any(UserProfileUpdateRequest.class)))
                .thenReturn(updatedProfile);

        mockMvc.perform(put("/api/user/profile")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("林安安"));
    }

    @Test
    @DisplayName("PUT /api/user/change-password 成功修改密碼")
    void changePassword_Success() throws Exception {
        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("OldPass123!")
                .newPassword("NewPass123!")
                .build();

        mockMvc.perform(put("/api/user/change-password")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).changePassword(eq("testmember"), any(PasswordChangeRequest.class));
    }
}
