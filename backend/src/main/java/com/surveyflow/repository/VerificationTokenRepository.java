/**
 * ============================================================================
 * 檔案名稱: VerificationTokenRepository.java
 * 所屬模組: 帳號安全資料存取層
 * 責任描述: 提供驗證碼之資料庫查詢、時效檢核與標記核銷能力
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: VerificationToken Entity, JpaRepository
 *   - 下游消費: AuthService, MailService
 * 邊界處理: 透過原生 SQL 依 Email 與驗證類型取得最新一筆有效驗證碼
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * 查詢指定信箱與驗證類型之最新一筆未核銷驗證碼
     * @param email 目標信箱
     * @param tokenType 驗證類型 (FORGOT_PASSWORD / REGISTRATION)
     * @return 最新驗證碼物件
     */
    @Query(value = "SELECT * FROM verification_tokens WHERE email = :email AND token_type = :tokenType AND is_used = false ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<VerificationToken> findLatestActiveToken(@Param("email") String email, @Param("tokenType") String tokenType);

    /**
     * 查詢指定信箱、驗證碼與類型之有效記錄
     * @param email 目標信箱
     * @param code 6 碼驗證碼
     * @param tokenType 驗證類型
     * @return 驗證碼物件
     */
    @Query(value = "SELECT * FROM verification_tokens WHERE email = :email AND code = :code AND token_type = :tokenType AND is_used = false ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<VerificationToken> findValidToken(@Param("email") String email, @Param("code") String code, @Param("tokenType") String tokenType);
}
