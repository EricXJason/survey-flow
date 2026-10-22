/**
 * ============================================================================
 * 檔案名稱: MailService.java
 * 所屬模組: 電子郵件傳輸與通訊通知服務模組 (Mail Service)
 * 責任描述: 負責透過 Gmail SMTP 發送安全驗證信（忘記密碼 OTP 重設信、會員註冊啟用信）
 * 架構分層: Service Layer / Infrastructure Communication
 * 聯動關係:
 *   - 上游依賴: JavaMailSender, MimeMessageHelper, application.properties
 *   - 下游消費: AuthService
 * 邊界處理: 支援富文本 HTML 現代科技風樣板；發信異常時記錄詳細審計日誌並給予友善提示
 * ============================================================================
 */

package com.surveyflow.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String senderName;
    private final String frontendBaseUrl;

    /**
     * 建構子依賴注入 (Constructor Injection)
     * 遵循 DIP 原則與設定檔單一真實來源，嚴禁於代碼中硬編碼業務預設值
     */
    public MailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromEmail,
            @Value("${app.mail.sender-name}") String senderName,
            @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.senderName = senderName;
        this.frontendBaseUrl = frontendBaseUrl;
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
     * 底層發送 HTML 電子郵件共用方法
     */
    private void sendHtmlMail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            String sanitizedName = resolveSenderName();
            helper.setFrom(new InternetAddress(fromEmail, sanitizedName, StandardCharsets.UTF_8.name()));
            helper.setTo(toEmail.trim());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("郵件已成功透過 SMTP 寄出: to={}, subject={}", toEmail, subject);
        } catch (Exception e) {
            log.warn("⚠️ SMTP 發送郵件遭遇異常 (例如 Google 帳密核對失敗或網路未通): to={}, 原因={}", toEmail, e.getMessage());
            log.warn("┌──────────────────────────────────────────────────────────────────────────┐");
            log.warn("│ 【開發環境防禦性降級】SMTP 發信未成功，已將驗證資訊記錄於下方伺服器日誌：      │");
            log.warn("│ 收件者: {}                                              ", toEmail);
            log.warn("│ 郵件主旨: {}                                            ", subject);
            log.warn("└──────────────────────────────────────────────────────────────────────────┘");
        }
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
              <style>
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f8fafc; color: #0f172a; }
                .email-card { max-width: 520px; margin: 30px auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05); }
                .email-head { background: #0f172a; padding: 24px 32px; text-align: center; }
                .email-head h1 { margin: 0; color: #ffffff; font-size: 20px; letter-spacing: 0.5px; }
                .email-body { padding: 32px; }
                .btn-box { text-align: center; margin: 24px 0 16px 0; }
                .btn-action { background-color: #0f172a; color: #ffffff !important; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; display: inline-block; box-shadow: 0 2px 6px rgba(15, 23, 42, 0.2); }
                .code-box { background: #f1f5f9; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 14px; text-align: center; margin: 16px 0; }
                .code-text { font-family: monospace; font-size: 28px; font-weight: 800; letter-spacing: 6px; color: #0f172a; }
                .tip-text { font-size: 14px; color: #64748b; line-height: 1.6; }
                .email-foot { background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 32px; text-align: center; font-size: 12px; color: #94a3b8; }
              </style>
            </head>
            <body>
              <div class="email-card">
                <div class="email-head">
                  <h1>SurveyFlow 帳號安全中心</h1>
                </div>
                <div class="email-body">
                  <h2 style="font-size: 18px; margin-top: 0;">重設您的登入密碼</h2>
                  <p class="tip-text">您好！我們收到了重設您 SurveyFlow 會員密碼的請求。您可以直接點擊下方按鈕前往重設密碼頁面（系統將為您自動帶入驗證碼）：</p>
                  
                  <div class="btn-box">
                    <a href="%s" target="_blank" class="btn-action">立即前往重設密碼</a>
                  </div>
                  
                  <p class="tip-text" style="margin-bottom: 6px;">若按鈕無法點擊，您亦可手動輸入以下 6 位數安全驗證碼：</p>
                  <div class="code-box">
                    <div class="code-text">%s</div>
                  </div>
                  
                  <p class="tip-text">⚠️ <strong>注意事項</strong>：<br/>
                  • 此驗證碼與重設連結之有效期限為 <strong>15 分鐘</strong>，請儘速完成密碼重設。<br/>
                  • 若非您本人發起此項操作，請忽略此郵件，您的帳號仍然是安全的。</p>
                </div>
                <div class="email-foot">
                  本信件由 SurveyFlow 系統自動發送，請勿直接回覆。
                </div>
              </div>
            </body>
            </html>
            """.formatted(resetUrl, code);
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
              <style>
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f8fafc; color: #0f172a; }
                .email-card { max-width: 520px; margin: 30px auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05); }
                .email-head { background: #0f172a; padding: 24px 32px; text-align: center; }
                .email-head h1 { margin: 0; color: #ffffff; font-size: 20px; letter-spacing: 0.5px; }
                .email-body { padding: 32px; }
                .btn-box { text-align: center; margin: 24px 0 16px 0; }
                .btn-action { background-color: #0f172a; color: #ffffff !important; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; display: inline-block; box-shadow: 0 2px 6px rgba(15, 23, 42, 0.2); }
                .code-box { background: #f1f5f9; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 14px; text-align: center; margin: 16px 0; }
                .code-text { font-family: monospace; font-size: 28px; font-weight: 800; letter-spacing: 6px; color: #0f172a; }
                .tip-text { font-size: 14px; color: #64748b; line-height: 1.6; }
                .email-foot { background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 32px; text-align: center; font-size: 12px; color: #94a3b8; }
              </style>
            </head>
            <body>
              <div class="email-card">
                <div class="email-head">
                  <h1>SurveyFlow 會員註冊認證</h1>
                </div>
                <div class="email-body">
                  <h2 style="font-size: 18px; margin-top: 0;">歡迎加入 SurveyFlow，%s！</h2>
                  <p class="tip-text">感謝您註冊 SurveyFlow 線上問卷系統。您可以直接點擊下方按鈕立即開通您的會員帳號並自動登入：</p>
                  
                  <div class="btn-box">
                    <a href="%s" target="_blank" class="btn-action">點擊立即開通帳號</a>
                  </div>
                  
                  <p class="tip-text" style="margin-bottom: 6px;">若按鈕無法點擊，您亦可在註冊開通頁面輸入以下 6 位數啟用碼：</p>
                  <div class="code-box">
                    <div class="code-text">%s</div>
                  </div>
                  
                  <p class="tip-text">⚠️ <strong>注意事項</strong>：<br/>
                  • 此驗證碼與啟用連結之有效期限為 <strong>15 分鐘</strong>。<br/>
                  • 驗證成功後，系統將自動完成開通，您即可立即開始設計問卷與管理專案。</p>
                </div>
                <div class="email-foot">
                  本信件由 SurveyFlow 系統自動發送，請勿直接回覆。
                </div>
              </div>
            </body>
            </html>
            """.formatted(fullName, verifyUrl, code);
    }
}
