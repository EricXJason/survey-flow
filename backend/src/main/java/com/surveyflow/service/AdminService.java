/**
 * ============================================================================
 * 檔案名稱: AdminService.java
 * 所屬模組: Service 業務服務層 - 全站營運與後台管理
 * 責任描述: 負責統籌系統管理員 (ADMIN) 專屬之會員治理、問卷審查與全站維運 KPI 業務流程
 * 架構分層: Service / Facade Layer
 * 聯動關係:
 *   - 上游依賴: AdminController
 *   - 下游消費: UserService, SurveyService, AnalyticsService
 * 邊界處理: 僅限具備 ROLE_ADMIN 權限之管理員調用，統一管理端點之業務流程與事務邊界
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.dto.analytics.SurveyAnalyticsResponse;
import com.surveyflow.dto.survey.SurveySummaryResponse;
import com.surveyflow.dto.user.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final SurveyService surveyService;
    private final AnalyticsService analyticsService;

    /**
     * 查詢全站會員分頁列表 (Admin 專屬業務)
     * @param pageable 分頁參數
     * @return 會員分頁資料
     */
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsers(Pageable pageable) {
        return userService.getAllUsersForAdmin(pageable);
    }

    /**
     * 修改特定會員帳號狀態 (ACTIVE / SUSPENDED)
     * @param userId 會員識別碼
     * @param status 新狀態
     */
    @Transactional
    public void updateUserStatus(Long userId, String status) {
        userService.updateUserStatus(userId, status);
    }

    /**
     * 管理員刪除或停權特定會員 (軟刪除)
     * @param userId 會員識別碼
     */
    @Transactional
    public void deleteUser(Long userId) {
        userService.deleteUserByAdmin(userId);
    }

    /**
     * 查詢全站所有問卷清單 (Admin 專屬)
     * @param pageable 分頁參數
     * @return 全站問卷列表
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> getAllSurveys(Pageable pageable) {
        return surveyService.getAllSurveysForAdmin(pageable);
    }

    /**
     * 管理員強制下架或軟刪除問卷
     * @param surveyId 問卷識別碼
     * @param adminUsername 管理員帳號
     */
    @Transactional
    public void forceDeleteSurvey(Long surveyId, String adminUsername) {
        surveyService.deleteSurvey(surveyId, adminUsername);
    }

    /**
     * 全站維運與營運概覽統計 KPI
     * @return 全站統計數據
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformOverview() {
        return analyticsService.getPlatformOverview();
    }

    /**
     * 管理員調閱任何問卷之完整數據報表
     * @param surveyId 問卷識別碼
     * @param adminUsername 管理員帳號
     * @return 數據報表
     */
    @Transactional(readOnly = true)
    public SurveyAnalyticsResponse getSurveyAnalytics(Long surveyId, String adminUsername) {
        return analyticsService.getSurveyAnalytics(surveyId, adminUsername);
    }
}
