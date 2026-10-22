/**
 * ============================================================================
 * 檔案名稱: ModelMapperConfig.java
 * 所屬模組: 系統全域架構設定模組 (System Configuration Module)
 * 責任描述: 註冊 ModelMapper 為 Spring 全域單例 Bean，負責 Entity 實體與 DTO 傳輸物件的雙向自動映射
 * 架構分層: Configuration Layer
 * 聯動關係:
 *   - 上游依賴: Spring Framework
 *   - 下游消費: SurveyService, UserService, SubmissionService
 * 邊界處理: 啟用嚴格/標準混合匹配策略，忽略 null 值覆蓋，避免破壞現存目標物件欄位
 * ============================================================================
 */

package com.surveyflow.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    /**
     * 宣告 ModelMapper 全域 Bean
     * @return 配置完成之 ModelMapper 實例
     */
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setFieldMatchingEnabled(true)
                .setSkipNullEnabled(true);
        return mapper;
    }
}
