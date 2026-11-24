/**
 * ============================================================================
 * 檔案名稱: AuditLog.java
 * 所屬模組: Domain Entity 實體層 - 稽核
 * 責任描述: 對映 audit_logs 資料表，一筆不可修改之稽核紀錄；hashFields() 定義參與 HMAC 計算之欄位與順序
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: audit_logs 資料表 (database/schema.sql)
 *   - 下游消費: AuditLogRepository、AuditLogService
 * 邊界處理: [Why]: @Immutable 讓 Hibernate 永遠不對此實體發出 UPDATE；沒有任何 Setter 與業務修改方法，只能新增
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    /** 參與雜湊之時間格式 (固定到毫秒，與 DATETIME(3) 精度一致) */
    private static final DateTimeFormatter HASH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 50)
    private String actor;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(length = 500)
    private String detail;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;

    /**
     * 取得參與 HMAC 計算之欄位 (順序固定；寫入時與驗證時都呼叫本方法，保證一致)
     * @return 發生時間、操作者、事件、對象類型、對象 ID、說明、IP、追蹤碼
     */
    public String[] hashFields() {
        return new String[] {
                occurredAt.format(HASH_TIME_FORMAT), actor, action, targetType, targetId, detail, clientIp, traceId
        };
    }
}
