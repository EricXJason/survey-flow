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

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.common.util.PiiMasker;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.dto.user.PasswordChangeRequest;
import org.jasonproduction.surveyflow.dto.user.UserProfileResponse;
import org.jasonproduction.surveyflow.dto.user.UserProfileUpdateRequest;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

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
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateProfile(String username, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號: " + username));

        user.updateProfile(
                request.getFullName().trim(),
                request.getPhone() != null ? request.getPhone().trim() : null,
                request.getIdentityCard() != null ? request.getIdentityCard().trim() : null);

        user = userRepository.save(user);
        log.info("會員個人檔案更新成功: username={}, fullName={}", username, user.getFullName());
        eventPublisher.publishEvent(new AuditEvent(AuditAction.PROFILE_UPDATED, username, AuditEvent.TARGET_USER, String.valueOf(user.getId()), null));
        return convertToProfileResponse(user);
    }

    /**
     * 會員修改登入密碼
     * @param username 會員登入帳號
     * @param request 包含原密碼與新密碼之請求
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String username, PasswordChangeRequest request) {
        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員帳號: " + username));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("目前原密碼輸入不正確，無法變更密碼");
        }

        user.changePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("會員密碼變更成功: username={}", username);
        eventPublisher.publishEvent(new AuditEvent(AuditAction.PASSWORD_CHANGED, username, AuditEvent.TARGET_USER, String.valueOf(user.getId()), null));
    }

    /**
     * 查詢全站會員分頁列表 (Admin 專屬業務)
     * @param pageable 分頁參數
     * @return 會員分頁資料
     */
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsersForAdmin(Pageable pageable) {
        Page<User> users = userRepository.findAllUsersForAdminNative(pageable);
        // [Why]: 管理員清單只需辨識帳號，手機與身分證以遮罩回傳 (見 PiiMasker)；會員本人之 getProfile 仍為完整值
        return users.map(user -> convertToProfileResponse(user).toBuilder()
                .phone(PiiMasker.maskPhone(user.getPhone()))
                .identityCard(PiiMasker.maskIdentityCard(user.getIdentityCard()))
                .build());
    }

    /**
     * 修改特定會員帳號狀態 (ACTIVE / SUSPENDED)
     * @param userId 會員識別碼
     * @param status 新狀態 (不分大小寫)
     * @throws IllegalArgumentException 查無會員或狀態值不合法
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, String status) {
        User user = userRepository.findByIdNative(userId)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員"));
        user.changeStatus(UserStatus.valueOf(status.toUpperCase(Locale.ROOT)));
        userRepository.save(user);
        log.info("管理員更新會員狀態: userId={}, status={}", userId, status);
        eventPublisher.publishEvent(new AuditEvent(AuditAction.USER_STATUS_CHANGED, null, AuditEvent.TARGET_USER, String.valueOf(userId),
                user.getUsername() + " → " + user.getStatus().name()));
    }

    /**
     * 管理員刪除或停權特定會員 (軟刪除)
     * @param userId 會員識別碼
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserByAdmin(Long userId) {
        User user = userRepository.findByIdNative(userId)
                .orElseThrow(() -> new IllegalArgumentException("查無此會員"));
        if (user.getRole() == RoleType.ROLE_ADMIN) {
            throw new IllegalArgumentException("系統最高管理員帳號受保護，嚴禁刪除");
        }
        user.softDelete(LocalDateTime.now());
        userRepository.save(user);
        log.info("管理員已將會員軟刪除: userId={}", userId);
        eventPublisher.publishEvent(new AuditEvent(AuditAction.USER_DELETED, null, AuditEvent.TARGET_USER, String.valueOf(userId), user.getUsername()));
    }

    /**
     * 實體轉換為 UserProfileResponse DTO (不含密碼雜湊)
     * @param user User 實體物件
     * @return 個人資料 DTO
     */
    private UserProfileResponse convertToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .identityCard(user.getIdentityCard())
                .role(user.getRole())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
