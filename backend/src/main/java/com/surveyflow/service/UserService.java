/**
 * ============================================================================
 * 檔案名稱: UserService.java
 * 所屬模組: Service 業務邏輯層 - 會員實體模組
 * 責任描述: 實現 User 實體專屬之個人檔案查詢、個人資料更新與登入密碼變更商務邏輯 (嚴格貫徹 SRP 原則)
 * 架構分層: Service Layer / Business Logic
 * 聯動關係:
 *   - 上游依賴: UserRepository, PasswordEncoder
 *   - 下游消費: UserController, AdminController
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.dto.user.PasswordChangeRequest;
import com.surveyflow.dto.user.UserProfileResponse;
import com.surveyflow.dto.user.UserProfileUpdateRequest;
import com.surveyflow.entity.User;
import com.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
     * 實體轉換為 UserProfileResponse DTO
     */
    public UserProfileResponse convertToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .identityCard(user.getIdentityCard())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
