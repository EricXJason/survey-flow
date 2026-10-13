/**
 * ============================================================================
 * 檔案名稱: UserProfileResponse.java
 * 所屬模組: DTO 資料傳輸層 - 會員模組
 * 責任描述: 封裝會員個人檔案資料，不包含密碼雜湊等機敏資訊
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: User 實體, UserService
 *   - 下游消費: UserController, 前端 UserProfileComponent, UserStateService
 * ============================================================================
 */

package com.surveyflow.dto.user;

import com.surveyflow.common.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String identityCard;
    private RoleType role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
