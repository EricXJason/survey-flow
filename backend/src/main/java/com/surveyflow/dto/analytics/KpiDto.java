/**
 * ============================================================================
 * 檔案名稱: KpiDto.java
 * 所屬模組: DTO 資料傳輸層 - 數據分析模組
 * 責任描述: 封裝問卷數據分析儀表板頂部 5 大核心 KPI 指標
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService 聚合計算
 *   - 下游消費: 前端 SurveyManagementComponent 數據儀表板
 * ============================================================================
 */

package com.surveyflow.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDto {
    /** 總回覆數 */
    private Long totalResponses;

    /** 平均填答秒數 (例如: 145 秒) */
    private Integer averageDurationSeconds;

    /** 完成率百分比 (例如: 94.5%) */
    private Double completionRate;

    /** 淨推薦值 NPS 指數 (-100 ~ +100) */
    private Integer netPromoterScore;

    /** 今日新增填答數 */
    private Long todayResponses;
}
