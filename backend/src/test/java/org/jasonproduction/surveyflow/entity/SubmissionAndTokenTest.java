/**
 * ============================================================================
 * 檔案名稱: SubmissionAndTokenTest.java
 * 測試對象: Submission (基本資料與作答明細綁定) 與 VerificationToken (有效判定、核銷) 之業務方法
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionAndTokenTest {

    @Test
    @DisplayName("Submission.assignBasicInfo：建立雙向關聯；submittedAt 預設為建立當下")
    void submission_AssignBasicInfo() {
        Submission submission = Submission.builder().build();
        SubmissionBasicInfo info = SubmissionBasicInfo.builder().name("王小明").build();

        submission.assignBasicInfo(info);

        assertSame(info, submission.getBasicInfo());
        assertSame(submission, info.getSubmission());
        assertNotNull(submission.getSubmittedAt());
    }

    @Test
    @DisplayName("Submission.replaceAnswers：以新清單取代作答明細")
    void submission_ReplaceAnswers() {
        Submission submission = Submission.builder().build();

        submission.replaceAnswers(List.of(SubmissionAnswer.builder().textAnswer("A").build()));
        submission.replaceAnswers(List.of(SubmissionAnswer.builder().textAnswer("B").build()));

        assertEquals(1, submission.getAnswers().size());
        assertEquals("B", submission.getAnswers().get(0).getTextAnswer());
    }

    @Test
    @DisplayName("VerificationToken：未使用且未過期為有效；核銷後無效")
    void verificationToken_ValidUntilUsed() {
        VerificationToken token = VerificationToken.builder().expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        assertTrue(token.isValid());

        token.markUsed();

        assertFalse(token.isValid());
    }

    @Test
    @DisplayName("VerificationToken：已過期或沒有到期時間皆為無效")
    void verificationToken_ExpiredIsInvalid() {
        assertFalse(VerificationToken.builder().expiresAt(LocalDateTime.now().minusSeconds(1)).build().isValid());
        assertFalse(VerificationToken.builder().build().isValid());
    }
}
