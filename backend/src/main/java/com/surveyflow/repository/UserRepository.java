/**
 * ============================================================================
 * 檔案名稱: UserRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 提供 users 資料表之 JPA CRUD 與客製化查詢介面
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: User, Spring Data JPA
 *   - 下游消費: AuthService, UserDetailsService, UserService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 依登入帳號查詢使用者
     * @param username 登入帳號
     * @return 匹配之使用者 Optional
     */
    Optional<User> findByUsername(String username);

    /**
     * 依電子信箱查詢使用者
     * @param email 電子信箱
     * @return 匹配之使用者 Optional
     */
    Optional<User> findByEmail(String email);

    /**
     * 檢查帳號是否存在
     * @param username 帳號
     * @return true 若已存在
     */
    boolean existsByUsername(String username);

    /**
     * 檢查電子信箱是否存在
     * @param email 信箱
     * @return true 若已存在
     */
    boolean existsByEmail(String email);
}
