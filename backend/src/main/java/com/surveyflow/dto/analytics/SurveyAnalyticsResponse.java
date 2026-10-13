/**
 * ============================================================================
 * 檔案名稱: SurveyAnalyticsResponse.java
 * 所屬模組: DTO 資料傳輸層 - 數據分析模組
 * 責任描述: 封裝問卷數據分析之全域聚合回應物件，包含 KPI、每日走勢、受訪者畫像與各題詳細統計
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService
 *   - 下游消費: AnalyticsController, 前端 SurveyAnalyticsService
 * ============================================================================
 */

package com.surveyflow.dto.analytics;

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
public class SurveyAnalyticsResponse {

    private Long surveyId;
    private String surveyTitle;
    private KpiDto kpi;

    @Builder.Default
    private List<TrendDto> trends = new ArrayList<>();

    private DemographicsDto demographics;

    @Builder.Default
    private List<QuestionStatDto> questionStats = new ArrayList<>();
}
