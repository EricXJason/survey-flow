/**
 * ============================================================================
 * 檔案名稱: DataInitializer.java
 * 所屬模組: Configuration 系統啟動資料初始化模組
 * 責任描述: 系統啟動時自動檢查並注入種子會員 (Admin/User) 與範例問卷，實現本地開箱即用
 * 架構分層: Configuration / Bootstrap Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, SurveyRepository, PasswordEncoder
 *   - 下游消費: 系統啟動事件 (CommandLineRunner)
 * 邊界處理: 僅於 users 表為空時注入，防止重複寫入覆蓋使用者既有資料
 * ============================================================================
 */

package com.surveyflow.config;

import com.surveyflow.common.enums.QuestionType;
import com.surveyflow.common.enums.RoleType;
import com.surveyflow.common.enums.SurveyStatus;
import com.surveyflow.entity.*;
import com.surveyflow.repository.SurveyRepository;
import com.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SurveyRepository surveyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("資料庫已具備使用者種子資料，跳過初始化步驟。");
            return;
        }

        log.info("🚀 檢測到全新資料庫，啟動 SurveyFlow 種子資料初始化程序...");

        // 1. 建立預設管理員與會員
        User admin = User.builder()
                .username("admin")
                .email("admin@surveyflow.com")
                .passwordHash(passwordEncoder.encode("Admin123!"))
                .fullName("系統最高管理員")
                .phone("0912345678")
                .role(RoleType.ROLE_ADMIN)
                .status("ACTIVE")
                .build();

        User user = User.builder()
                .username("user1")
                .email("user1@surveyflow.com")
                .passwordHash(passwordEncoder.encode("User1234!"))
                .fullName("王小明")
                .phone("0987654321")
                .role(RoleType.ROLE_USER)
                .status("ACTIVE")
                .build();

        userRepository.save(admin);
        userRepository.save(user);

        // 2. 建立示範公開問卷
        Survey demoSurvey = Survey.builder()
                .creator(admin)
                .title("2026 企業內部協作與遠端辦公體驗調研")
                .description("感謝您參與本次調研，本問卷旨在評估新形態混合辦公機制之滿意度與協作效率，全數題目均嚴格匿名處理。")
                .category("員工與職場文化")
                .status(SurveyStatus.PUBLISHED)
                .startDate(LocalDateTime.now().minusDays(2))
                .endDate(LocalDateTime.now().plusDays(30))
                .viewCount(128)
                .build();

        // 基本資料設定
        SurveyBasicInfoConfig config = SurveyBasicInfoConfig.builder()
                .survey(demoSurvey)
                .requireName(true)
                .requireGender(true)
                .requireAge(true)
                .requirePhone(false)
                .requireEmail(true)
                .requireIndustry(true)
                .requireResidence(true)
                .build();
        demoSurvey.setBasicInfoConfig(config);

        // 建立題目清單
        List<SurveyQuestion> questions = new ArrayList<>();

        // Q1: 單選題
        SurveyQuestion q1 = SurveyQuestion.builder()
                .survey(demoSurvey)
                .questionCode("Q1")
                .type(QuestionType.SINGLE)
                .questionText("您目前的每週遠端居家辦公天數為何？")
                .isRequired(true)
                .sortOrder(1)
                .build();
        List<SurveyOption> q1Options = List.of(
                SurveyOption.builder().question(q1).optionText("完全實體進辦公室 (0天)").sortOrder(1).build(),
                SurveyOption.builder().question(q1).optionText("1 至 2 天遠端").sortOrder(2).build(),
                SurveyOption.builder().question(q1).optionText("3 至 4 天遠端").sortOrder(3).build(),
                SurveyOption.builder().question(q1).optionText("完全遠端辦公 (5天)").sortOrder(4).build()
        );
        q1.setOptions(new ArrayList<>(q1Options));
        questions.add(q1);

        // Q2: 多選題
        SurveyQuestion q2 = SurveyQuestion.builder()
                .survey(demoSurvey)
                .questionCode("Q2")
                .type(QuestionType.MULTI)
                .questionText("在遠端協作過程中，您最常使用的通訊或專案管理軟體有哪些？(可複選)")
                .isRequired(true)
                .sortOrder(2)
                .build();
        List<SurveyOption> q2Options = List.of(
                SurveyOption.builder().question(q2).optionText("Slack / Discord").sortOrder(1).build(),
                SurveyOption.builder().question(q2).optionText("Microsoft Teams").sortOrder(2).build(),
                SurveyOption.builder().question(q2).optionText("Notion / Jira").sortOrder(3).build(),
                SurveyOption.builder().question(q2).optionText("Google Workspace").sortOrder(4).build()
        );
        q2.setOptions(new ArrayList<>(q2Options));
        questions.add(q2);

        // Q3: 評分量表題
        SurveyQuestion q3 = SurveyQuestion.builder()
                .survey(demoSurvey)
                .questionCode("Q3")
                .type(QuestionType.RATING)
                .questionText("總體而言，您對目前團隊的遠端溝通流暢度滿意評分為？(1-5分)")
                .isRequired(true)
                .sortOrder(3)
                .minRating(1)
                .maxRating(5)
                .build();
        questions.add(q3);

        // Q4: 開放簡答題
        SurveyQuestion q4 = SurveyQuestion.builder()
                .survey(demoSurvey)
                .questionCode("Q4")
                .type(QuestionType.TEXT)
                .questionText("您認為公司在推行混合辦公制度上，目前最急需改善的痛點或建議為何？")
                .isRequired(false)
                .sortOrder(4)
                .build();
        questions.add(q4);

        demoSurvey.setQuestions(questions);
        surveyRepository.save(demoSurvey);

        log.info("✅ 種子資料初始化完成！");
        log.info("   - 管理員帳號: admin (密碼: Admin123!)");
        log.info("   - 會員帳號: user1 (密碼: User1234!)");
        log.info("   - 示範問卷已發布至大廳 (ID: {})", demoSurvey.getId());
    }
}
