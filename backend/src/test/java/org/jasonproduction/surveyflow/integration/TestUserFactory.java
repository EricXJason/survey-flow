/**
 * ============================================================================
 * 檔案名稱: TestUserFactory.java
 * 測試對象: (共用工具) 整合測試中建立已提交之會員與問卷資料
 * 說明: 併發測試與 Security 測試需要「真的寫進資料庫並提交」的資料 (多執行緒、多個請求才看得到)，
 *       因此不能使用 @Transactional 回滾；由本元件以唯一帳號建立，互不干擾。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.integration;

import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestComponent
public class TestUserFactory {

    /** 測試會員一律使用之密碼 */
    public static final String PASSWORD = "Password1!";

    private final UserRepository userRepository;
    private final SurveyRepository surveyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 建構子注入
     * @param userRepository 會員 Repository
     * @param surveyRepository 問卷 Repository
     * @param passwordEncoder Argon2id 雜湊器 (與正式環境相同)
     */
    public TestUserFactory(UserRepository userRepository, SurveyRepository surveyRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.surveyRepository = surveyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 建立一位啟用中之會員 (密碼為 PASSWORD)
     * @param username 帳號 (建議以 IntegrationTestSupport.uniqueName 產生)
     * @param role 角色
     * @return 已提交之會員
     */
    public User createUser(String username, RoleType role) {
        return userRepository.save(User.builder().username(username).email(username + "@it.test")
                .passwordHash(passwordEncoder.encode(PASSWORD)).fullName("整合測試會員")
                .role(role).status(UserStatus.ACTIVE).build());
    }

    /**
     * 建立一份開放中、公開、不限期之問卷 (無題目)
     * @param owner 建立者
     * @return 已提交之問卷 (version 為 0)
     */
    public Survey createOpenSurvey(User owner) {
        return surveyRepository.save(Survey.builder().creator(owner).title("整合測試問卷").isOpen(true).isPublicToLobby(true).build());
    }
}
