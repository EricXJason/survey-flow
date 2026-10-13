/**
 * ============================================================================
 * 檔案名稱: SurveySummaryResponse.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 封裝問卷概要列表項目，供問卷大廳 (Lobby) 與問卷管理 (SurveyManagement) 列表使用
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyService, SurveyRepository
 *   - 下游消費: LobbyComponent, SurveyManagementComponent
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import com.surveyflow.common.enums.SurveyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveySummaryResponse {

    private Long id;
    private String title;
    private String description;
    private String coverImageUrl;
    private String category;
    private Boolean isPublicToLobby;
    private SurveyStatus status;
    private String authorName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer viewCount;
    private Long submissionCount;
    private Integer questionCount;
    private LocalDateTime createdAt;
}
