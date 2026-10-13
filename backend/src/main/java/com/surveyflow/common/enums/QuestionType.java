/**
 * ============================================================================
 * 檔案名稱: QuestionType.java
 * 所屬模組: Common 核心列舉
 * 責任描述: 定義問卷支援之題型代碼，對應前端問卷設計器與填答器之互動模式
 * 架構分層: Domain / Enum Layer
 * 聯動關係:
 *   - 上游依賴: SurveyDesignComponent 題型工具列
 *   - 下游消費: SurveyQuestion 實體、SubmissionAnswer 驗證與統計分析圖表
 * ============================================================================
 */

package com.surveyflow.common.enums;

public enum QuestionType {
    /** 單選題：受訪者僅能從既定選項中勾選 1 項 */
    SINGLE,

    /** 多選題：受訪者可複選 1 至多項選項 */
    MULTI,

    /** 簡答題 / 開放式文字反饋：受訪者以自由文字輸入回答 */
    TEXT,

    /** 評分量表題：1 至 5 分 (或 1 至 10 分) Likert 滿意度或 NPS 量表 */
    RATING
}
