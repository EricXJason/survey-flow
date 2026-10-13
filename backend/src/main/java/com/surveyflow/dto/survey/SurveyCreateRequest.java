/**
 * ============================================================================
 * 檔案名稱: SurveyCreateRequest.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 接收建立新問卷之完整請求酬載，包含問卷核心資訊、受訪者收集設定與題目清單
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyDesignComponent (點擊「暫存草稿」或「正式發布」)
 *   - 下游消費: SurveyController, SurveyService
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import com.surveyflow.common.enums.SurveyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyCreateRequest {

    @NotBlank(message = "問卷標題不得為空")
    private String title;

    private String description;

    @Builder.Default
    private String category = "綜合調查";

    @Builder.Default
    private SurveyStatus status = SurveyStatus.DRAFT;

    @NotNull(message = "問卷開始時間不得為空")
    private LocalDateTime startDate;

    @NotNull(message = "問卷結束時間不得為空")
    private LocalDateTime endDate;

    private BasicInfoConfigDto basicInfoConfig;

    @Builder.Default
    private List<QuestionDto> questions = new ArrayList<>();
}
