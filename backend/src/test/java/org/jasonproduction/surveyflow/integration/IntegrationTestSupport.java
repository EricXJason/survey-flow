/**
 * ============================================================================
 * 檔案名稱: IntegrationTestSupport.java
 * 測試對象: (共用基底) 所有整合測試 (*IT) 之 Spring 容器與資料庫環境
 * 說明:
 *   - 以 Testcontainers 啟動一個真實 MySQL 8.0 Docker 容器 (整個測試執行期間只啟動一次)，
 *     Spring 啟動時執行 schema.sql + seed/01~05，因此每個整合測試都面對「與正式環境相同之資料表 + 種子資料」。
 *   - @SpringBootTest 載入完整應用程式 (含 Security 過濾鏈、交易、原生 SQL)，與單元測試最大差異在此。
 *   - 只以 @MockitoBean 取代最底層之 JavaMailSender (SMTP)，MailService 本身仍由 Spring 真實建立，
 *     可驗證其依賴注入正確；寄出之信件內容可由 sentMailHtml() 取得 (例如擷取驗證碼)。
 *   - 所有子類別設定相同，Spring 會快取並共用同一個容器內容，測試速度才不會變慢。
 *   - 子類別以建構子取得 Bean (src/test/resources/junit-platform.properties 設定 spring.test.constructor.autowire.mode=all)，
 *     不使用 @Autowired 欄位注入。
 * 執行方式: ./mvnw verify (需先啟動 Docker)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.integration;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUserFactory.class)
@ActiveProfiles("integration")
public abstract class IntegrationTestSupport {

    /**
     * 測試用 MySQL 容器 (版本與 TiDB Cloud 相容之 MySQL 8.0)
     * [Why]: 以 root 帳號連線，因 schema.sql 第一行為 DROP / CREATE DATABASE，需要建立資料庫之權限
     */
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36")
            .withDatabaseName("survey_flow")
            .withUsername("root")
            .withPassword("integration-test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        // [Why]: 以靜態區塊啟動一次並不手動停止 (Testcontainers 的 Ryuk 會在 JVM 結束時清理)，
        //        所有測試類別共用同一個容器與 Spring 內容，避免每個類別重啟資料庫
        MYSQL.start();
    }

    /** 以 Mock 取代 SMTP 寄信元件 (整合測試設定 app.mail.provider=SMTP，不會呼叫外部 REST API) */
    @MockitoBean
    protected JavaMailSender javaMailSender;

    /**
     * 讓 Mock 之 JavaMailSender 可建立空白信件 (MailService 以 MimeMessageHelper 填入內容後呼叫 send)
     */
    @BeforeEach
    void allowMailSending() {
        when(javaMailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    /**
     * 取得最近一封寄出信件之 HTML 內容
     * @return HTML 字串
     * @throws Exception 讀取信件失敗
     */
    protected String lastSentMailHtml() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeastOnce()).send(captor.capture());
        return extractHtml(captor.getValue().getContent());
    }

    /**
     * 從信件內容中取出 6 碼驗證碼 (信件中唯一一組獨立之 6 位數字)
     * @return 驗證碼
     * @throws Exception 讀取信件失敗
     */
    protected String lastSentVerificationCode() throws Exception {
        Matcher matcher = Pattern.compile("code=(\\d{6})").matcher(lastSentMailHtml());
        if (!matcher.find()) {
            throw new IllegalStateException("信件中找不到驗證碼");
        }
        return matcher.group(1);
    }

    /**
     * 遞迴取出 Multipart 信件中之文字內容
     * @param content 信件或區段內容
     * @return 文字內容
     * @throws Exception 讀取失敗
     */
    private static String extractHtml(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        StringBuilder html = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                html.append(extractHtml(multipart.getBodyPart(i).getContent()));
            }
        }
        return html.toString();
    }

    /**
     * 將容器之連線資訊注入 Spring 設定 (取代正式環境之 SPRING_DATASOURCE_* 環境變數)
     * @param registry 動態設定註冊器
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        String separator = MYSQL.getJdbcUrl().contains("?") ? "&" : "?";
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + separator
                + "useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Taipei");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    /**
     * 產生測試專用之唯一帳號 (避免不同測試、不同執行輪次之資料互相衝突)
     * @param prefix 帳號前綴 (英數字)
     * @return 例如 it_lock_1a2b3c4d
     */
    protected static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
