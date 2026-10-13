/**
 * ============================================================================
 * 檔案名稱: AdminController.java
 * 所屬模組: Controller 控制器層 - 系統管理員專屬 (Admin)
 * 責任描述: 提供系統管理員 (ROLE_ADMIN) 專屬之全站會員管理、問卷稽核與全站維運數據 API
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, SurveyRepository, SubmissionRepository, AnalyticsService
 *   - 下游消費: 前端 UserManagementComponent, SurveyManagementComponent (Admin 視角)
 * 邊界處理: 標註 @PreAuthorize("hasRole('ADMIN')") 進行嚴格角色防護，未授權者回傳 403
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import com.surveyflow.dto.survey.SurveySummaryResponse;
import com.surveyflow.dto.user.UserProfileResponse;
import com.surveyflow.entity.Survey;
import com.surveyflow.entity.User;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import com.surveyflow.repository.UserRepository;
import com.surveyflow.service.AnalyticsService;
import com.surveyflow.service.SurveyService;
import com.surveyflow.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final SurveyRepository surveyRepository;
    private final SubmissionRepository submissionRepository;
    private final SurveyService surveyService;
    private final AnalyticsService analyticsService;

    private final UserService userService;

    /**
     * 查詢全站會員分頁列表 (Admin 專屬)
     * @param pageable 分頁參數
     * @return 會員分頁資料
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserProfileResponse>>> getAllUsers(
            @PageableDefault(size = 15, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<User> users = userRepository.findAllUsersForAdminNative(pageable);
        Page<UserProfileResponse> responsePage = users.map(userService::convertToProfileResponse);
        return ResponseEntity.ok(ApiResponse.ok(responsePage, "成功取得全站會員名冊"));
    }

    /**
     * 修改特定會員帳號狀態 (ACTIVE / SUSPENDED)
     * @param userId 會員識別碼
     * @param status 新狀態
     * @return 操作結果
     */
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員"));
        user.setStatus(status.toUpperCase());
        userRepository.save(user);
        log.info("管理員更新會員狀態: userId={}, status={}", userId, status);
        return ResponseEntity.ok(ApiResponse.ok("會員狀態已成功更新"));
    }

    /**
     * 管理員刪除或停權特定會員
     * @param userId 會員識別碼
     * @return 操作確認回應
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員"));
        if (user.getRole() == com.surveyflow.common.enums.RoleType.ROLE_ADMIN) {
            throw new IllegalArgumentException("系統最高管理員帳號受保護，嚴禁刪除");
        }
        user.setDeletedAt(LocalDateTime.now());
        user.setStatus("DELETED");
        userRepository.save(user);
        log.info("管理員已將會員軟刪除: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok("會員帳號已成功移除"));
    }

    /**
     * 查詢全站所有問卷清單 (Admin 專屬)
     * @param pageable 分頁參數
     * @return 全站問卷列表
     */
    @GetMapping("/surveys")
    public ResponseEntity<ApiResponse<Page<SurveySummaryResponse>>> getAllSurveys(
            @PageableDefault(size = 15, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Survey> page = surveyRepository.findAllSurveysForAdminNative(pageable);
        Page<SurveySummaryResponse> responsePage = page.map(s -> SurveySummaryResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .category(s.getCategory())
                .status(s.getStatus())
                .authorName(s.getCreator() != null ? s.getCreator().getFullName() : "未知")
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .viewCount(s.getViewCount())
                .submissionCount(submissionRepository.countBySurveyIdNative(s.getId()))
                .questionCount(s.getQuestions() != null ? s.getQuestions().size() : 0)
                .createdAt(s.getCreatedAt())
                .build());
        return ResponseEntity.ok(ApiResponse.ok(responsePage, "成功取得全站問卷列表"));
    }

    /**
     * 管理員強制下架或軟刪除問卷
     * @param surveyId 問卷識別碼
     * @param userDetails 管理員身分
     * @return 刪除結果
     */
    @DeleteMapping("/surveys/{surveyId}")
    public ResponseEntity<ApiResponse<Void>> forceDeleteSurvey(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        surveyService.deleteSurvey(surveyId, userDetails.getUsername());
        log.info("管理員強制刪除問卷: surveyId={}, admin={}", surveyId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("問卷已強制下架移除"));
    }

    /**
     * 全站維運與營運概覽統計 KPI
     * @return 全站統計數據
     */
    @GetMapping("/analytics/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformOverview() {
        long totalUsers = userRepository.count();
        long totalSurveys = surveyRepository.count();
        long totalSubmissions = submissionRepository.countTotalSubmissionsNative();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalSurveys", totalSurveys);
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("serverTime", LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.ok(stats, "全站營運總覽統計取得成功"));
    }

    /**
     * 管理員調閱任何問卷之完整數據報表
     * @param surveyId 問卷識別碼
     * @param userDetails 管理員身分
     * @return 數據報表
     */
    @GetMapping("/analytics/surveys/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyAnalyticsResponse>> getSurveyAnalyticsForAdmin(
            @PathVariable Long surveyId,
            @AuthenticationPrincipal UserDetails userDetails) {
        SurveyAnalyticsResponse response = analyticsService.getSurveyAnalytics(surveyId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(response, "成功取得問卷數據分析報表"));
    }
}
