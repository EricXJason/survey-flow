/**
 * ============================================================================
 * 檔案名稱: RevokedTokenRepository.java
 * 所屬模組: 帳號安全資料存取層
 * 責任描述: 以原生 SQL 維護 JWT 黑名單 (revoked_tokens) 與判定 Token 是否失效
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: RevokedToken Entity、revoked_tokens 與 users 資料表
 *   - 下游消費: TokenRevocationService
 * 邊界處理: 時間一律由呼叫端傳入 (JVM 時區 Asia/Taipei)，不使用資料庫 NOW()，避免資料庫與應用程式時區不同造成誤判
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /**
     * 將 jti 寫入黑名單；已存在時不做任何事
     * [Why]: 以 PRIMARY KEY + INSERT IGNORE 讓「檢查是否用過」與「標記為用過」成為同一個原子動作。
     *        兩個請求同時拿同一顆 Refresh Token 換發時，只有一個會得到 1，另一個得到 0 而被判定為重放。
     * @param jti Token 唯一識別碼
     * @param expiresAt Token 原本之到期時間
     * @param revokedAt 作廢時間
     * @return 1 表示本次新寫入；0 表示 jti 先前已在黑名單
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO revoked_tokens (jti, expires_at, revoked_at) VALUES (:jti, :expiresAt, :revokedAt)", nativeQuery = true)
    int insertIfAbsent(@Param("jti") String jti, @Param("expiresAt") LocalDateTime expiresAt, @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * 判定 Token 是否失效：jti 在黑名單且尚未過期，或簽發時間早於該使用者之整批作廢時間點 (users.tokens_revoked_before)
     * [Why]: 每個已登入請求都會檢查，合併成一次查詢，只多一次以主鍵 / 唯一索引命中的往返
     * @param jti Token 唯一識別碼 (可為 null，null 時只比對整批作廢)
     * @param username Token 所屬帳號
     * @param issuedAt Token 簽發時間 (可為 null，null 時只比對黑名單)
     * @param now 目前時間，用來忽略已過期之黑名單列
     * @return 大於 0 表示必須拒絕此 Token
     */
    @Query(value = "SELECT (EXISTS (SELECT 1 FROM revoked_tokens WHERE jti = :jti AND expires_at > :now)) "
            + "+ (EXISTS (SELECT 1 FROM users WHERE username = :username AND tokens_revoked_before > :issuedAt))",
            nativeQuery = true)
    int countRevocationMatches(@Param("jti") String jti, @Param("username") String username,
                               @Param("issuedAt") LocalDateTime issuedAt, @Param("now") LocalDateTime now);

    /**
     * 刪除已過期之黑名單列 (Token 本身已過期，不需再記錄)
     * @param now 目前時間
     * @return 刪除筆數
     */
    @Modifying
    @Query(value = "DELETE FROM revoked_tokens WHERE expires_at <= :now", nativeQuery = true)
    int deleteExpired(@Param("now") LocalDateTime now);
}
