/**
 * ============================================================================
 * 檔案名稱: UserServiceTest.java
 * 所屬模組: Test 測試層 - 會員業務單元測試
 * 責任描述: 採用 JUnit 5 與 Mockito 測試會員個人檔案查詢、資料更新、密碼變更與軟刪除安全機制
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: UserService
 *   - 模擬對象: UserRepository, PasswordEncoder, ModelMapper
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.user.PasswordChangeRequest;
import com.surveyflow.dto.user.UserProfileResponse;
import com.surveyflow.dto.user.UserProfileUpdateRequest;
import com.surveyflow.entity.User;
import com.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private ModelMapper modelMapper = new com.surveyflow.config.ModelMapperConfig().modelMapper();

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testmember")
                .email("test@example.com")
                .passwordHash("hashed_old_password")
                .fullName("林小安")
                .phone("0912345678")
                .identityCard("A123456789")
                .role(RoleType.ROLE_USER)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminUser = User.builder()
                .id(99L)
                .username("sysadmin")
                .email("admin@example.com")
                .role(RoleType.ROLE_ADMIN)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("查詢會員個人檔案成功並正確透過 ModelMapper 映射")
    void getProfile_Success() {
        when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = userService.getProfile("testmember");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("testmember", response.getUsername());
        assertEquals("林小安", response.getFullName());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    @DisplayName("更新會員個人檔案資料成功")
    void updateProfile_Success() {
        when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .fullName("林安安")
                .phone("0988776655")
                .build();

        UserProfileResponse response = userService.updateProfile("testmember", request);

        assertNotNull(response);
        assertEquals("林安安", response.getFullName());
        assertEquals("0988776655", response.getPhone());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("會員修改密碼：原密碼正確時應成功雜湊儲存新密碼")
    void changePassword_Success() {
        when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPass123!", "hashed_old_password")).thenReturn(true);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("hashed_new_password");

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("OldPass123!")
                .newPassword("NewPass123!")
                .build();

        userService.changePassword("testmember", request);

        assertEquals("hashed_new_password", sampleUser.getPasswordHash());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("會員修改密碼：原密碼錯誤應拋出 IllegalArgumentException")
    void changePassword_WrongOldPassword() {
        when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPass!", "hashed_old_password")).thenReturn(false);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("WrongPass!")
                .newPassword("NewPass123!")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword("testmember", request));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("原密碼"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("管理員禁止軟刪除系統最高管理員帳號")
    void deleteUser_AdminProtected() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUserByAdmin(99L));
        assertTrue(ex.getMessage().contains("最高管理員"));
        verify(userRepository, never()).save(any());
    }
}
