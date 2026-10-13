/**
 * ============================================================================
 * 檔案名稱: BasicInfoConfigDto.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 封裝問卷基本資料收集設定（姓名、性別、年齡、手機、信箱等開關）
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyDesignComponent, SurveyRunnerComponent
 *   - 下游消費: SurveyService, SurveyBasicInfoConfig
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasicInfoConfigDto {
    private Boolean requireName;
    private Boolean requireGender;
    private Boolean requireAge;
    private Boolean requirePhone;
    private Boolean requireEmail;
    private Boolean requireIndustry;
    private Boolean requireResidence;
}
