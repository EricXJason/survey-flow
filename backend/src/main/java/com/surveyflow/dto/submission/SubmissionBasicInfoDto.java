/**
 * ============================================================================
 * 檔案名稱: SubmissionBasicInfoDto.java
 * 所屬模組: DTO 資料傳輸層 - 填答模組
 * 責任描述: 封裝受訪者填答時填寫之個人基本輪廓資料
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRunnerComponent
 *   - 下游消費: SubmissionRequest, SubmissionService
 * ============================================================================
 */

package com.surveyflow.dto.submission;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionBasicInfoDto {

    @Size(max = 50, message = "姓名長度不得超過 50 字元")
    private String name;

    @Size(max = 10, message = "性別長度不得超過 10 字元")
    private String gender;

    @Size(max = 30, message = "年齡區間長度不得超過 30 字元")
    private String ageGroup;

    @Size(max = 20, message = "電話長度不得超過 20 字元")
    private String phone;

    @Size(max = 100, message = "電子信箱長度不得超過 100 字元")
    private String email;

    @Size(max = 50, message = "產業別長度不得超過 50 字元")
    private String industry;

    @Size(max = 50, message = "居住地區長度不得超過 50 字元")
    private String residence;
}
