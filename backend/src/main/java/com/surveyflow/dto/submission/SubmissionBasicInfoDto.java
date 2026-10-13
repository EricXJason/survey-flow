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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionBasicInfoDto {
    private String name;
    private String gender;
    private String ageGroup;
    private String phone;
    private String email;
    private String industry;
    private String residence;
}
