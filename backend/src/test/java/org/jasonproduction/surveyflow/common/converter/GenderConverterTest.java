/**
 * ============================================================================
 * 檔案名稱: GenderConverterTest.java
 * 測試對象: GenderConverter (性別文字 ↔ 資料庫 TINYINT 代碼)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenderConverterTest {

    private final GenderConverter converter = new GenderConverter();

    @ParameterizedTest(name = "「{0}」寫入資料庫為 {1}")
    @DisplayName("中英文與數字性別寫入資料庫時轉為代碼 (無法辨識為 0)")
    @CsvSource({
            "男, 1", "male, 1", "MALE, 1", "1, 1",
            "女, 2", "female, 2", "2, 2",
            "其他, 3", "other, 3",
            "外星人, 0"
    })
    void toDatabaseColumn(String attribute, int expected) {
        assertEquals(expected, converter.convertToDatabaseColumn(attribute));
    }

    @Test
    @DisplayName("null 或空白寫入資料庫為 0 (未指定)")
    void toDatabaseColumn_Blank() {
        assertEquals(0, converter.convertToDatabaseColumn(null));
        assertEquals(0, converter.convertToDatabaseColumn("  "));
    }

    @ParameterizedTest(name = "代碼 {0} 讀出為「{1}」")
    @DisplayName("資料庫代碼讀出時轉為繁中文字")
    @CsvSource({"1, 男", "2, 女", "3, 其他", "0, 未指定", "9, 未指定"})
    void toEntityAttribute(int dbData, String expected) {
        assertEquals(expected, converter.convertToEntityAttribute(dbData));
    }

    @Test
    @DisplayName("資料庫為 NULL 時讀出為「未指定」")
    void toEntityAttribute_Null() {
        assertEquals("未指定", converter.convertToEntityAttribute(null));
    }
}
