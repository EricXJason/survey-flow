/**
 * ============================================================================
 * 檔案名稱: SubmissionController.java
 * 所屬模組: Controller 控制器層 - 填答送出
 * 責任描述: 提供受訪者提交問卷作答之公開 RESTful API 端點
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: SubmissionService
 *   - 下游消費: 前端 SurveyRunnerComponent
 * 邊界處理: 免 JWT 登入即可填答，並自 HTTP 請求中提取 IP 用於審計與防刷
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.submission.SubmissionRequest;
import com.surveyflow.dto.submission.SubmissionResponse;
import com.surveyflow.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * 受訪者提交填答結果端點 (免認證)
     * @param request 填答內容
     * @param httpRequest HTTP 請求物件 (用於提取客戶端 IP)
     * @return 提交成功確認
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SubmissionResponse>> submitSurvey(
            @Valid @RequestBody SubmissionRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        SubmissionResponse response = submissionService.submitSurvey(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok(response, "問卷填答已成功提交！"));
    }

    /**
     * 提取客戶端真實 IP (相容反向代理如 Nginx 或 AWS ALB)
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
