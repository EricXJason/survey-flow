/**
 * ============================================================================
 * 檔案名稱: VerificationToken.java
 * 所屬模組: 帳號安全與驗證權杖實體層
 * 責任描述: 對映 verification_tokens 資料表，管理註冊啟用與忘記密碼之時效性驗證碼 (OTP)
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: Spring Data JPA
 *   - 下游消費: VerificationTokenRepository, MailService, AuthService
 * 邊界處理: 驗證碼預設時效 15 分鐘，經核驗或過期後標記為已使用，杜絕重複利用
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import org.jasonproduction.surveyflow.common.enums.VerificationTokenType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens", indexes = {
        @Index(name = "idx_vt_email_type", columnList = "email, token_type")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 30)
    private VerificationTokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_used", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @Builder.Default
    private Boolean isUsed = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 判定驗證碼是否依然有效 (未過期且未使用)
     * @return true 表示可用於核對
     */
    public boolean isValid() {
        return !isUsed && expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * 核銷驗證碼，杜絕同一組驗證碼被重複使用
     */
    public void markUsed() {
        this.isUsed = true;
    }
}
