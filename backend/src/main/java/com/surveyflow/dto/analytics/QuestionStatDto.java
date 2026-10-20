/**
 * ============================================================================
 * 檔案名稱: QuestionStatDto.java
 * 所屬模組: DTO 資料傳輸層 - 數據分析模組
 * 責任描述: 封裝個別題目的統計聚合數據（選項佔比、量表平均分與情感分組、文字答案列表）
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService 題型分組計算
 *   - 下游消費: 前端題型卡片與圖表視覺化
 * ============================================================================
 */

package com.surveyflow.dto.analytics;

import com.surveyflow.common.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionStatDto {

    private Long questionId;
    private String questionCode;
    private String questionText;
    private QuestionType type;
    private Long totalAnswers;

    /** 選項統計 (選項名稱 -> 選取次數) */
    @Builder.Default
    private Map<String, Long> optionCounts = new HashMap<>();

    /** 選項百分比 (選項名稱 -> 百分比 0.0 ~ 100.0) */
    @Builder.Default
    private Map<String, Double> optionPercentages = new HashMap<>();

    /** 評分量表平均分 (僅 RATING) */
    private Double averageRating;

    /** 評分量表最小分與最大分 */
    private Integer minRating;
    private Integer maxRating;

    /** 評分分佈明細 (分數 -> 人數) (僅 RATING) */
    @Builder.Default
    private Map<Integer, Long> ratingDistribution = new HashMap<>();

    /** 評分量表統計指標 (中位數、眾數、標準差、量表 NPS) */
    private Double medianRating;
    private Integer modeRating;
    private Double standardDeviation;
    private Integer npsScore;

    /** 情感分組統計 (貶損者/被動者/推薦者) (僅 RATING) */
    @Builder.Default
    private Map<String, Long> npsBreakdown = new HashMap<>();

    /** 開放式文字反饋清單抽樣 (支援結構化物件包含 content, respondentName, submittedAt) */
    @Builder.Default
    private List<Object> textFeedbacks = new ArrayList<>();
}
