/**
 * ============================================================================
 * 檔案名稱: OptionStatItemDto.java
 * 所屬模組: DTO 資料傳輸層 - 數據分析模組
 * 責任描述: 封裝個別選項之精確聚合統計資料（以選項 ID 為絕對主鍵，支援同名選項完全獨立呈現）
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService 題型選項計算
 *   - 下游消費: QuestionStatDto 與前端 OptionStatItem
 * ============================================================================
 */

package org.jasonproduction.surveyflow.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionStatItemDto {

    /** 選項 ID（絕對唯一識別碼） */
    private Long optionId;

    /** 選項文字標籤 */
    private String text;

    /** 選項排序序號 */
    private Integer sortOrder;

    /** 該選項獲得之總票數（選取次數） */
    private Long count;

    /** 佔總填答受訪者之百分比（0.0 ~ 100.0） */
    private Double percentage;
}
