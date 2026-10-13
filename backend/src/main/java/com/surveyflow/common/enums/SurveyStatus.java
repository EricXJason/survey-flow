/**
 * ============================================================================
 * 檔案名稱: SurveyStatus.java
 * 所屬模組: Common 核心列舉
 * 責任描述: 定義問卷生命週期流轉狀態
 * 架構分層: Domain / Enum Layer
 * 聯動關係:
 *   - 上游依賴: SurveyController 狀態變更端點
 *   - 下游消費: Survey 實體、大廳 Lobby 篩選器與作答器 SurveyRunner 狀態守衛
 * ============================================================================
 */

package com.surveyflow.common.enums;

public enum SurveyStatus {
    /** 正式公開：公開展示於問卷大廳，並依時程限制開放填答 */
    PUBLISHED,

    /** 未公開至大廳：不展示於問卷大廳，僅持專屬直達連結之受訪者可依時程限制填答 */
    UNPUBLISHED
}
