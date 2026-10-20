/**
 * ============================================================================
 * 檔案名稱: UserProfileUpdateRequest.java
 * 所屬模組: DTO 資料傳輸層 - 會員模組
 * 責任描述: 封裝會員個人檔案資料修改請求 (真實姓名、手機、身分證)
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: UserProfileComponent (前端個人檔案頁面)
 *   - 下游消費: UserController, UserService
 * ============================================================================
 */

package com.surveyflow.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

    /**
     * 真實中文姓名 (2 至 50 字元)
     */
    @NotBlank(message = "真實姓名不可為空")
    @Size(min = 2, max = 50, message = "姓名長度須介於 2 至 50 字元")
    private String fullName;

    /**
     * 聯絡手機號碼 (臺灣 09 開頭格式)
     */
    @Pattern(regexp = "^(09\\d{8}|09\\d{2}-\\d{3}-\\d{3})?$", message = "手機號碼格式不正確 (須為臺灣 09 開頭 10 碼電話)")
    private String phone;

    /**
     * 身分證字號
     */
    @Pattern(regexp = "^([A-Z][1289]\\d{8})?$", message = "身分證字號格式不合規")
    private String identityCard;
}
