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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyCreateRequest {

    @NotBlank(message = "問卷標題不得為空")
    @Size(min = 1, max = 150, message = "問卷標題長度需介於 1 至 150 字元")
    private String title;

    @Size(max = 5000, message = "問卷前言描述長度不得超過 5000 字元")
    private String description;

    private String coverImageUrl;

    @Builder.Default
    @Size(max = 50, message = "問卷分類長度不得超過 50 字元")
    private String category = "綜合調查";

    @Builder.Default
    private Boolean isPublicToLobby = true;

    @Builder.Default
    private Boolean isOpen = true;

    @NotNull(message = "問卷開始時間不得為空")
    private LocalDateTime startDate;

    @NotNull(message = "問卷結束時間不得為空")
    private LocalDateTime endDate;

    @Valid
    private BasicInfoConfigDto basicInfoConfig;

    @Builder.Default
    private List<@Valid QuestionDto> questions = new ArrayList<>();
}
