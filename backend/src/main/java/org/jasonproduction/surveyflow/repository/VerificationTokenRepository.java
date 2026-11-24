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

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * 以悲觀鎖 (SELECT ... FOR UPDATE) 取出指定信箱、驗證碼與用途之最新未使用驗證碼
     * [Why]: 同一組驗證碼若被兩個請求同時送出 (重複點擊或攻擊)，兩者都可能讀到 is_used = 0 而各自成功。
     *        FOR UPDATE 會鎖住該列直到交易結束：第二個請求必須等第一個提交，
     *        之後讀到的已是 is_used = 1，因此查無資料而失敗，保證驗證碼只能被使用一次。
     *        必須在 @Transactional 方法內呼叫，鎖才會持續到交易提交。
     * @param email 目標信箱
     * @param code 6 碼驗證碼
     * @param tokenType 驗證碼用途 (VerificationTokenType 名稱)
     * @return 已鎖定之驗證碼；查無時為空
     */
    @Query(value = "SELECT * FROM verification_tokens WHERE email = :email AND code = :code AND token_type = :tokenType AND is_used = false ORDER BY id DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<VerificationToken> findValidTokenForUpdate(@Param("email") String email, @Param("code") String code, @Param("tokenType") String tokenType);
}
