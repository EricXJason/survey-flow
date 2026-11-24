/**
 * ============================================================================
 * 檔案名稱: SubmissionRequest.java
 * 所屬模組: DTO 資料傳輸層 - 填答模組
 * 責任描述: 接收受訪者提交填答之完整請求，包含問卷 ID、分享代碼、填答耗時、受訪者輪廓與題型答案陣列
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRunnerComponent (提交問卷)
 *   - 下游消費: SubmissionController, SubmissionService
 * ============================================================================
 */

package org.jasonproduction.surveyflow.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequest {

    @NotNull(message = "問卷 ID 不得為空")
    private Long surveyId;

    /** 分享代碼：由專屬填答連結進入時帶上；未公開於大廳之問卷缺少或不符即拒絕提交，由大廳進入時為 null */
    private String shareToken;

    /** 填答花費時間 (秒) */
    private Integer durationSeconds;

    @Valid
    /** 受訪者基本輪廓 */
    private SubmissionBasicInfoDto basicInfo;

    /** 各題答案清單 */
    @Builder.Default
    private List<@Valid SubmissionAnswerDto> answers = new ArrayList<>();
}
