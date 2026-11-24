/**
 * ============================================================================
 * 檔案名稱: SurveyResponse.java
 * 所屬模組: DTO 資料傳輸層 - 問卷模組
 * 責任描述: 封裝問卷完整詳情，供問卷編輯器或填答器 (SurveyRunner) 完整載入
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyService, Survey 實體
 *   - 下游消費: SurveyController, 前端 SurveyGetterService
 * ============================================================================
 */

package org.jasonproduction.surveyflow.dto.survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class SurveyResponse {

    private Long id;
    private Long userId;
    private String authorName;
    private String title;
    private String description;
    private String coverImageUrl;
    private String category;
    private Boolean isPublicToLobby;
    private Boolean isOpen;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer viewCount;

    /** 分享代碼 (專屬填答連結 ?share= 參數)；僅回應給建立者與管理員，受訪者與大廳清單為 null */
    private String shareToken;

    /** 樂觀鎖版本號，前端修改問卷時須原樣送回 */
    private Long version;
    private Long submissionCount;
    private BasicInfoConfigDto basicInfoConfig;

    @Builder.Default
    private List<QuestionDto> questions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
