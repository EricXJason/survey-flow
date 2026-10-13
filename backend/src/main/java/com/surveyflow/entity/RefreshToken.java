/**
 * ============================================================================
 * 檔案名稱: RefreshToken.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL refresh_tokens 資料表，記錄已簽發之長效 Refresh Token 以便旋轉與防偽
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: RefreshTokenRepository
 *   - 下游消費: TokenProvider, AuthService
 * ============================================================================
 */

package com.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
