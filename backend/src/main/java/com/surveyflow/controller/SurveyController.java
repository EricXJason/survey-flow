/**
 * ============================================================================
 * 檔案名稱: SurveyController.java
 * 所屬模組: Controller 控制器層 - 問卷管理
 * 責任描述: 提供問卷大廳公開搜尋、問卷設計草稿存取、發布與專案維護之 RESTful API
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: SurveyService
 *   - 下游消費: 前端 LobbyComponent, SurveyDesignComponent, SurveyManagementComponent
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.survey.*;
import com.surveyflow.service.SurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    /**
     * 問卷大廳公開問卷分頁檢索端點 (免認證)
     * @param category 分類篩選
     * @param keyword 關鍵字模糊查詢
     * @param pageable 分頁參數 (預設依建立時間倒序)
     * @return 問卷概要分頁結果
     */
    @GetMapping("/lobby")
    public ResponseEntity<ApiResponse<Page<SurveySummaryResponse>>> getLobbySurveys(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SurveySummaryResponse> result = surveyService.searchLobbySurveys(category, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result, "成功獲取問卷大廳清單"));
    }

    /**
     * 受訪者進入填答頁獲取公開問卷結構端點 (免認證，自動累加瀏覽次數)
     * @param id 問卷 ID
     * @return 完整題目與選項結構
     */
    @GetMapping("/{id}/public")
    public ResponseEntity<ApiResponse<SurveyResponse>> getPublicSurvey(@PathVariable Long id) {
        SurveyResponse response = surveyService.getPublicSurvey(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "成功獲取問卷作答內容"));
    }

    /**
     * 會員後台管理專案清單端點 (需登入)
     * @param userDetails 當前登入會員
     * @param pageable 分頁參數
     * @return 該會員發布之問卷分頁列表
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<SurveySummaryResponse>>> getMySurveys(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SurveySummaryResponse> result = surveyService.getUserSurveys(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(result, "成功獲取我的問卷專案列表"));
    }

    /**
     * 查詢單一問卷完整結構詳情 (編輯或預覽使用，需登入)
     * @param id 問卷 ID
     * @return 問卷詳細資料
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyResponse>> getSurveyDetail(@PathVariable Long id) {
        SurveyResponse response = surveyService.getSurveyDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "成功獲取問卷詳情"));
    }

    /**
     * 建立新問卷專案 (需登入)
     * @param request 建立問卷請求
     * @param userDetails 當前登入會員
     * @return 建立成功後之問卷詳情
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SurveyResponse>> createSurvey(
            @Valid @RequestBody SurveyCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        SurveyResponse response = surveyService.createSurvey(request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(response, "問卷專案建立成功"));
    }

    /**
     * 修改更新既有問卷專案 (需登入且為擁有者)
     * @param id 問卷 ID
     * @param request 更新請求
     * @param userDetails 當前操作者
     * @return 更新後之問卷詳情
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyResponse>> updateSurvey(
            @PathVariable Long id,
            @RequestBody SurveyUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        SurveyResponse response = surveyService.updateSurvey(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(response, "問卷專案更新成功"));
    }

    /**
     * 軟刪除問卷專案 (需登入且為擁有者)
     * @param id 問卷 ID
     * @param userDetails 當前操作者
     * @return 刪除成功回應
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSurvey(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        surveyService.deleteSurvey(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("問卷專案已成功移除"));
    }
}
