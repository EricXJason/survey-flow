/**
 * ============================================================================
 * 檔案名稱: UserCredentialSynchronizer.java
 * 所屬模組: Configuration 系統安全性與帳號憑證動態同步模組
 * 責任描述: 系統啟動時讀取 application.properties 所設定之管理員與測試會員帳密，
 *           自動於資料庫建立帳號或同步最新 Argon2id 密碼雜湊，確保隨時可依設定檔登入
 * 架構分層: Configuration / Bootstrap Security Layer
 * 聯動關係:
 *   - 上游依賴: application.properties, UserRepository, PasswordEncoder
 *   - 下游消費: 系統啟動事件 (CommandLineRunner)
 * 邊界處理: 僅精準同步帳號與密碼，不變動其他實體業務問卷或作答紀錄
 * ============================================================================
 */

package com.surveyflow.config;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.entity.User;
import com.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCredentialSynchronizer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin.username:admin}")
    private String adminUsername;

    @Value("${app.security.admin.password:Admin123!}")
    private String adminPassword;

    @Value("${app.security.admin.email:admin@surveyflow.com}")
    private String adminEmail;

    @Value("${app.security.admin.fullname:系統最高管理員}")
    private String adminFullName;

    @Value("${app.security.test-user.username:testuser}")
    private String testUsername;

    @Value("${app.security.test-user.password:User1234!}")
    private String testPassword;

    @Value("${app.security.test-user.email:testuser@surveyflow.com}")
    private String testEmail;

    @Value("${app.security.test-user.fullname:測試會員小明}")
    private String testFullName;

    /**
     * 於 Spring Boot 啟動就緒後執行管理員與測試會員帳密動態同步
     * @param args 命令列參數
     */
    @Override
    @Transactional
    public void run(String... args) {
        log.info("🔐 正在自 application.properties 讀取帳號密碼配置並同步至資料庫...");

        // 1. 同步管理員帳號
        syncUser(adminUsername, adminEmail, adminPassword, adminFullName, "0912345678", RoleType.ROLE_ADMIN);

        // 2. 同步測試一般會員帳號
        syncUser(testUsername, testEmail, testPassword, testFullName, "0987654321", RoleType.ROLE_USER);

        log.info("✅ 帳號憑證同步完成：管理員 [{}] 與測試會員 [{}] 可直接依設定檔密碼登入", adminUsername, testUsername);
    }

    /**
     * 同步單一使用者憑證 (若存在則更新最新密碼雜湊，若不存在則直接新建)
     */
    private void syncUser(String username, String email, String rawPassword, String fullName, String phone, RoleType role) {
        userRepository.findByUsername(username).ifPresentOrElse(existingUser -> {
            existingUser.setPasswordHash(passwordEncoder.encode(rawPassword));
            existingUser.setEmail(email);
            existingUser.setFullName(fullName);
            existingUser.setRole(role);
            existingUser.setStatus("ACTIVE");
            userRepository.save(existingUser);
            log.info("已同步使用者 [{}] 之最新密碼雜湊與個人設定", username);
        }, () -> {
            User newUser = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .fullName(fullName)
                    .phone(phone)
                    .role(role)
                    .status("ACTIVE")
                    .build();
            userRepository.save(newUser);
            log.info("已於資料庫建立全新使用者帳號 [{}]", username);
        });
    }
}
