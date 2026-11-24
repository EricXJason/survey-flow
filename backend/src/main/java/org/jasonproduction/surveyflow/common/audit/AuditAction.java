/**
 * ============================================================================
 * 檔案名稱: AuditAction.java
 * 所屬模組: Common 稽核模組
 * 責任描述: 定義會寫入稽核日誌之事件類型、繁中名稱，以及是否為「失敗事件」
 * 架構分層: Common / Audit Layer
 * 聯動關係:
 *   - 上游依賴: 無
 *   - 下游消費: AuthService、UserService、SurveyService、TokenRevocationService (發布事件)；AuditLogService (寫入與查詢)；
 *               前端稽核日誌頁 (篩選選單與顯示名稱由 GET /api/admin/audit-logs/actions 取得)
 * 邊界處理: [Why]: 失敗事件 (failure = true) 通常伴隨例外與交易回滾，仍必須留下紀錄，
 *           AuditLogService 對它們改在交易結束 (不論提交或回滾) 後寫入；成功事件只在交易提交後寫入，回滾的操作不留紀錄。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.audit;

public enum AuditAction {

    REGISTERED("註冊帳號", false),
    ACCOUNT_ACTIVATED("開通帳號", false),
    LOGIN_SUCCEEDED("登入成功", false),
    LOGIN_FAILED("登入失敗", true),
    LOGOUT("登出", false),
    PASSWORD_RESET("以驗證碼重設密碼", false),
    PASSWORD_CHANGED("變更密碼", false),
    PROFILE_UPDATED("修改個人資料", false),
    TOKEN_REPLAY_DETECTED("偵測到 Refresh Token 重放", true),
    USER_STATUS_CHANGED("變更會員狀態", false),
    USER_DELETED("刪除會員", false),
    SURVEY_CREATED("建立問卷", false),
    SURVEY_UPDATED("修改問卷內容", false),
    SURVEY_SETTINGS_CHANGED("修改問卷開放設定", false),
    SURVEY_DELETED("刪除問卷", false);

    private final String label;
    private final boolean failure;

    /**
     * @param label 繁中顯示名稱
     * @param failure 是否為失敗事件 (交易回滾時仍需記錄)
     */
    AuditAction(String label, boolean failure) {
        this.label = label;
        this.failure = failure;
    }

    /**
     * 取得繁中顯示名稱
     * @return 名稱
     */
    public String label() {
        return label;
    }

    /**
     * 是否為失敗事件
     * @return true 表示交易回滾時仍需記錄
     */
    public boolean failure() {
        return failure;
    }
}
