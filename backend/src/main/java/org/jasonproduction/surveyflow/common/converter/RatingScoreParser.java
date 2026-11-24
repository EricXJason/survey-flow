/**
 * ============================================================================
 * 檔案名稱: RatingScoreParser.java
 * 所屬模組: Common 共用轉換與工具模組
 * 責任描述: 將評分題之作答文字轉為整數分數。純數字直接轉換；文字量表 (如「非常滿意」「不同意」)
 *           依關鍵字對照表轉為 1~5 分。
 * 架構分層: Cross-Cutting / Common Layer (SSOT)
 * 聯動關係:
 *   - 上游依賴: 無 (純邏輯解析)
 *   - 下游消費: SubmissionService (評分題未帶 ratingValue 時)、AnalyticsService (統計文字評分)
 * 邊界處理: 空白或無法辨識之文字回傳 null，由呼叫端決定是否略過
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.converter;

import java.util.List;

public final class RatingScoreParser {

    /**
     * 關鍵字與對應分數
     * @param score 命中時回傳之分數
     * @param keywords 任一關鍵字出現於作答文字即視為命中
     */
    private record KeywordScore(int score, List<String> keywords) {
    }

    /**
     * 關鍵字對照表 (由上而下依序比對，第一個命中者即為結果)
     * [Why]: 否定詞必須排在前面。「非常不滿意」「不滿意」都包含「滿意」，
     *        若先比對「滿意」會被誤判為 4 分。
     */
    private static final List<KeywordScore> KEYWORD_SCORES = List.of(
            new KeywordScore(1, List.of("非常不", "從不")),
            new KeywordScore(2, List.of("不滿意", "不同意", "不重要", "不喜歡", "很少")),
            new KeywordScore(5, List.of("非常滿意", "非常同意", "非常重要", "非常喜歡", "總是")),
            new KeywordScore(4, List.of("滿意", "同意", "重要", "喜歡", "經常")),
            new KeywordScore(3, List.of("普通", "有時"))
    );

    /**
     * 純靜態工具類別，禁止實體化
     */
    private RatingScoreParser() {
    }

    /**
     * 將作答文字解析為整數分數
     * 第一步：空白回傳 null。
     * 第二步：可直接轉成整數者 (如 "4") 直接回傳。
     * 第三步：依 KEYWORD_SCORES 由上而下比對關鍵字，回傳第一個命中之分數。
     * @param rawText 原始作答文字，可為 null
     * @return 解析出之分數；無法辨識時回傳 null
     */
    public static Integer parseRating(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String text = rawText.trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notNumeric) {
            return KEYWORD_SCORES.stream()
                    .filter(entry -> entry.keywords().stream().anyMatch(text::contains))
                    .map(KeywordScore::score)
                    .findFirst()
                    .orElse(null);
        }
    }
}
