/**
 * ============================================================================
 * 檔案名稱: MailServiceTest.java
 * 測試對象: MailService (寄送管道順序、失敗備援、全部失敗時拋出例外、信件內容跳脫與編碼)
 * 測試方式: JavaMailSender 與 HttpClient 以 Mock 取代，不會真的寄信或連線到 Brevo / Resend
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.jasonproduction.surveyflow.service.MailService.MailChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> httpResponse;

    /**
     * 依指定策略與金鑰建立受測物件 (寄件者 no-reply@surveyflow.tw、前端網址 https://app.example.com)
     * @param provider 寄送策略字串
     * @param brevoKey Brevo 金鑰
     * @param resendKey Resend 金鑰
     * @return MailService
     */
    private MailService mailService(String provider, String brevoKey, String resendKey) {
        return new MailService(mailSender, httpClient, " no-reply@surveyflow.tw ", "SurveyFlow 團隊",
                "https://app.example.com", brevoKey, resendKey, provider, 15);
    }

    /**
     * 讓 HttpClient 回傳指定狀態碼
     * @param status HTTP 狀態碼
     * @throws Exception Mock 宣告需要
     */
    private void givenHttpStatus(int status) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
    }

    /**
     * 讓 SMTP 可建立空白信件
     */
    private void givenSmtpAvailable() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    /**
     * 從 SMTP 實際送出之信件中取出 HTML 內文 (多層 Multipart 時遞迴尋找)
     * @param content MimeMessage 或 BodyPart 之內容
     * @return HTML 字串，找不到時為空字串
     * @throws Exception 讀取信件內容失敗
     */
    private String extractHtml(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder html = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                html.append(extractHtml(part.getContent()));
            }
            return html.toString();
        }
        return "";
    }

    @Test
    @DisplayName("寄送順序：AUTO 依有設定之金鑰決定 (Brevo → Resend → SMTP)，SMTP 永遠是最後備援")
    void resolveChannels_FollowsProviderSetting() {
        assertEquals(List.of(MailChannel.BREVO, MailChannel.RESEND, MailChannel.SMTP), mailService("auto", "k1", "k2").resolveChannels());
        assertEquals(List.of(MailChannel.SMTP), mailService("AUTO", "", null).resolveChannels());
        assertEquals(List.of(MailChannel.RESEND, MailChannel.SMTP), mailService("AUTO", " ", "k2").resolveChannels());
        assertEquals(List.of(MailChannel.BREVO, MailChannel.SMTP), mailService("BREVO", "k1", "").resolveChannels());
        assertEquals(List.of(MailChannel.RESEND, MailChannel.SMTP), mailService("RESEND", "", "k2").resolveChannels());
        assertEquals(List.of(MailChannel.SMTP), mailService("SMTP", "k1", "k2").resolveChannels());
    }

    @Test
    @DisplayName("寄送策略設定值不合法：建立時即拋出例外 (應用程式啟動失敗，及早發現設定錯誤)")
    void invalidProvider_FailsFast() {
        assertThrows(IllegalArgumentException.class, () -> mailService("GMAIL", "", ""));
    }

    @Test
    @DisplayName("Brevo 回應 2xx：呼叫 Brevo API (api-key 標頭) 後即結束，不再嘗試 SMTP")
    void brevoSuccess_StopsThere() throws Exception {
        // Given
        givenHttpStatus(201);

        // When
        mailService("AUTO", "brevo-key", "").sendPasswordResetMail("user@example.com", "123456");

        // Then
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("https://api.brevo.com/v3/smtp/email", request.getValue().uri().toString());
        assertEquals("brevo-key", request.getValue().headers().firstValue("api-key").orElseThrow());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Resend：以 Authorization: Bearer 金鑰呼叫 Resend API")
    void resendSuccess_UsesBearerHeader() throws Exception {
        // Given
        givenHttpStatus(200);

        // When
        mailService("RESEND", "", "resend-key").sendPasswordResetMail("user@example.com", "123456");

        // Then
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("https://api.resend.com/emails", request.getValue().uri().toString());
        assertEquals("Bearer resend-key", request.getValue().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    @DisplayName("Brevo 回應 500：改用 SMTP 備援寄出")
    void brevoFailure_FallsBackToSmtp() throws Exception {
        // Given
        givenHttpStatus(500);
        givenSmtpAvailable();

        // When
        mailService("BREVO", "brevo-key", "").sendPasswordResetMail("user@example.com", "123456");

        // Then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("HTTP 連線失敗也會改用下一個管道")
    void httpException_FallsBackToNextChannel() throws Exception {
        // Given
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("connection refused"));
        givenSmtpAvailable();

        // When
        mailService("AUTO", "brevo-key", "resend-key").sendPasswordResetMail("user@example.com", "123456");

        // Then：Brevo、Resend 各嘗試一次後由 SMTP 寄出
        verify(httpClient, times(2)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("所有管道都失敗：拋出 MailSendException (呼叫端交易回滾，API 回 503)")
    void allChannelsFail_ThrowsMailSendException() {
        // Given
        givenSmtpAvailable();
        doThrow(new MailSendException("SMTP 587 被封鎖")).when(mailSender).send(any(MimeMessage.class));

        // When / Then
        MailSendException ex = assertThrows(MailSendException.class,
                () -> mailService("SMTP", "", "").sendPasswordResetMail("user@example.com", "123456"));
        assertEquals("驗證信寄送失敗，請稍後再試", ex.getMessage());
    }

    @Test
    @DisplayName("註冊信內容：含驗證碼與直達連結；信箱經 URL 編碼、姓名經 HTML 跳脫 (防止信件被注入 HTML)")
    void registrationMail_EscapesUserInput() throws Exception {
        // Given
        givenSmtpAvailable();

        // When
        mailService("SMTP", "", "").sendRegistrationVerificationMail(" a+b@example.com ", "<b>駭客</b>", "654321");

        // Then
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        String html = extractHtml(sent.getValue().getContent());
        assertTrue(html.contains("654321"));
        assertTrue(html.contains("https://app.example.com/verify-email?email=a%2Bb%40example.com&code=654321"),
                "信箱前後空白須先去除，+ 與 @ 須經 URL 編碼");
        assertTrue(html.contains("&lt;b&gt;駭客&lt;/b&gt;"));
        assertFalse(html.contains("<b>駭客</b>"));
        assertEquals("a+b@example.com", sent.getValue().getAllRecipients()[0].toString());
    }

    @Test
    @DisplayName("重設密碼信內容：直達連結指向前端 /reset-password 並帶入驗證碼")
    void passwordResetMail_ContainsResetLink() throws Exception {
        // Given
        givenSmtpAvailable();

        // When
        mailService("SMTP", "", "").sendPasswordResetMail("user@example.com", "112233");

        // Then
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        String html = extractHtml(sent.getValue().getContent());
        assertTrue(html.contains("https://app.example.com/reset-password?email=user%40example.com"));
        assertTrue(html.contains("112233"));
    }

    @Test
    @DisplayName("API 回應 199 或 300 (非 2xx) 視為失敗，改用 SMTP；金鑰為 null 時視為未設定")
    void non2xxResponses_FallBack() throws Exception {
        givenSmtpAvailable();
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(199, 300);

        mailService("BREVO", "k", null).sendPasswordResetMail("a@example.com", "123456");
        mailService("BREVO", "k", null).sendPasswordResetMail("a@example.com", "123456");

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        assertEquals(List.of(MailChannel.SMTP), mailService("AUTO", null, null).resolveChannels());
    }
}
