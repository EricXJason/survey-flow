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

package org.jasonproduction.surveyflow.dto.survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class SurveySummaryResponse {

    private Long id;
    private String title;
    private String description;
    private String coverImageUrl;
    private String category;
    private Boolean isPublicToLobby;
    private Boolean isOpen;
    private String authorName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer viewCount;

    /** 分享代碼 (專屬填答連結 ?share= 參數)；僅回應給建立者與管理員，受訪者與大廳清單為 null */
    private String shareToken;

    /** 樂觀鎖版本號，前端修改問卷時須原樣送回 */
    private Long version;
    private Long submissionCount;
    private Integer questionCount;
    private LocalDateTime createdAt;

    /** 該會員個人專案之問卷編號 (從 1 起算之個人序號，非全站全局 ID) */
    private Integer userSurveyNumber;
}
