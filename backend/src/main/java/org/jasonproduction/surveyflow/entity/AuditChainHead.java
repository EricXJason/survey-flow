/**
 * ============================================================================
 * 檔案名稱: AuditChainHead.java
 * 所屬模組: Domain Entity 實體層 - 稽核
 * 責任描述: 對映 audit_chain_head (固定單列)，記錄稽核雜湊鏈最後一筆之 ID 與 hash
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: audit_chain_head 資料表 (schema.sql 建表時寫入 id = 1)
 *   - 下游消費: AuditChainHeadRepository (FOR UPDATE 鎖定)、AuditLogService
 * 邊界處理: 狀態只能經 advance() 前進到新的一筆，不提供 Setter
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_chain_head")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditChainHead {

    /** 唯一一列之主鍵值 */
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "last_log_id", nullable = false)
    private Long lastLogId;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 鏈頂端前進到剛寫入之紀錄
     * @param logId 新紀錄 ID
     * @param hash 新紀錄 hash
     * @param now 更新時間
     */
    public void advance(Long logId, String hash, LocalDateTime now) {
        this.lastLogId = logId;
        this.lastHash = hash;
        this.updatedAt = now;
    }
}
