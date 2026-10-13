/**
 * ============================================================================
 * 檔案名稱: TrendDto.java
 * 所屬模組: DTO 資料傳輸層 - 數據分析模組
 * 責任描述: 封裝特定日期區間內每日填答次數走勢
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService 依日期分組聚合
 *   - 下游消費: 前端填答趨勢直方圖
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
public class TrendDto {
    /** 日期標籤 (例如: 2026-10-01) */
    private String date;

    /** 當日回收填答數 */
    private Long count;
}
