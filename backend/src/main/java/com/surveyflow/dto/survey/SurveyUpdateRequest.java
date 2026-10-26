/**
 * ============================================================================
 * 檔案名稱: SurveyUpdateRequest.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 接收修改問卷內容之請求酬載
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyDesignComponent (編輯模式)
 *   - 下游消費: SurveyController, SurveyService
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyUpdateRequest {

    private String title;
    private String description;
    private String coverImageUrl;
    private String category;
    private Boolean isPublicToLobby;
    private Boolean isOpen;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "[yyyy-MM-dd'T'HH:mm:ss][yyyy-MM-dd'T'HH:mm][yyyy/MM/dd HH:mm:ss][yyyy/MM/dd HH:mm][yyyy-MM-dd HH:mm:ss][yyyy-MM-dd HH:mm][yyyy-MM-dd]")
    private LocalDateTime startDate;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "[yyyy-MM-dd'T'HH:mm:ss][yyyy-MM-dd'T'HH:mm][yyyy/MM/dd HH:mm:ss][yyyy/MM/dd HH:mm][yyyy-MM-dd HH:mm:ss][yyyy-MM-dd HH:mm][yyyy-MM-dd]")
    private LocalDateTime endDate;
    private BasicInfoConfigDto basicInfoConfig;
    private List<QuestionDto> questions;
}
