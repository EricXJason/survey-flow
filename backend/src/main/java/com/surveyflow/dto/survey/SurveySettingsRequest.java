/**
 * ============================================================================
 * 檔案名稱: SurveySettingsRequest.java
 * 所屬模組: DTO 資料傳輸層 - 問卷時限與發布設定
 * 責任描述: 封裝問卷可填寫日期時間區間調整、大廳公開性與即時填寫開放狀態之更新資料
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyManagementComponent (前端設定對話框)
 *   - 下游消費: SurveyController, SurveyService
 * ============================================================================
 */

package com.surveyflow.dto.survey;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveySettingsRequest {

    /**
     * 可填寫起始時間 (含日期與時分，null 代表常態開放無時限)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "[yyyy-MM-dd'T'HH:mm:ss][yyyy-MM-dd'T'HH:mm][yyyy/MM/dd HH:mm:ss][yyyy/MM/dd HH:mm][yyyy-MM-dd HH:mm:ss][yyyy-MM-dd HH:mm][yyyy-MM-dd]")
    private LocalDateTime startDate;

    /**
     * 可填寫截止時間 (含日期與時分，null 代表常態開放無時限)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "[yyyy-MM-dd'T'HH:mm:ss][yyyy-MM-dd'T'HH:mm][yyyy/MM/dd HH:mm:ss][yyyy/MM/dd HH:mm][yyyy-MM-dd HH:mm:ss][yyyy-MM-dd HH:mm][yyyy-MM-dd]")
    private LocalDateTime endDate;

    /**
     * 是否公開展示於大廳 (TRUE: 公開於大廳, FALSE: 僅持有專屬分享連結可填寫)
     */
    @NotNull(message = "大廳公開狀態設定不可為空")
    private Boolean isPublicToLobby;

    /**
     * 是否開放填寫 (TRUE: 開放填寫 PUBLISHED, FALSE: 已截止關閉 CLOSED)
     */
    @NotNull(message = "開放填寫狀態設定不可為空")
    private Boolean isOpen;
}
