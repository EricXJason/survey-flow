/**
 * ============================================================================
 * 檔案名稱: AuditEvent.java
 * 所屬模組: Common 稽核模組
 * 責任描述: 業務 Service 發布之稽核事件 (Spring ApplicationEvent 酬載)，描述「誰、做了什麼、對哪個對象」
 * 架構分層: Common / Audit Layer
 * 聯動關係:
 *   - 上游依賴: AuthService、UserService、SurveyService、TokenRevocationService (ApplicationEventPublisher.publishEvent)
 *   - 下游消費: AuditLogService (@TransactionalEventListener 接收並寫入 audit_logs)
 * 邊界處理: [Why]: 以事件解耦，業務 Service 不直接呼叫 AuditLogService (遵守「Service 不平級互呼」)；
 *           detail 禁止放入密碼、Token、驗證碼等機敏值。actor 為 null 時由 AuditLogService 取目前登入者。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.audit;

/**
 * @param action 事件類型
 * @param actor 操作者帳號；null 表示取 SecurityContext 中之目前登入者
 * @param targetType 操作對象類型 (USER / SURVEY)，可為 null
 * @param targetId 操作對象識別碼，可為 null
 * @param detail 補充說明，可為 null
 */
public record AuditEvent(AuditAction action, String actor, String targetType, String targetId, String detail) {

    /** 操作對象類型：會員 */
    public static final String TARGET_USER = "USER";

    /** 操作對象類型：問卷 */
    public static final String TARGET_SURVEY = "SURVEY";

    /**
     * 是否為失敗事件 (供 @TransactionalEventListener 之 condition 判斷寫入時機)
     * @return true 表示交易回滾時仍需記錄
     */
    public boolean isFailureEvent() {
        return action.failure();
    }
}
