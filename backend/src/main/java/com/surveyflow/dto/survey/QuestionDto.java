/**
 * ============================================================================
 * 檔案名稱: QuestionDto.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 封裝單一題目資料傳輸規格，包含題型、題幹文字、必填狀態、排序與選項清單
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyCreateRequest, SurveyResponse
 *   - 下游消費: SurveyService, SurveyQuestion
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import com.surveyflow.common.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;

    @Size(max = 50, message = "題目代碼長度不得超過 50 字元")
    private String questionCode;

    @NotNull(message = "題型不得為空")
    private QuestionType type;

    @NotBlank(message = "題目文字不得為空")
    @Size(min = 1, max = 500, message = "題目文字長度需介於 1 至 500 字元")
    private String questionText;

    @Builder.Default
    private Boolean isRequired = true;

    @Builder.Default
    private Integer sortOrder = 1;

    @Builder.Default
    private Integer minRating = 1;

    @Builder.Default
    private Integer maxRating = 5;

    @Builder.Default
    private List<@Valid OptionDto> options = new ArrayList<>();
}
