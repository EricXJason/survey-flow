/**
 * ============================================================================
 * 檔案名稱: DemographicsDto.java
 * 所屬模組: DTO 資料傳輸層 - 數據分析模組
 * 責任描述: 封裝受訪者人口統計學輪廓（性別比例、年齡層金字塔、產業分佈、居住地區）
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService 輪廓聚合
 *   - 下游消費: 前端人口統計圓環圖與比例橫條
 * ============================================================================
 */

package com.surveyflow.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemographicsDto {

    /** 性別統計分佈 (男, 女, 其他) -> 次數 */
    @Builder.Default
    private Map<String, Long> genderDistribution = new HashMap<>();

    /** 年齡層分佈 (18-24歲, 25-34歲等) -> 次數 */
    @Builder.Default
    private Map<String, Long> ageGroupDistribution = new HashMap<>();

    /** 前五大任職產業分佈 -> 次數 */
    @Builder.Default
    private Map<String, Long> industryDistribution = new HashMap<>();

    /** 前五大居住地區分佈 -> 次數 */
    @Builder.Default
    private Map<String, Long> residenceDistribution = new HashMap<>();
}
