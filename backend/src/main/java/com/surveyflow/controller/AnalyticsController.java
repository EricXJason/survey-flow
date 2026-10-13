/**
 * ============================================================================
 * 檔案名稱: AnalyticsController.java
 * 所屬模組: Controller 控制器層 - 數據分析
 * 責任描述: 提供問卷即時全維度數據報表 (KPI、走勢、人口畫像、題型統計) 之 RESTful API 端點
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: AnalyticsService
 *   - 下游消費: 前端 SurveyManagementComponent 數據分析儀表板
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import com.surveyflow.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 獲取單一問卷之全維度數據分析報表 (需登入且為問卷發布者或管理員)
     * @param surveyId 問卷 ID
     * @param userDetails 當前登入會員身分
     * @return 完整聚合指標、走勢與題型統計
     */
    @GetMapping("/surveys/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyAnalyticsResponse>> getSurveyAnalytics(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        SurveyAnalyticsResponse response = analyticsService.getSurveyAnalytics(surveyId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(response, "成功獲取問卷數據分析報表"));
    }
}
