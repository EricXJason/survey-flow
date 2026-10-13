/**
 * ============================================================================
 * 檔案名稱: SurveyUpdateRequest.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 接收修改問卷內容之請求酬載
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyDesignComponent (編輯模式)
 *   - 下游消費: SurveyController, SurveyService
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import com.surveyflow.common.enums.SurveyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyUpdateRequest {

    private String title;
    private String description;
    private String category;
    private SurveyStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BasicInfoConfigDto basicInfoConfig;
    private List<QuestionDto> questions;
}
