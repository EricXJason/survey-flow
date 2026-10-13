/**
 * ============================================================================
 * 檔案名稱: RoleType.java
 * 所屬模組: Common 核心列舉
 * 責任描述: 定義系統使用者之身分角色與安全權限邊界
 * 架構分層: Domain / Enum Layer
 * 聯動關係:
 *   - 上游依賴: Spring Security 授權框架
 *   - 下游消費: User 實體、JwtAuthenticationFilter 與權限註解 (@PreAuthorize)
 * ============================================================================
 */

package com.surveyflow.common.enums;

public enum RoleType {
    /** 訪客：未登入之匿名受訪者，僅能填答公開或持分享連結之問卷 */
    ROLE_GUEST,

    /** 一般註冊會員：可建立與管理自己發布的問卷、查閱自己問卷之分析統計報表 */
    ROLE_USER,

    /** 系統最高管理員：可管理全站會員帳號、檢視與分析全站所有問卷數據 */
    ROLE_ADMIN
}
