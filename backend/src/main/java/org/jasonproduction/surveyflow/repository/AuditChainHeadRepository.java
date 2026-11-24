/**
 * ============================================================================
 * 檔案名稱: AuditChainHeadRepository.java
 * 所屬模組: 稽核資料存取層
 * 責任描述: 讀取與鎖定稽核雜湊鏈頂端 (audit_chain_head 單列)
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: AuditChainHead Entity
 *   - 下游消費: AuditLogService
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.AuditChainHead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, Integer> {

    /**
     * 以悲觀鎖 (SELECT ... FOR UPDATE) 取得鏈頂端
     * [Why]: 兩個請求同時寫入稽核時，第二個必須等第一個提交後才讀得到新的 last_hash，否則兩筆會接在同一筆後面而分岔。
     *        鎖定式讀取一律讀最新已提交版本，必須在 @Transactional 方法內呼叫。
     * @return 鏈頂端；schema.sql 未建立起點列時為空
     */
    @Query(value = "SELECT * FROM audit_chain_head WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Optional<AuditChainHead> findHeadForUpdate();

    /**
     * 不加鎖讀取鏈頂端 (驗證雜湊鏈時使用)
     * @return 鏈頂端
     */
    @Query(value = "SELECT * FROM audit_chain_head WHERE id = 1", nativeQuery = true)
    Optional<AuditChainHead> findHeadNative();
}
