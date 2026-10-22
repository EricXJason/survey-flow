/**
 * ============================================================================
 * 檔案名稱: User.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL users 資料表，管理系統會員帳號、個人通訊與安全權限資料
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, Spring Data JPA
 *   - 下游消費: AuthService, UserService, UserDto
 * 邊界處理: 密碼一律存儲 Argon2id 雜湊字串，禁止存儲任何明文
 * ============================================================================
 */

package com.surveyflow.entity;

import com.surveyflow.common.enums.RoleType;
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
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
