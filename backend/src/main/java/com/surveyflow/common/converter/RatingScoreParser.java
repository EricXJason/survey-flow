/**
 * ============================================================================
 * 檔案名稱: RatingScoreParser.java
 * 所屬模組: Common 共用轉換與工具模組
 * 責任描述: 提供量表題目滿意度、認同度、重要度等文字轉整數分值之統一解析與標準化
 * 架構分層: Cross-Cutting / Common Layer (SSOT)
 * 聯動關係:
 *   - 上游依賴: 無 (純邏輯解析)
 *   - 下游消費: SubmissionService, AnalyticsService
 * 邊界處理: 支援純整數解析與文字量表降級對齊，無效文字回傳 null
 * ============================================================================
 */

package com.surveyflow.common.converter;

public final class RatingScoreParser {

    private RatingScoreParser() {
        // 私有構造函式防範實體化
    }

    /**
     * 自作答文字中智能解析整數評分值 (1~5)
     * @param rawText 原始作答文字
     * @return 解析出之整數評分，若無法匹配回傳 null
     */
    public static Integer parseRating(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String text = rawText.trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            if (text.contains("非常滿意") || text.contains("非常同意") || text.contains("非常重要") || text.contains("非常喜歡") || text.contains("總是")) {
                return 5;
            } else if (text.contains("滿意") || text.contains("同意") || text.contains("重要") || text.contains("喜歡") || text.contains("經常")) {
                return 4;
            } else if (text.contains("普通") || text.contains("有時")) {
                return 3;
            } else if (text.contains("非常不") || text.contains("從不")) {
                return 1;
            } else if (text.contains("不滿意") || text.contains("不同意") || text.contains("不重要") || text.contains("不喜歡") || text.contains("很少")) {
                return 2;
            }
        }
        return null;
    }
}
