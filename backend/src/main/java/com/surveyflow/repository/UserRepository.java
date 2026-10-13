/**
 * ============================================================================
 * 檔案名稱: UserRepository.java
 * 所屬模組: Persistence Repository 層
 * 責任描述: 提供 users 資料表之 JPA CRUD 與原生 SQL 檢索介面
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: User, Spring Data JPA
 *   - 下游消費: AuthService, CustomUserDetailsService, AdminService
 * ============================================================================
 */

package com.surveyflow.repository;

import com.surveyflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 原生 SQL 依登入帳號查詢使用者
     * @param username 登入帳號
     * @return 匹配之使用者 Optional
     */
    @Query(value = "SELECT * FROM users WHERE username = :username AND deleted_at IS NULL LIMIT 1", nativeQuery = true)
    Optional<User> findByUsernameNative(@Param("username") String username);

    /**
     * 原生 SQL 依電子信箱查詢使用者
     * @param email 電子信箱
     * @return 匹配之使用者 Optional
     */
    @Query(value = "SELECT * FROM users WHERE email = :email AND deleted_at IS NULL LIMIT 1", nativeQuery = true)
    Optional<User> findByEmailNative(@Param("email") String email);

    /**
     * 原生 SQL 檢查帳號是否存在
     * @param username 帳號
     * @return 1 若存在，否則 0
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE username = :username AND deleted_at IS NULL", nativeQuery = true)
    long countByUsernameNative(@Param("username") String username);

    /**
     * 原生 SQL 檢查信箱是否存在
     * @param email 信箱
     * @return 1 若存在，否則 0
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE email = :email AND deleted_at IS NULL", nativeQuery = true)
    long countByEmailNative(@Param("email") String email);

    /**
     * 原生 SQL 分頁查詢全站會員 (管理員專用)
     * @param pageable 分頁參數
     * @return 全站會員分頁
     */
    @Query(value = "SELECT * FROM users WHERE deleted_at IS NULL AND role != 'ROLE_ADMIN' ORDER BY created_at DESC",
           countQuery = "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND role != 'ROLE_ADMIN'",
           nativeQuery = true)
    Page<User> findAllUsersForAdminNative(Pageable pageable);

    // 保留標準 Spring Data JPA 兼容別名方法供 Service 直接呼叫
    default Optional<User> findByUsername(String username) {
        return findByUsernameNative(username);
    }

    default Optional<User> findByEmail(String email) {
        return findByEmailNative(email);
    }

    default boolean existsByUsername(String username) {
        return countByUsernameNative(username) > 0;
    }

    default boolean existsByEmail(String email) {
        return countByEmailNative(email) > 0;
    }
}
