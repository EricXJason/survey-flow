/**
 * ============================================================================
 * 檔案名稱: UserService.java
 * 所屬模組: Service 業務邏輯層 - 會員實體模組
 * 責任描述: 實現 User 實體專屬之個人檔案查詢、個人資料更新、登入密碼變更及管理員會員管理商務邏輯 (嚴格貫徹分層架構與 SRP 原則)
 * 架構分層: Service Layer / Business Logic
 * 聯動關係:
 *   - 上游依賴: UserRepository, PasswordEncoder
 *   - 下游消費: UserController, AdminController
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.user.PasswordChangeRequest;
import com.surveyflow.dto.user.UserProfileResponse;
import com.surveyflow.dto.user.UserProfileUpdateRequest;
import com.surveyflow.entity.User;
import com.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import org.modelmapper.ModelMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    /**
     * 依使用者帳號取得個人檔案資料
     * @param username 會員登入帳號
     * @return 個人資料 DTO
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username) {
        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號: " + username));
        return convertToProfileResponse(user);
    }

    /**
     * 更新會員個人檔案資料 (姓名、手機、身分證)
     * @param username 會員登入帳號
     * @param request 更新內容載荷
     * @return 更新後之個人資料 DTO
     */
    @Transactional
    public UserProfileResponse updateProfile(String username, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號: " + username));

        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        // 身分證字號於系統中為法定實名綁定資料，註冊後永久鎖定，嚴禁後續變更

        user = userRepository.save(user);
        log.info("會員個人檔案更新成功: username={}, fullName={}", username, user.getFullName());
        return convertToProfileResponse(user);
    }

    /**
     * 會員修改登入密碼
     * @param username 會員登入帳號
     * @param request 包含原密碼與新密碼之請求
     */
    @Transactional
    public void changePassword(String username, PasswordChangeRequest request) {
        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號: " + username));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("目前原密碼輸入不正確，無法變更密碼");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("會員密碼變更成功: username={}", username);
    }

    /**
     * 查詢全站會員分頁列表 (Admin 專屬業務)
     * @param pageable 分頁參數
     * @return 會員分頁資料
     */
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsersForAdmin(Pageable pageable) {
        Page<User> users = userRepository.findAllUsersForAdminNative(pageable);
        return users.map(this::convertToProfileResponse);
    }

    /**
     * 修改特定會員帳號狀態 (ACTIVE / SUSPENDED)
     * @param userId 會員識別碼
     * @param status 新狀態
     */
    @Transactional
    public void updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員"));
        user.setStatus(status.toUpperCase());
        userRepository.save(user);
        log.info("管理員更新會員狀態: userId={}, status={}", userId, status);
    }

    /**
     * 管理員刪除或停權特定會員 (軟刪除)
     * @param userId 會員識別碼
     */
    @Transactional
    public void deleteUserByAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員"));
        if (user.getRole() == RoleType.ROLE_ADMIN) {
            throw new IllegalArgumentException("系統最高管理員帳號受保護，嚴禁刪除");
        }
        user.setDeletedAt(LocalDateTime.now());
        user.setStatus("DELETED");
        userRepository.save(user);
        log.info("管理員已將會員軟刪除: userId={}", userId);
    }

    /**
     * 實體轉換為 UserProfileResponse DTO (統一透過 ModelMapper 自動映射)
     * @param user User 實體物件
     * @return 轉換完成之 UserProfileResponse DTO，若為 null 則回傳 null
     */
    public UserProfileResponse convertToProfileResponse(User user) {
        if (user == null) return null;
        return modelMapper.map(user, UserProfileResponse.class);
    }
}
