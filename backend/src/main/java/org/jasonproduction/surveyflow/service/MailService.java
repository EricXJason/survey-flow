/**
 * ============================================================================
 * 檔案名稱: MailService.java
 * 所屬模組: 電子郵件通知服務模組 (Mail Service)
 * 責任描述: 寄送帳號安全驗證信 (註冊啟用信、忘記密碼重設信)，信件含一鍵直達連結與 6 碼驗證碼。
 *           依 app.mail.provider 決定寄送管道與順序 (前一個失敗才試下一個)：
 *             AUTO   → Brevo (有金鑰時) → Resend (有金鑰時) → SMTP
 *             BREVO  → Brevo → SMTP
 *             RESEND → Resend → SMTP
 *             SMTP   → SMTP
 * 架構分層: Service Layer / Infrastructure Communication
 * 聯動關係:
 *   - 上游依賴: JavaMailSender (SMTP)、HttpClient (HttpClientConfig，呼叫 Brevo / Resend REST API)、
 *               application.properties (寄件者、金鑰、前端網址、逾時)
 *   - 下游消費: AuthService (註冊、補發驗證碼、忘記密碼)
 * 邊界處理: [Why]: 雲端平臺 (Render 免費方案) 封鎖 SMTP 587，正式環境以 HTTPS 443 之 REST API 為主力。
 *           所有管道皆失敗時拋出 MailSendException (回應 503)，讓呼叫端交易回滾，而非假裝寄送成功。
 *           驗證碼明碼只寫入信件內容，不寫入任何日誌；信件中之姓名做 HTML 跳脫、連結中之信箱做 URL 編碼。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class MailService {

    /** 寄送策略設定值 (app.mail.provider)：AUTO 代表依已設定之金鑰自動決定管道順序 */
    enum MailProvider { AUTO, BREVO, RESEND, SMTP }

    /** 實際寄送管道 */
    enum MailChannel { BREVO, RESEND, SMTP }

    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final JavaMailSender mailSender;
    private final HttpClient httpClient;
    /**
     * 組裝 REST API 請求內容之 JSON 工具
     * [Why]: Spring Boot 4 預設以 Jackson 3 處理 HTTP JSON，容器中沒有 Jackson 2 之 ObjectMapper Bean，
     *        故在此自行建立 (執行緒安全，可重複使用)，不透過建構子注入
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String fromEmail;
    private final String senderName;
    private final String frontendBaseUrl;
    private final String brevoApiKey;
    private final String resendApiKey;
    private final MailProvider mailProvider;
    private final Duration requestTimeout;

    /**
     * 建構子注入所有依賴與設定 (設定值皆來自 application.properties，程式內不寫任何預設信箱或名稱)
     * @param mailSender SMTP 寄信元件
     * @param httpClient 呼叫 REST API 之 HTTP 用戶端
     * @param fromEmail 寄件者信箱 (正式環境須與 Brevo 驗證之寄件者一致)
     * @param senderName 寄件者顯示名稱
     * @param frontendBaseUrl 前端網址，用於組裝信件中之直達連結
     * @param brevoApiKey Brevo 金鑰，未使用時為空字串
     * @param resendApiKey Resend 金鑰，未使用時為空字串
     * @param mailProvider 寄送策略 (AUTO / BREVO / RESEND / SMTP，不分大小寫；非法值啟動即失敗)
     * @param requestTimeoutSeconds 單次 REST API 請求逾時秒數
     */
    public MailService(JavaMailSender mailSender,
                       HttpClient httpClient,
                       @Value("${app.mail.from}") String fromEmail,
                       @Value("${app.mail.sender-name}") String senderName,
                       @Value("${app.frontend.base-url}") String frontendBaseUrl,
                       @Value("${app.mail.brevo-api-key}") String brevoApiKey,
                       @Value("${app.mail.resend-api-key}") String resendApiKey,
                       @Value("${app.mail.provider}") String mailProvider,
                       @Value("${app.mail.request-timeout-seconds}") long requestTimeoutSeconds) {
        this.mailSender = mailSender;
        this.httpClient = httpClient;
        this.fromEmail = fromEmail.trim();
        this.senderName = senderName;
        this.frontendBaseUrl = frontendBaseUrl;
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.resendApiKey = resendApiKey == null ? "" : resendApiKey.trim();
        this.mailProvider = MailProvider.valueOf(mailProvider.trim().toUpperCase(Locale.ROOT));
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    /**
     * 寄送忘記密碼重設信 (含一鍵直達重設頁連結與 6 碼驗證碼)
     * @param toEmail 收件者信箱
     * @param code 6 碼數字驗證碼
     * @throws MailSendException 所有寄送管道皆失敗
     */
    public void sendPasswordResetMail(String toEmail, String code) {
        String recipient = toEmail.trim();
        String subject = "【SurveyFlow】您的密碼重設專屬連結與驗證碼通知";
        sendHtmlMail(recipient, subject, buildPasswordResetHtml(recipient, code));
    }

    /**
     * 寄送會員註冊啟用信 (含一鍵開通連結與 6 碼啟用碼)
     * @param toEmail 收件者信箱
     * @param fullName 會員姓名 (顯示於信件問候語)
     * @param code 6 碼數字啟用碼
     * @throws MailSendException 所有寄送管道皆失敗
     */
    public void sendRegistrationVerificationMail(String toEmail, String fullName, String code) {
        String recipient = toEmail.trim();
        String subject = "【SurveyFlow】歡迎加入！請點擊開通您的會員帳號";
        sendHtmlMail(recipient, subject, buildRegistrationHtml(recipient, fullName, code));
    }

    /**
     * 依寄送策略逐一嘗試各管道，任一成功即結束；全部失敗則拋出例外
     * @param toEmail 收件者信箱 (已 trim)
     * @param subject 信件主旨
     * @param htmlContent HTML 信件內容
     * @throws MailSendException 所有管道皆失敗
     */
    private void sendHtmlMail(String toEmail, String subject, String htmlContent) {
        for (MailChannel channel : resolveChannels()) {
            try {
                deliverVia(channel, toEmail, subject, htmlContent);
                log.info("郵件寄送成功: channel={}, to={}, subject={}", channel, toEmail, subject);
                return;
            } catch (Exception e) {
                log.warn("郵件寄送失敗，改試下一個管道: channel={}, to={}, 原因={}", channel, toEmail, e.getMessage());
            }
        }
        log.error("所有郵件管道皆寄送失敗: to={}, subject={}", toEmail, subject);
        throw new MailSendException("驗證信寄送失敗，請稍後再試");
    }

    /**
     * 依 app.mail.provider 決定實際嘗試之管道順序 (SMTP 永遠是最後備援)
     * @return 依序嘗試之管道清單
     */
    List<MailChannel> resolveChannels() {
        return switch (mailProvider) {
            case BREVO -> List.of(MailChannel.BREVO, MailChannel.SMTP);
            case RESEND -> List.of(MailChannel.RESEND, MailChannel.SMTP);
            case SMTP -> List.of(MailChannel.SMTP);
            case AUTO -> {
                List<MailChannel> channels = new ArrayList<>();
                if (!brevoApiKey.isBlank()) {
                    channels.add(MailChannel.BREVO);
                }
                if (!resendApiKey.isBlank()) {
                    channels.add(MailChannel.RESEND);
                }
                channels.add(MailChannel.SMTP);
                yield channels;
            }
        };
    }

    /**
     * 透過指定管道寄出一封信
     * @param channel 寄送管道 (BREVO / RESEND / SMTP)
     * @param toEmail 收件者信箱 (已 trim)
     * @param subject 信件主旨
     * @param htmlContent HTML 信件內容
     * @throws Exception 該管道寄送失敗 (連線錯誤、非 2xx 回應、SMTP 例外)
     */
    private void deliverVia(MailChannel channel, String toEmail, String subject, String htmlContent) throws Exception {
        switch (channel) {
            case BREVO -> postJson(BREVO_ENDPOINT, "api-key", brevoApiKey, Map.of(
                    "sender", Map.of("name", senderName, "email", fromEmail),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", htmlContent));
            case RESEND -> postJson(RESEND_ENDPOINT, "Authorization", "Bearer " + resendApiKey, Map.of(
                    "from", "%s <%s>".formatted(senderName, fromEmail),
                    "to", List.of(toEmail),
                    "subject", subject,
                    "html", htmlContent));
            case SMTP -> sendViaSmtp(toEmail, subject, htmlContent);
        }
    }

    /**
     * 以 JSON 呼叫郵件服務 REST API，非 2xx 回應視為失敗
     * @param endpoint API 網址
     * @param authHeaderName 驗證標頭名稱 (Brevo 為 api-key，Resend 為 Authorization)
     * @param authHeaderValue 驗證標頭值
     * @param payload 請求內容
     * @throws IOException 連線失敗或回應狀態碼非 2xx
     * @throws InterruptedException 等待回應時執行緒被中斷
     */
    private void postJson(String endpoint, String authHeaderName, String authHeaderValue, Map<String, Object> payload)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header(authHeaderName, authHeaderValue)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .timeout(requestTimeout)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API 回應狀態碼 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 透過 SMTP (JavaMailSender) 寄出 HTML 信件
     * @param toEmail 收件者信箱
     * @param subject 信件主旨
     * @param htmlContent HTML 信件內容
     * @throws Exception 建立信件或 SMTP 傳送失敗
     */
    private void sendViaSmtp(String toEmail, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(fromEmail, senderName, StandardCharsets.UTF_8.name()));
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    /**
     * 將信箱做 URL 編碼後放入連結查詢參數 (避免信箱中之 + 等字元破壞連結)
     * @param value 原始字串
     * @return URL 編碼後字串
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 構建忘記密碼 HTML 電子郵件樣板 (含一鍵直達重設連結與 6 碼驗證碼)
     * @param toEmail 收件者信箱
     * @param code 6 碼 OTP 驗證碼
     * @return 完整 HTML 郵件內容字串
     */
    private String buildPasswordResetHtml(String toEmail, String code) {
        String resetUrl = "%s/reset-password?email=%s&code=%s".formatted(frontendBaseUrl, urlEncode(toEmail), code);
        return """
            <!DOCTYPE html>
            <html lang="zh-TW">
            <head>
              <meta charset="UTF-8">
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f8fafc; color: #0f172a;">
              <div style="max-width: 520px; margin: 30px auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);">
                <div style="background: #0f172a; padding: 24px 32px; text-align: center;">
                  <h1 style="margin: 0; color: #ffffff; font-size: 20px; letter-spacing: 0.5px;">SurveyFlow 帳號安全中心</h1>
                </div>
                <div style="padding: 32px;">
                  <h2 style="font-size: 18px; margin-top: 0; color: #0f172a;">重設您的登入密碼</h2>
                  <p style="font-size: 14px; color: #475569; line-height: 1.6;">您好！我們收到了重設您 SurveyFlow 會員密碼的請求。請直接點擊下方按鈕前往重設密碼（系統將自動為您帶入信箱與驗證碼）：</p>
                  
                  <div style="text-align: center; margin: 28px 0 16px 0;">
                    <a href="%s" target="_blank" style="background-color: #0f172a; color: #ffffff !important; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; display: inline-block;">立即前往重設密碼</a>
                  </div>
                  
                  <p style="font-size: 13px; color: #64748b; line-height: 1.6; word-break: break-all; margin: 12px 0 20px 0; text-align: center;">
                    或直接點擊/複製專屬連結：<br/>
                    <a href="%s" target="_blank" style="color: #2563eb; text-decoration: underline;">%s</a>
                  </p>
                  
                  <p style="font-size: 14px; color: #475569; margin-bottom: 6px;">若連結無法開啟，您亦可手動輸入以下 6 位數安全驗證碼：</p>
                  <div style="background: #f1f5f9; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 14px; text-align: center; margin: 12px 0;">
                    <div style="font-family: monospace; font-size: 28px; font-weight: 800; letter-spacing: 6px; color: #0f172a;">%s</div>
                  </div>
                  
                  <p style="font-size: 13px; color: #64748b; line-height: 1.6; margin-top: 20px;">⚠️ <strong>注意事項</strong>：<br/>
                  • 此驗證碼與專屬連結有效期限為 <strong>15 分鐘</strong>，請儘速完成密碼重設。<br/>
                  • 若非您本人發起此項操作，請忽略此郵件，您的帳號仍然是安全的。</p>
                </div>
                <div style="background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 32px; text-align: center; font-size: 12px; color: #94a3b8;">
                  本信件由 SurveyFlow 系統自動發送，請勿直接回覆。
                </div>
              </div>
            </body>
            </html>
            """.formatted(resetUrl, resetUrl, resetUrl, code);
    }

    /**
     * 構建會員註冊啟用 HTML 電子郵件樣板 (含一鍵開通連結與 6 碼 OTP)
     * @param toEmail 收件者信箱
     * @param fullName 會員全名
     * @param code 6 碼數字啟用碼
     * @return 完整 HTML 郵件內容字串
     */
    private String buildRegistrationHtml(String toEmail, String fullName, String code) {
        String verifyUrl = "%s/verify-email?email=%s&code=%s".formatted(frontendBaseUrl, urlEncode(toEmail), code);
        return """
            <!DOCTYPE html>
            <html lang="zh-TW">
            <head>
              <meta charset="UTF-8">
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f8fafc; color: #0f172a;">
              <div style="max-width: 520px; margin: 30px auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);">
                <div style="background: #0f172a; padding: 24px 32px; text-align: center;">
                  <h1 style="margin: 0; color: #ffffff; font-size: 20px; letter-spacing: 0.5px;">SurveyFlow 會員註冊認證</h1>
                </div>
                <div style="padding: 32px;">
                  <h2 style="font-size: 18px; margin-top: 0; color: #0f172a;">歡迎加入 SurveyFlow，%s！</h2>
                  <p style="font-size: 14px; color: #475569; line-height: 1.6;">感謝您註冊 SurveyFlow 線上問卷系統。您可以直接點擊下方按鈕立即開通您的會員帳號並自動登入：</p>
                  
                  <div style="text-align: center; margin: 28px 0 16px 0;">
                    <a href="%s" target="_blank" style="background-color: #0f172a; color: #ffffff !important; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; display: inline-block;">點擊立即開通帳號</a>
                  </div>
                  
                  <p style="font-size: 13px; color: #64748b; line-height: 1.6; word-break: break-all; margin: 12px 0 20px 0; text-align: center;">
                    或直接點擊/複製專屬連結：<br/>
                    <a href="%s" target="_blank" style="color: #2563eb; text-decoration: underline;">%s</a>
                  </p>
                  
                  <p style="font-size: 14px; color: #475569; margin-bottom: 6px;">若連結無法開啟，您亦可在開通頁面手動輸入以下 6 位數啟用碼：</p>
                  <div style="background: #f1f5f9; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 14px; text-align: center; margin: 12px 0;">
                    <div style="font-family: monospace; font-size: 28px; font-weight: 800; letter-spacing: 6px; color: #0f172a;">%s</div>
                  </div>
                  
                  <p style="font-size: 13px; color: #64748b; line-height: 1.6; margin-top: 20px;">⚠️ <strong>注意事項</strong>：<br/>
                  • 此驗證碼與開通連結有效期限為 <strong>15 分鐘</strong>。<br/>
                  • 驗證成功後，系統將自動完成開通，您即可立即開始設計問卷與管理專案。</p>
                </div>
                <div style="background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 32px; text-align: center; font-size: 12px; color: #94a3b8;">
                  本信件由 SurveyFlow 系統自動發送，請勿直接回覆。
                </div>
              </div>
            </body>
            </html>
            """.formatted(HtmlUtils.htmlEscape(fullName), verifyUrl, verifyUrl, verifyUrl, code);
    }
}
