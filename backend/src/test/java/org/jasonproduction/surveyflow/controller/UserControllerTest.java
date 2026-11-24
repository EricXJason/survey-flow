/**
 * ============================================================================
 * 檔案名稱: UserControllerTest.java
 * 測試對象: UserController (個人資料查詢 / 更新、修改密碼之路由與參數驗證)
 * 測試方式: MockMvc standalone + UserService Mock；以 ControllerTestSupport.loginAs 模擬登入者
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.jasonproduction.surveyflow.dto.user.PasswordChangeRequest;
import org.jasonproduction.surveyflow.dto.user.UserProfileResponse;
import org.jasonproduction.surveyflow.dto.user.UserProfileUpdateRequest;
import org.jasonproduction.surveyflow.service.UserService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;

    private MockMvc mockMvc;

    /**
     * 建立受測 Controller 並模擬 testmember 已登入
     */
    @BeforeEach
    void setUp() {
        mockMvc = ControllerTestSupport.mockMvcFor(new UserController(userService));
        ControllerTestSupport.loginAs("testmember", "ROLE_USER");
    }

    /**
     * 清除模擬登入狀態
     */
    @AfterEach
    void tearDown() {
        ControllerTestSupport.logout();
    }

    @Test
    @DisplayName("GET /api/user/profile：以登入者帳號查詢個人資料")
    void getProfile() throws Exception {
        when(userService.getProfile("testmember")).thenReturn(UserProfileResponse.builder().username("testmember").fullName("林安安").build());

        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("林安安"));
    }

    @Test
    @DisplayName("PUT /api/user/profile：資料合法時委派 Service 更新")
    void updateProfile() throws Exception {
        when(userService.updateProfile(eq("testmember"), any(UserProfileUpdateRequest.class)))
                .thenReturn(UserProfileResponse.builder().fullName("林安安").build());

        mockMvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(UserProfileUpdateRequest.builder().fullName("林安安").phone("0912345678").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("林安安"));
    }

    @Test
    @DisplayName("PUT /api/user/profile：手機格式錯誤時 400，不呼叫 Service")
    void updateProfile_InvalidPhone_Returns400() throws Exception {
        mockMvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(UserProfileUpdateRequest.builder().fullName("林安安").phone("12345").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    @DisplayName("PUT /api/user/password：資料合法時委派 Service 修改密碼")
    void changePassword() throws Exception {
        mockMvc.perform(put("/api/user/password").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(PasswordChangeRequest.builder().oldPassword("Old123!").newPassword("NewPass1!").build())))
                .andExpect(status().isOk());

        verify(userService).changePassword(eq("testmember"), any(PasswordChangeRequest.class));
    }

    @Test
    @DisplayName("PUT /api/user/password：目前密碼錯誤時 Service 拋出 IllegalArgumentException → 400")
    void changePassword_WrongOldPassword_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("目前原密碼輸入不正確，無法變更密碼"))
                .when(userService).changePassword(eq("testmember"), any(PasswordChangeRequest.class));

        mockMvc.perform(put("/api/user/password").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(PasswordChangeRequest.builder().oldPassword("wrong").newPassword("NewPass1!").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("目前原密碼輸入不正確，無法變更密碼"));
    }

    @Test
    @DisplayName("PUT /api/user/password：新密碼少於 6 碼時 400")
    void changePassword_ShortPassword_Returns400() throws Exception {
        mockMvc.perform(put("/api/user/password").contentType(MediaType.APPLICATION_JSON)
                        .content(ControllerTestSupport.json(PasswordChangeRequest.builder().oldPassword("Old1!").newPassword("123").build())))
                .andExpect(status().isBadRequest());
    }
}
