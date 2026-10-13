/**
 * ============================================================================
 * 檔案名稱: SurveyResponse.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 封裝問卷完整詳情，供問卷編輯器或填答器 (SurveyRunner) 完整載入
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyService, Survey 實體
 *   - 下游消費: SurveyController, 前端 SurveyGetterService
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import com.surveyflow.common.enums.SurveyStatus;
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
public class SurveyResponse {

    private Long id;
    private Long userId;
    private String authorName;
    private String title;
    private String description;
    private String category;
    private SurveyStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer viewCount;
    private Long submissionCount;
    private BasicInfoConfigDto basicInfoConfig;

    @Builder.Default
    private List<QuestionDto> questions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
