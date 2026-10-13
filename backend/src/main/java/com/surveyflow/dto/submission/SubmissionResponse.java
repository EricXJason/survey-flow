/**
 * ============================================================================
 * 檔案名稱: SubmissionResponse.java
 * 所屬模組: DTO 資料傳輸層 - 填答模組
 * 責任描述: 封裝填答提交成功後之回應結果
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SubmissionService
 *   - 下游消費: 前端感謝頁 / 填答完成對話框
 * ============================================================================
 */

package com.surveyflow.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private Long submissionId;
    private Long surveyId;
    private String message;
    private LocalDateTime submittedAt;
}
