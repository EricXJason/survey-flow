/**
 * ============================================================================
 * 檔案名稱: UserRepositoryIT.java
 * 測試對象: UserRepository 之原生 SQL (真實 MySQL + 種子資料)
 * 測試方式: 整合測試；類別標註 @Transactional，每個測試結束後自動回滾，不污染其他測試
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class UserRepositoryIT extends IntegrationTestSupport {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 建構子注入 (junit-platform.properties 已設定自動注入)
     * @param userRepository 受測 Repository
     * @param passwordEncoder 用於驗證種子密碼
     */
    UserRepositoryIT(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 新增一位會員並立即寫入資料庫 (交易結束後回滾)
     * @param username 帳號 (同時作為信箱前綴)
     * @param role 角色
     * @return 已寫入之會員
     */
    private User saveUser(String username, RoleType role) {
        return userRepository.saveAndFlush(User.builder().username(username).email(username + "@it.test")
                .passwordHash("hash").fullName("整合測試").role(role).status(UserStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("findByUsernameNative：種子管理員之角色、狀態 (VARCHAR → 列舉) 與 Argon2id 密碼皆正確")
    void findByUsername_SeedAdmin() {
        User admin = userRepository.findByUsernameNative("admin").orElseThrow();

        assertEquals(RoleType.ROLE_ADMIN, admin.getRole());
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
        assertTrue(passwordEncoder.matches("Admin123!", admin.getPasswordHash()));
    }

    @Test
    @DisplayName("findByEmailNative / findByIdNative：可依信箱與 ID 查到同一位會員")
    void findByEmailAndId() {
        User byEmail = userRepository.findByEmailNative("testuser@surveyflow.com").orElseThrow();

        assertEquals("testuser", byEmail.getUsername());
        assertEquals(byEmail.getUsername(), userRepository.findByIdNative(byEmail.getId()).orElseThrow().getUsername());
    }

    @Test
    @DisplayName("軟刪除之會員：三種查詢都查不到 (deleted_at IS NULL 條件生效)")
    void softDeletedUser_IsInvisible() {
        // Given
        User user = saveUser("it_deleted", RoleType.ROLE_USER);
        user.softDelete(LocalDateTime.now());
        userRepository.saveAndFlush(user);

        // Then
        assertTrue(userRepository.findByUsernameNative("it_deleted").isEmpty());
        assertTrue(userRepository.findByEmailNative("it_deleted@it.test").isEmpty());
        assertTrue(userRepository.findByIdNative(user.getId()).isEmpty());
    }

    @Test
    @DisplayName("findAllUsersForAdminNative：排除管理員，分頁 countQuery 與實際筆數一致，可依 created_at 排序")
    void adminList_ExcludesAdminsAndPagesCorrectly() {
        // Given
        long before = userRepository.findAllUsersForAdminNative(PageRequest.of(0, 1)).getTotalElements();
        saveUser("it_member", RoleType.ROLE_USER);
        saveUser("it_admin", RoleType.ROLE_ADMIN);

        // When
        Page<User> page = userRepository.findAllUsersForAdminNative(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "created_at")));

        // Then：只多 1 位 (新增之管理員不列入)
        assertEquals(before + 1, page.getTotalElements());
        assertTrue(page.getContent().stream().noneMatch(u -> u.getRole() == RoleType.ROLE_ADMIN));
    }

    @Test
    @DisplayName("countActiveUsersNative：新增會員 +1，軟刪除後 -1")
    void countActiveUsers_Delta() {
        long before = userRepository.countActiveUsersNative();
        User user = saveUser("it_count", RoleType.ROLE_USER);
        assertEquals(before + 1, userRepository.countActiveUsersNative());

        user.softDelete(LocalDateTime.now());
        userRepository.saveAndFlush(user);
        assertEquals(before, userRepository.countActiveUsersNative());
    }

    @Test
    @DisplayName("帳號重複：資料庫 UNIQUE KEY 擋下並拋出 DataIntegrityViolationException (併發註冊之最後防線)")
    void duplicateUsername_RejectedByUniqueKey() {
        assertThrows(DataIntegrityViolationException.class, () -> saveUser("testuser", RoleType.ROLE_USER));
    }
}
