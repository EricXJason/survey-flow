/**
 * ============================================================================
 * 檔案名稱: RefreshTokenRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 管理 JWT Refresh Token 實體之資料存取與撤銷邏輯
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: RefreshToken, Spring Data JPA
 *   - 下游消費: TokenProvider, AuthService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.RefreshToken;
import com.surveyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 依 Token 雜湊值查詢未撤銷且未過期之權杖
     * @param tokenHash 權杖雜湊值
     * @return 匹配之 RefreshToken Optional
     */
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    /**
     * 撤銷該使用者的所有舊 Refresh Token (登出或安全性重設時調用)
     * @param user 使用者實體
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    void revokeAllByUser(User user);
}
