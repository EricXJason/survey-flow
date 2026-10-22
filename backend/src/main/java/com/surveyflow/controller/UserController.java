/**
 * ============================================================================
 * 檔案名稱: UserController.java
 * 所屬模組: Controller 控制器層 - 會員實體模組
 * 責任描述: 提供一般會員個人檔案查詢、個人資料更新與登入密碼變更之 RESTful API 端點 (嚴格貫徹一對一 SRP 原則)
 * 架構分層: Presentation / Controller Layer
 * 聯動關係:
 *   - 上游依賴: UserService
 *   - 下游消費: 前端 UserProfileComponent, UserEmitterService, UserStateService
 * ============================================================================
 */

package com.surveyflow.controller;

import com.surveyflow.common.ApiResponse;
import com.surveyflow.dto.user.PasswordChangeRequest;
import com.surveyflow.dto.user.UserProfileResponse;
import com.surveyflow.dto.user.UserProfileUpdateRequest;
import com.surveyflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 查詢當前登入會員之個人檔案資料 (需登入)
     * @param principal 系統憑證實體 (Spring Security 注入)
     * @return 個人資料 DTO
     */
    @GetMapping({"/profile", "/me"})
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(java.security.Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("未授權之存取請求");
        }
        UserProfileResponse response = userService.getProfile(principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(response, "成功獲取個人資料"));
    }

    /**
     * 更新當前登入會員之個人檔案資料 (姓名、電話、身分證，需登入)
     * @param request 個人資料更新請求
     * @param principal 系統憑證實體 (Spring Security 注入)
     * @return 更新後之個人資料 DTO
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest request,
            java.security.Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("未授權之存取請求");
        }
        UserProfileResponse response = userService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok(response, "個人資料已成功更新"));
    }

    /**
     * 修改當前登入會員之登入密碼 (需登入)
     * @param request 包含原密碼與新密碼之請求
     * @param principal 系統憑證實體 (Spring Security 注入)
     * @return 操作確認回應
     */
    @PutMapping({"/password", "/change-password"})
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            java.security.Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("未授權之存取請求");
        }
        userService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok("登入密碼已成功變更"));
    }
}
