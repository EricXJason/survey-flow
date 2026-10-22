/**
 * ============================================================================
 * 檔案名稱: GenderConverter.java
 * 所屬模組: 領域資料型別轉換模組 (Domain Converter)
 * 責任描述: 負責受訪者性別在 Java 字串 ("男", "女", "其他") 與 MySQL TINYINT (1, 2, 3, 0)
 *           之間的自動雙向映射，落實資料庫實體層極致壓縮 (1 Byte) 與業務層語意自明。
 * 架構分層: Persistence Layer / JPA AttributeConverter
 * 聯動關係:
 *   - 上游依賴: Jakarta Persistence
 *   - 下游消費: SubmissionBasicInfo.gender
 * 邊界處理: 支援前端 'male'/'female'、'1'/'2'、中文字元全相容轉換；空值回退 0 (未指定)
 * ============================================================================
 */

package com.surveyflow.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class GenderConverter implements AttributeConverter<String, Integer> {

    @Override
    public Integer convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.trim().isEmpty()) {
            return 0; // 0: 未指定 / 未知
        }
        String clean = attribute.trim().toLowerCase();
        return switch (clean) {
            case "1", "male", "男", "男性" -> 1;
            case "2", "female", "女", "女性" -> 2;
            case "3", "other", "其他", "多元" -> 3;
            default -> 0;
        };
    }

    @Override
    public String convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return "未指定";
        }
        return switch (dbData) {
            case 1 -> "男";
            case 2 -> "女";
            case 3 -> "其他";
            default -> "未指定";
        };
    }
}
