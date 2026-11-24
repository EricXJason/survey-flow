/**
 * ============================================================================
 * 檔案名稱: UserTest.java
 * 測試對象: User 實體之業務方法 (狀態判定、開通、個資更新、改密碼、停權、軟刪除)
 * 測試方式: 純 Java 物件測試 (Entity 不開放 Setter，所有狀態變更都經由這些方法)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    /**
     * 建立指定狀態之會員
     * @param status 帳號狀態
     * @return 會員實體
     */
    private User user(UserStatus status) {
        return User.builder().id(1L).username("testuser").email("t@example.com").passwordHash("old")
                .fullName("舊姓名").phone("0911111111").identityCard("A123456789")
                .role(RoleType.ROLE_USER).status(status).build();
    }

    @Test
    @DisplayName("Builder 未指定時預設角色為 ROLE_USER、狀態為 ACTIVE")
    void builderDefaults() {
        User user = User.builder().username("u").build();

        assertEquals(RoleType.ROLE_USER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("待驗證帳號：isPendingVerification 為 true；activate 後轉為 ACTIVE")
    void activate_FromPending() {
        User user = user(UserStatus.PENDING_VERIFICATION);
        assertTrue(user.isPendingVerification());
        assertFalse(user.isActive());

        user.activate();

        assertTrue(user.isActive());
        assertFalse(user.isPendingVerification());
    }

    @Test
    @DisplayName("updateProfile：手機為 null 時保留原值、身分證空白時保留原值")
    void updateProfile_KeepsUnchangedFields() {
        User user = user(UserStatus.ACTIVE);

        user.updateProfile("新姓名", null, " ");

        assertEquals("新姓名", user.getFullName());
        assertEquals("0911111111", user.getPhone());
        assertEquals("A123456789", user.getIdentityCard());
    }

    @Test
    @DisplayName("updateProfile：三個欄位都有值時全部更新")
    void updateProfile_UpdatesAllFields() {
        User user = user(UserStatus.ACTIVE);

        user.updateProfile("新姓名", "0922222222", "B223456789");

        assertEquals("0922222222", user.getPhone());
        assertEquals("B223456789", user.getIdentityCard());
    }

    @Test
    @DisplayName("overwritePendingRegistration：以新註冊資料覆寫帳號、信箱、密碼與個資")
    void overwritePendingRegistration() {
        User user = user(UserStatus.PENDING_VERIFICATION);

        user.overwritePendingRegistration("newname", "new@example.com", "newhash", "新會員", null, null);

        assertEquals("newname", user.getUsername());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("newhash", user.getPasswordHash());
        assertEquals("新會員", user.getFullName());
        assertEquals(null, user.getPhone());
    }

    @Test
    @DisplayName("changePasswordHash / changeStatus：直接替換對應欄位")
    void changePasswordAndStatus() {
        User user = user(UserStatus.ACTIVE);

        user.changePasswordHash("newhash");
        user.changeStatus(UserStatus.SUSPENDED);

        assertEquals("newhash", user.getPasswordHash());
        assertEquals(UserStatus.SUSPENDED, user.getStatus());
    }

    @Test
    @DisplayName("softDelete：寫入刪除時間並將狀態設為 DELETED")
    void softDelete() {
        User user = user(UserStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();

        user.softDelete(now);

        assertEquals(now, user.getDeletedAt());
        assertEquals(UserStatus.DELETED, user.getStatus());
    }
}
