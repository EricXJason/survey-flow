/**
 * ============================================================================
 * 檔案名稱: SubmissionAnswerDto.java
 * 所屬模組: DTO 資料傳輸層 - 填答模組
 * 責任描述: 封裝單一題目之作答數據，相容單選、多選（支援多選項集合）、簡答文字與量表分數
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRunnerComponent
 *   - 下游消費: SubmissionService, SubmissionAnswer
 * ============================================================================
 */

package com.surveyflow.dto.submission;

import jakarta.validation.constraints.NotNull;
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
public class SubmissionAnswerDto {

    @NotNull(message = "題目 ID 不得為空")
    private Long questionId;

    /** 單選選項 ID */
    private Long selectedOptionId;

    /** 多選選項 ID 清單 */
    @Builder.Default
    private List<Long> selectedOptionIds = new ArrayList<>();

    /** 開放式簡答文字 */
    private String textAnswer;

    /** 量表評分分數 (1-5 或 1-10) */
    private Integer ratingValue;
}
