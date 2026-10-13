/**
 * ============================================================================
 * 檔案名稱: OptionDto.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 封裝題目選項內容與顯示順序
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: QuestionDto, SurveyDesignComponent
 *   - 下游消費: SurveyOption, SurveyService
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionDto {
    private Long id;

    @NotBlank(message = "選項文字不得為空")
    private String optionText;

    @Builder.Default
    private Integer sortOrder = 1;
}
