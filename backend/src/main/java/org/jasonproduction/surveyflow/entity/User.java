/**
 * ============================================================================
 * 檔案名稱: User.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL users 資料表，管理系統會員帳號、個人通訊與安全權限資料
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: users 資料表 (database/schema.sql)、RoleType、UserStatus
 *   - 下游消費: UserRepository, AuthService, UserService, CustomUserDetailsService
 * 邊界處理: 密碼一律存儲 Argon2id 雜湊字串，禁止存儲任何明文；
 *           不開放 Setter，所有狀態變更皆透過具業務語意之方法完成
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    @Column(columnDefinition = "CHAR(10)", unique = true)
    private String phone;

    @Column(name = "identity_card", columnDefinition = "CHAR(10)", unique = true)
    private String identityCard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoleType role = RoleType.ROLE_USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 判定帳號是否為啟用狀態
     * @return true 表示可正常登入與換發 Token
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * 判定帳號是否仍在等待信箱驗證
     * @return true 表示註冊後尚未完成驗證碼核對
     */
    public boolean isPendingVerification() {
        return status == UserStatus.PENDING_VERIFICATION;
    }

    /**
     * 以新的註冊表單內容覆寫一筆尚未驗證之註冊資料，避免使用者因舊驗證信過期而卡死
     * @param username 新登入帳號 (已 trim)
     * @param email 新電子信箱 (已 trim)
     * @param passwordHash 新密碼之 Argon2id 雜湊值
     * @param fullName 真實姓名 (已 trim)
     * @param phone 手機號碼，可為 null
     * @param identityCard 身分證字號，可為 null
     */
    public void overwritePendingRegistration(String username, String email, String passwordHash,
                                             String fullName, String phone, String identityCard) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.identityCard = identityCard;
    }

    /**
     * 信箱驗證通過後正式開通帳號
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 更新個人檔案；手機為 null 時保留原值，身分證為空白時保留原值
     * @param fullName 真實姓名 (必填，已 trim)
     * @param phone 手機號碼 (已 trim)，null 表示不修改
     * @param identityCard 身分證字號 (已 trim)，null 或空白表示不修改
     */
    public void updateProfile(String fullName, String phone, String identityCard) {
        this.fullName = fullName;
        if (phone != null) {
            this.phone = phone;
        }
        if (identityCard != null && !identityCard.isBlank()) {
            this.identityCard = identityCard;
        }
    }

    /**
     * 替換登入密碼雜湊值
     * @param newPasswordHash 新密碼之 Argon2id 雜湊值
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * 管理員變更帳號狀態 (如停權 / 解除停權)
     * @param newStatus 目標狀態
     */
    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * 軟刪除帳號：標記刪除時間並將狀態設為 DELETED
     * @param deletedTime 刪除時間戳記
     */
    public void softDelete(LocalDateTime deletedTime) {
        this.deletedAt = deletedTime;
        this.status = UserStatus.DELETED;
    }
}
