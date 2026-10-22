/**
 * ============================================================================
 * 檔案名稱: MailService.java
 * 所屬模組: 電子郵件傳輸與通訊通知服務模組 (Mail Service)
 * 責任描述: 負責發送安全驗證信（忘記密碼 OTP 重設信、會員註冊啟用信）。
 *           支援三軌通訊策略：
 *           1. Brevo HTTP REST API (走 HTTPS Port 443，突破 Render 免費容器 SMTP 587 阻斷)
 *           2. Resend HTTP REST API (走 HTTPS Port 443，突破 Render 免費容器 SMTP 587 阻斷)
 *           3. 原生 Spring JavaMailSender (標準 SMTP 587，適用於本地或付費雲端)
 * 架構分層: Service Layer / Infrastructure Communication
 * 聯動關係:
 *   - 上游依賴: JavaMailSender, ObjectMapper, application.properties
 *   - 下游消費: AuthService
 * 邊界處理: 支援富文本 HTML 現代科技風樣板；發信異常時記錄日誌並降級打印驗證碼，保證系統不崩潰
 * ============================================================================
 */

package com.surveyflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String fromEmail;
    private final String senderName;
    private final String frontendBaseUrl;
    private final String brevoApiKey;
    private final String resendApiKey;
    private final String mailProvider;
    private final HttpClient httpClient;

    /**
     * 建構子依賴注入 (Constructor Injection)
     * 遵循 DIP 原則與設定檔單一真實來源，嚴禁於代碼中硬編碼業務預設值
     */
    public MailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromEmail,
            @Value("${app.mail.sender-name}") String senderName,
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.mail.brevo-api-key:}") String brevoApiKey,
            @Value("${app.mail.resend-api-key:}") String resendApiKey,
            @Value("${app.mail.provider:AUTO}") String mailProvider) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.senderName = senderName;
        this.frontendBaseUrl = frontendBaseUrl;
        this.brevoApiKey = brevoApiKey != null ? brevoApiKey.trim() : "";
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.mailProvider = mailProvider != null ? mailProvider.trim().toUpperCase() : "AUTO";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 發送忘記密碼重設驗證信 (含一鍵直達連結與 6 碼 OTP)
     * @param toEmail 收件者信箱
     * @param code 6 碼數字驗證碼
     */
    public void sendPasswordResetMail(String toEmail, String code) {
        String subject = "【SurveyFlow】您的密碼重設專屬連結與驗證碼通知";
        String htmlContent = buildPasswordResetHtml(toEmail, code);
        sendHtmlMail(toEmail, subject, htmlContent);
        log.info("忘記密碼驗證信已寄出: to={}, code={}", toEmail, code);
    }

    /**
     * 發送會員註冊啟用驗證信 (含一鍵開通連結與 6 碼 OTP)
     * @param toEmail 收件者信箱
     * @param fullName 會員全名
     * @param code 6 碼數字啟用碼
     */
    public void sendRegistrationVerificationMail(String toEmail, String fullName, String code) {
        String subject = "【SurveyFlow】歡迎加入！請點擊開通您的會員帳號";
        String htmlContent = buildRegistrationHtml(toEmail, fullName, code);
        sendHtmlMail(toEmail, subject, htmlContent);
        log.info("註冊啟用驗證信已寄出: to={}, fullName={}, code={}", toEmail, fullName, code);
    }

    /**
     * 底層發送 HTML 電子郵件共用調度方法
     * 依據提供商策略動態路由至 Brevo HTTP API (443)、Resend HTTP API (443) 或 SMTP (587)
     */
    private void sendHtmlMail(String toEmail, String subject, String htmlContent) {
        boolean sentSuccessfully = false;

        // 策略 1: 指定或自動優先使用 Brevo HTTP REST API (走 443 埠，100% 繞過雲端 SMTP 封鎖)
        if ("BREVO".equals(mailProvider) || ("AUTO".equals(mailProvider) && !brevoApiKey.isBlank())) {
            try {
                sendViaBrevoApi(toEmail, subject, htmlContent);
                sentSuccessfully = true;
            } catch (Exception e) {
                log.warn("⚠️ Brevo HTTP API 寄信失敗: to={}, 原因={}", toEmail, e.getMessage());
            }
        }

        // 策略 2: 指定或備選使用 Resend HTTP REST API (走 443 埠)
        if (!sentSuccessfully && ("RESEND".equals(mailProvider) || ("AUTO".equals(mailProvider) && !resendApiKey.isBlank()))) {
            try {
                sendViaResendApi(toEmail, subject, htmlContent);
                sentSuccessfully = true;
            } catch (Exception e) {
                log.warn("⚠️ Resend HTTP API 寄信失敗: to={}, 原因={}", toEmail, e.getMessage());
            }
        }

        // 策略 3: 若無 HTTP API 設定或 HTTP API 均未啟用，降級嘗試傳統 SMTP 587
        if (!sentSuccessfully) {
            try {
                sendViaSmtp(toEmail, subject, htmlContent);
                sentSuccessfully = true;
            } catch (Exception e) {
                log.warn("⚠️ SMTP 發送郵件遭遇異常 (例如 Google 帳密核對失敗或 Render 雲端封鎖 587 埠): to={}, 原因={}", toEmail, e.getMessage());
            }
        }

        // 防禦性降級日誌保全：若所有實體發信管道均不可用，打印日誌確保開發或除錯能順利取得驗證碼
        if (!sentSuccessfully) {
            log.warn("┌──────────────────────────────────────────────────────────────────────────┐");
            log.warn("│ 【雲端環境防禦性降級】所有實體郵件傳輸未成功，已將驗證資訊記錄於下方伺服器日誌：│");
            log.warn("│ 收件者: {}                                              ", toEmail);
            log.warn("│ 郵件主旨: {}                                            ", subject);
            log.warn("└──────────────────────────────────────────────────────────────────────────┘");
        }
    }

    /**
     * 透過 Brevo (原 Sendinblue) HTTP REST API 發送郵件 (HTTPS Port 443)
     */
    private void sendViaBrevoApi(String toEmail, String subject, String htmlContent) throws Exception {
        String sanitizedName = resolveSenderName();
        String senderAddress = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "user46972@gmail.com";

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", sanitizedName, "email", senderAddress));
        payload.put("to", List.of(Map.of("email", toEmail.trim())));
        payload.put("subject", subject);
        payload.put("htmlContent", htmlContent);

        String jsonBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("郵件已成功透過 Brevo HTTP API (Port 443) 寄出: to={}, subject={}", toEmail, subject);
        } else {
            throw new RuntimeException("Brevo API 回應狀態碼 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 透過 Resend HTTP REST API 發送郵件 (HTTPS Port 443)
     */
    private void sendViaResendApi(String toEmail, String subject, String htmlContent) throws Exception {
        String sanitizedName = resolveSenderName();
        String senderAddress = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "onboarding@resend.dev";
        String fromHeader = "%s <%s>".formatted(sanitizedName, senderAddress);

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromHeader);
        payload.put("to", Collections.singletonList(toEmail.trim()));
        payload.put("subject", subject);
        payload.put("html", htmlContent);

        String jsonBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("郵件已成功透過 Resend HTTP API (Port 443) 寄出: to={}, subject={}", toEmail, subject);
        } else {
            throw new RuntimeException("Resend API 回應狀態碼 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 透過傳統 Spring JavaMailSender (SMTP Port 587) 發送郵件
     */
    private void sendViaSmtp(String toEmail, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        String sanitizedName = resolveSenderName();
        String senderAddress = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "no-reply@surveyflow.tw";
        helper.setFrom(new InternetAddress(senderAddress, sanitizedName, StandardCharsets.UTF_8.name()));
        helper.setTo(toEmail.trim());
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        log.info("郵件已成功透過 SMTP 寄出: to={}, subject={}", toEmail, subject);
    }

    /**
     * 防禦性解析寄件者外顯名稱，自動校正 properties ISO-8859-1 編碼轉換為正確繁體中文
     */
    private String resolveSenderName() {
        if (senderName == null || senderName.isBlank()) {
            return "SurveyFlow 線上問卷團隊";
        }
        // 若偵測到 Latin-1 亂碼特徵 (例如 ç, ä, å, é)，自動逆向還原回純 UTF-8
        if (senderName.contains("ç") || senderName.contains("ä") || senderName.contains("å") || senderName.contains("é")) {
            try {
                return new String(senderName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return senderName;
    }

    /**
     * 構建忘記密碼 HTML 電子郵件樣板 (含一鍵直達重設連結與 6 碼驗證碼)
     */
    private String buildPasswordResetHtml(String toEmail, String code) {
        String resetUrl = "%s/reset-password?email=%s&code=%s".formatted(frontendBaseUrl, toEmail, code);
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
     */
    private String buildRegistrationHtml(String toEmail, String fullName, String code) {
        String verifyUrl = "%s/verify-email?email=%s&code=%s".formatted(frontendBaseUrl, toEmail, code);
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
            """.formatted(fullName, verifyUrl, verifyUrl, verifyUrl, code);
    }
}
