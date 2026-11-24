/**
 * ============================================================================
 * 檔案名稱: AuditLogRepository.java
 * 所屬模組: 稽核資料存取層
 * 責任描述: 稽核紀錄之新增 (save)、管理員條件分頁查詢，以及驗證雜湊鏈用之依 ID 遞增分批讀取
 * 架構分層: Persistence / Repository Layer
 * 聯動關係:
 *   - 上游依賴: AuditLog Entity、audit_logs 資料表
 *   - 下游消費: AuditLogService
 * 邊界處理: 刻意不提供任何 UPDATE / DELETE 查詢；條件為 NULL 或空字串時視為不篩選
 * ============================================================================
 */

package org.jasonproduction.surveyflow.repository;

import org.jasonproduction.surveyflow.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 原生 SQL 管理員條件分頁查詢 (依 id 由新到舊)
     * @param actor 操作者帳號 (完全比對)，NULL 或空字串表示不篩選
     * @param action 事件類型代碼，NULL 或空字串表示不篩選
     * @param pageable 分頁參數
     * @return 稽核紀錄分頁
     */
    @Query(value = "SELECT * FROM audit_logs WHERE (:actor IS NULL OR :actor = '' OR actor = :actor) "
            + "AND (:action IS NULL OR :action = '' OR action = :action) ORDER BY id DESC",
           countQuery = "SELECT COUNT(*) FROM audit_logs WHERE (:actor IS NULL OR :actor = '' OR actor = :actor) "
            + "AND (:action IS NULL OR :action = '' OR action = :action)",
           nativeQuery = true)
    Page<AuditLog> searchNative(@Param("actor") String actor, @Param("action") String action, Pageable pageable);

    /**
     * 原生 SQL 依 id 遞增分批讀取 (Keyset 分頁)，供驗證整條雜湊鏈時不必一次載入全部紀錄
     * @param afterId 上一批最後一筆之 id (第一批傳 0)
     * @param batchSize 每批筆數
     * @return id 大於 afterId 之下一批紀錄
     */
    @Query(value = "SELECT * FROM audit_logs WHERE id > :afterId ORDER BY id ASC LIMIT :batchSize", nativeQuery = true)
    List<AuditLog> findBatchAfterIdNative(@Param("afterId") long afterId, @Param("batchSize") int batchSize);
}
