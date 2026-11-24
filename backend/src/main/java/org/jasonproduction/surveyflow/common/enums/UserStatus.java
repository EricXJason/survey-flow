/**
 * ============================================================================
 * 檔案名稱: UserStatus.java
 * 所屬模組: Common 核心列舉
 * 責任描述: 定義會員帳號生命週期狀態，取代散落各處之魔術字串
 * 架構分層: Domain / Enum Layer
 * 聯動關係:
 *   - 上游依賴: users.status 欄位 (VARCHAR，以列舉名稱存放)
 *   - 下游消費: User 實體、AuthService、UserService、CustomUserDetailsService
 * 邊界處理: 管理員以字串傳入狀態時以 valueOf 轉換，非法值拋出 IllegalArgumentException 由全域例外處理回應 400
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.enums;

public enum UserStatus {
    /** 待驗證：已送出註冊但尚未完成信箱驗證碼核對 */
    PENDING_VERIFICATION,

    /** 啟用：可正常登入與操作 */
    ACTIVE,

    /** 停權：由管理員凍結，禁止登入與換發 Token */
    SUSPENDED,

    /** 已刪除：管理員軟刪除，保留資料但不可再使用 */
    DELETED
}
