/**
 * ============================================================================
 * 檔案名稱: PasswordChangeRequest.java
 * 所屬模組: DTO 資料傳輸層 - 會員模組
 * 責任描述: 封裝會員在個人資料頁修改登入密碼請求
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: UserProfileComponent (前端修改密碼面板)
 *   - 下游消費: UserController, UserService
 * ============================================================================
 */

package com.surveyflow.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeRequest {

    /**
     * 既有原密碼
     */
    @NotBlank(message = "原密碼不可為空")
    private String oldPassword;

    /**
     * 欲變更之新密碼 (8 至 100 字元)
     */
    @NotBlank(message = "新密碼不可為空")
    @Size(min = 6, max = 100, message = "新密碼長度須介於 6 至 100 字元")
    private String newPassword;
}
