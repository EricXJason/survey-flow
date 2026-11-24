/**
 * ============================================================================
 * 檔案名稱: RevokedToken.java
 * 所屬模組: Domain Entity 實體層 - 帳號安全
 * 責任描述: 對映 revoked_tokens 資料表，代表一顆已被作廢 (登出或 Refresh Token 輪替) 的 JWT
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: revoked_tokens 資料表 (database/schema.sql)
 *   - 下游消費: RevokedTokenRepository、TokenRevocationService
 * 邊界處理: 寫入一律走 RevokedTokenRepository 之原生 INSERT IGNORE (保證輪替原子性)，本實體只作為 Repository 型別，不經 save() 寫入
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "revoked_tokens")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RevokedToken {

    /** JWT 唯一識別碼 (jti 宣告，UUID 36 字元) */
    @Id
    @Column(name = "jti", nullable = false, length = 36)
    private String jti;

    /** 該 Token 原本之到期時間，過期後此列可被排程刪除 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;
}
