/**
 * ============================================================================
 * 檔案名稱: UserServiceTest.java
 * 測試對象: UserService (個人資料查詢 / 更新、改密碼、管理員會員名冊、停權、軟刪除)
 * 測試方式: JUnit 5 + Mockito；UserRepository、PasswordEncoder 以 Mock 取代
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.dto.user.PasswordChangeRequest;
import org.jasonproduction.surveyflow.dto.user.UserProfileResponse;
import org.jasonproduction.surveyflow.dto.user.UserProfileUpdateRequest;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserService userService;
    private User member;

    /**
     * 建立受測物件與一位啟用中之一般會員 (id 1、帳號 testmember)
     */
    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, eventPublisher);
        member = User.builder().id(1L).username("testmember").email("m@example.com").passwordHash("old_hash")
                .fullName("林安安").phone("0911111111").identityCard("A123456789")
                .role(RoleType.ROLE_USER).status(UserStatus.ACTIVE).build();
    }

    @Nested
    @DisplayName("個人資料")
    class Profile {

        @Test
        @DisplayName("查詢個人資料：欄位完整轉換，狀態以字串回傳，且不含密碼雜湊")
        void getProfile_MapsFields() {
            // Given
            when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(member));

            // When
            UserProfileResponse profile = userService.getProfile("testmember");

            // Then
            assertEquals(1L, profile.getId());
            assertEquals("林安安", profile.getFullName());
            assertEquals("A123456789", profile.getIdentityCard());
            assertEquals(RoleType.ROLE_USER, profile.getRole());
            assertEquals("ACTIVE", profile.getStatus());
        }

        @Test
        @DisplayName("查無帳號：拋出 IllegalArgumentException")
        void getProfile_Unknown_Throws() {
            when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> userService.getProfile("ghost"));
        }

        @Test
        @DisplayName("更新個人資料：姓名與手機去除前後空白後寫入並儲存")
        void updateProfile_TrimsAndSaves() {
            // Given
            when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(member));
            when(userRepository.save(member)).thenReturn(member);
            UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                    .fullName(" 林安安二 ").phone(" 0922222222 ").identityCard(null).build();

            // When
            UserProfileResponse profile = userService.updateProfile("testmember", request);

            // Then
            assertEquals("林安安二", profile.getFullName());
            assertEquals("0922222222", profile.getPhone());
            assertEquals("A123456789", profile.getIdentityCard(), "身分證未帶時保留原值");
        }
    }

    @Nested
    @DisplayName("changePassword 修改密碼")
    class ChangePassword {

        @Test
        @DisplayName("目前密碼正確：以新密碼之雜湊取代並儲存")
        void correctOldPassword_UpdatesHash() {
            // Given
            when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("Old123!", "old_hash")).thenReturn(true);
            when(passwordEncoder.encode("New123!")).thenReturn("new_hash");

            // When
            userService.changePassword("testmember", PasswordChangeRequest.builder().oldPassword("Old123!").newPassword("New123!").build());

            // Then
            assertEquals("new_hash", member.getPasswordHash());
            verify(userRepository).save(member);
        }

        @Test
        @DisplayName("目前密碼錯誤：拋出 IllegalArgumentException，密碼不變也不儲存")
        void wrongOldPassword_Throws() {
            // Given
            when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("Wrong!", "old_hash")).thenReturn(false);

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> userService.changePassword("testmember",
                    PasswordChangeRequest.builder().oldPassword("Wrong!").newPassword("New123!").build()));
            assertEquals("old_hash", member.getPasswordHash());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("管理員會員管理")
    class AdminOperations {

        @Test
        @DisplayName("會員名冊：分頁內容轉為個人資料 DTO，手機與身分證為遮罩值，信箱保留完整")
        void getAllUsersForAdmin_MapsPageWithMaskedPii() {
            // Given
            PageRequest pageable = PageRequest.of(0, 10);
            when(userRepository.findAllUsersForAdminNative(pageable)).thenReturn(new PageImpl<>(List.of(member)));

            // When
            UserProfileResponse row = userService.getAllUsersForAdmin(pageable).getContent().get(0);

            // Then
            assertEquals("testmember", row.getUsername());
            assertEquals("0911***111", row.getPhone());
            assertEquals("A******789", row.getIdentityCard());
            assertEquals("m@example.com", row.getEmail());
        }

        @Test
        @DisplayName("變更狀態：字串不分大小寫轉為列舉後寫入")
        void updateUserStatus_ParsesEnum() {
            // Given
            when(userRepository.findByIdNative(1L)).thenReturn(Optional.of(member));

            // When
            userService.updateUserStatus(1L, "suspended");

            // Then
            assertEquals(UserStatus.SUSPENDED, member.getStatus());
            verify(userRepository).save(member);
            verify(eventPublisher).publishEvent(new AuditEvent(AuditAction.USER_STATUS_CHANGED, null, AuditEvent.TARGET_USER, "1", "testmember → SUSPENDED"));
        }

        @Test
        @DisplayName("變更狀態為非法值：拋出 IllegalArgumentException (全域處理器回 400)")
        void updateUserStatus_InvalidValue_Throws() {
            when(userRepository.findByIdNative(1L)).thenReturn(Optional.of(member));

            assertThrows(IllegalArgumentException.class, () -> userService.updateUserStatus(1L, "BANNED"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("變更狀態但查無會員：拋出 IllegalArgumentException")
        void updateUserStatus_Unknown_Throws() {
            when(userRepository.findByIdNative(9L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> userService.updateUserStatus(9L, "ACTIVE"));
        }

        @Test
        @DisplayName("刪除一般會員：軟刪除 (寫入刪除時間、狀態 DELETED)")
        void deleteUser_SoftDeletes() {
            // Given
            when(userRepository.findByIdNative(1L)).thenReturn(Optional.of(member));

            // When
            userService.deleteUserByAdmin(1L);

            // Then
            assertNotNull(member.getDeletedAt());
            assertEquals(UserStatus.DELETED, member.getStatus());
        }

        @Test
        @DisplayName("刪除管理員帳號：受保護，拋出 IllegalArgumentException")
        void deleteAdmin_IsProtected() {
            // Given
            User admin = User.builder().id(2L).username("admin").role(RoleType.ROLE_ADMIN).build();
            when(userRepository.findByIdNative(2L)).thenReturn(Optional.of(admin));

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> userService.deleteUserByAdmin(2L));
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("更新個人資料：身分證有值時去除空白後更新，手機為 null 時保留原值")
    void updateProfile_WithIdentityCard() {
        when(userRepository.findByUsernameNative("testmember")).thenReturn(Optional.of(member));
        when(userRepository.save(member)).thenReturn(member);

        userService.updateProfile("testmember", UserProfileUpdateRequest.builder().fullName("林安安").phone(null).identityCard(" B223456789 ").build());

        assertEquals("B223456789", member.getIdentityCard());
        assertEquals("0911111111", member.getPhone());
    }

    @Test
    @DisplayName("更新個人資料、修改密碼、刪除會員但查無對象：拋出 IllegalArgumentException")
    void notFoundCases() {
        when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByIdNative(9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile("ghost", UserProfileUpdateRequest.builder().fullName("x").build()));
        assertThrows(IllegalArgumentException.class, () -> userService.changePassword("ghost", PasswordChangeRequest.builder().oldPassword("a").newPassword("b").build()));
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUserByAdmin(9L));
    }
}
