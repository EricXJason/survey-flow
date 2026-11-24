/**
 * ============================================================================
 * 檔案名稱: CustomUserDetailsServiceTest.java
 * 測試對象: CustomUserDetailsService (將 users 資料轉為 Spring Security UserDetails)
 * 測試方式: UserRepository 以 Mock 取代
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    /**
     * 建立指定角色與狀態之會員
     * @param role 角色
     * @param status 帳號狀態
     * @return 會員實體
     */
    private User user(RoleType role, UserStatus status) {
        return User.builder().id(1L).username("testuser").passwordHash("hash").fullName("測試")
                .email("t@example.com").role(role).status(status).build();
    }

    @Test
    @DisplayName("啟用中之會員：帳號、密碼雜湊與角色正確轉換，且 enabled 為 true")
    void activeUser_IsEnabled() {
        // Given
        when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(user(RoleType.ROLE_ADMIN, UserStatus.ACTIVE)));

        // When
        UserDetails details = new CustomUserDetailsService(userRepository).loadUserByUsername("testuser");

        // Then
        assertEquals("testuser", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
        assertTrue(details.isEnabled());
    }

    @Test
    @DisplayName("停權會員：enabled 為 false (JwtAuthenticationFilter 據此拒絕)")
    void suspendedUser_IsDisabled() {
        // Given
        when(userRepository.findByUsernameNative("testuser")).thenReturn(Optional.of(user(RoleType.ROLE_USER, UserStatus.SUSPENDED)));

        // Then
        assertFalse(new CustomUserDetailsService(userRepository).loadUserByUsername("testuser").isEnabled());
    }

    @Test
    @DisplayName("查無帳號：拋出 UsernameNotFoundException")
    void unknownUser_Throws() {
        // Given
        when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());

        // Then
        assertThrows(UsernameNotFoundException.class, () -> new CustomUserDetailsService(userRepository).loadUserByUsername("ghost"));
    }
}
