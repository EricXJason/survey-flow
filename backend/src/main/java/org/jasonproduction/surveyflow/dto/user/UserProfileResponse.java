/**
 * ============================================================================
 * 檔案名稱: UserProfileResponse.java
 * 所屬模組: DTO 資料傳輸層 - 會員模組
 * 責任描述: 封裝會員個人檔案資料，不包含密碼雜湊等機敏資訊；管理員清單之手機與身分證為遮罩值
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: User 實體, UserService
 *   - 下游消費: UserController, 前端 UserProfileComponent, UserStateService
 * ============================================================================
 */

package org.jasonproduction.surveyflow.dto.user;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@ToString
public class UserProfileResponse {

    private Long id;
    private String username;
    // [Why]: 個資不寫入日誌，toString 排除信箱、手機與身分證
    @ToString.Exclude
    private String email;
    private String fullName;
    /** 手機號碼；管理員清單為遮罩值 (如 0912***678) */
    @ToString.Exclude
    private String phone;
    /** 身分證字號；管理員清單為遮罩值 (如 A******789) */
    @ToString.Exclude
    private String identityCard;
    private RoleType role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
