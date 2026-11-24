/**
 * ============================================================================
 * 檔案名稱: RatingScoreParserTest.java
 * 測試對象: RatingScoreParser (評分作答文字 → 1~5 分)
 * 測試方式: @ParameterizedTest 以一張「輸入 → 預期」表格列出所有案例，新增案例只需加一行
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RatingScoreParserTest {

    @ParameterizedTest(name = "「{0}」→ {1} 分")
    @DisplayName("數字與量表文字轉為正確分數 (否定詞優先，避免「不滿意」被判成「滿意」)")
    @CsvSource({
            "4, 4",
            " 10 , 10",
            "非常滿意, 5",
            "總是, 5",
            "滿意, 4",
            "經常, 4",
            "普通, 3",
            "有時, 3",
            "不滿意, 2",
            "很少, 2",
            "非常不滿意, 1",
            "從不, 1"
    })
    void parseRating_ReturnsExpectedScore(String input, int expected) {
        assertEquals(expected, RatingScoreParser.parseRating(input));
    }

    @ParameterizedTest(name = "「{0}」→ null")
    @DisplayName("空白或無法辨識之文字回傳 null")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "還可以吧", "abc"})
    void parseRating_UnrecognizedText_ReturnsNull(String input) {
        assertNull(RatingScoreParser.parseRating(input));
    }
}
