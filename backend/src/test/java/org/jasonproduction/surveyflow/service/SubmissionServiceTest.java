/**
 * ============================================================================
 * 檔案名稱: SubmissionServiceTest.java
 * 測試對象: SubmissionService (填答時窗檢核、受訪者基本資料、各題型作答明細轉換與題目對應容錯)
 * 測試方式: JUnit 5 + Mockito；以 ArgumentCaptor 擷取實際要儲存的 Submission，逐筆檢查作答明細
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.jasonproduction.surveyflow.dto.submission.SubmissionAnswerDto;
import org.jasonproduction.surveyflow.dto.submission.SubmissionBasicInfoDto;
import org.jasonproduction.surveyflow.dto.submission.SubmissionRequest;
import org.jasonproduction.surveyflow.dto.submission.SubmissionResponse;
import org.jasonproduction.surveyflow.entity.Submission;
import org.jasonproduction.surveyflow.entity.SubmissionAnswer;
import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.SurveyOption;
import org.jasonproduction.surveyflow.entity.SurveyQuestion;
import org.jasonproduction.surveyflow.repository.SubmissionRepository;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SurveyRepository surveyRepository;

    private SubmissionService submissionService;
    private Survey survey;

    /**
     * 建立受測物件，以及一份含 5 種題型之開放中問卷：
     * Q1 單選 (id 10)、Q2 多選 (id 20)、Q3 是非 (id 30)、Q4 評分 (id 40)、Q5 文字 (id 50)
     */
    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(submissionRepository, surveyRepository);
        survey = Survey.builder().id(1L).title("產品體驗調查").isOpen(true).build();
        survey.replaceQuestions(List.of(
                question(10L, 1, QuestionType.SINGLE, option(101L, "A"), option(102L, "B")),
                question(20L, 2, QuestionType.MULTI, option(201L, "紅"), option(202L, "藍")),
                question(30L, 3, QuestionType.BOOLEAN, option(301L, "是，我同意"), option(302L, "否，我不同意")),
                question(40L, 4, QuestionType.RATING),
                question(50L, 5, QuestionType.TEXT)));
    }

    /**
     * 建立題目並綁定選項
     * @param id 題目 ID
     * @param sortOrder 排序序號
     * @param type 題型
     * @param options 選項
     * @return 題目實體
     */
    private SurveyQuestion question(Long id, int sortOrder, QuestionType type, SurveyOption... options) {
        SurveyQuestion question = SurveyQuestion.builder().id(id).sortOrder(sortOrder).type(type).questionText("Q" + sortOrder).build();
        question.replaceOptions(new ArrayList<>(List.of(options)));
        return question;
    }

    /**
     * 建立選項
     * @param id 選項 ID
     * @param text 選項文字
     * @return 選項實體
     */
    private SurveyOption option(Long id, String text) {
        return SurveyOption.builder().id(id).optionText(text).build();
    }

    /**
     * 送出只含指定作答之填答，並回傳實際要儲存之 Submission
     * @param answers 作答清單
     * @return 傳給 submissionRepository.save 的物件
     */
    private Submission submit(SubmissionAnswerDto... answers) {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(survey));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        submissionService.submitSurvey(SubmissionRequest.builder().surveyId(1L).answers(List.of(answers)).build(), "127.0.0.1");
        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        return saved.getValue();
    }

    @Nested
    @DisplayName("時窗與基本資料")
    class WindowAndBasicInfo {

        @Test
        @DisplayName("成功送出：記錄 IP、作答秒數與受訪者基本資料，回傳資料庫配發之填答 ID")
        void submit_SavesSubmissionWithBasicInfo() {
            // Given
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(survey));
            when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
                Submission toSave = invocation.getArgument(0);
                ReflectionTestUtils.setField(toSave, "id", 888L);
                return toSave;
            });
            SubmissionRequest request = SubmissionRequest.builder()
                    .surveyId(1L)
                    .durationSeconds(95)
                    .basicInfo(SubmissionBasicInfoDto.builder().name("王小明").gender("男").height(172.5).build())
                    .build();

            // When
            SubmissionResponse response = submissionService.submitSurvey(request, "203.0.113.5");

            // Then
            ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
            verify(submissionRepository).save(saved.capture());
            assertEquals("203.0.113.5", saved.getValue().getIpAddress());
            assertEquals(95, saved.getValue().getDurationSeconds());
            assertEquals("王小明", saved.getValue().getBasicInfo().getName());
            assertEquals(172.5, saved.getValue().getBasicInfo().getHeight());
            assertSame(saved.getValue(), saved.getValue().getBasicInfo().getSubmission());
            assertEquals(888L, response.getSubmissionId());
            assertEquals(1L, response.getSurveyId());
        }

        @Test
        @DisplayName("問卷不存在：拋出 IllegalArgumentException")
        void surveyNotFound_Throws() {
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> submissionService.submitSurvey(SubmissionRequest.builder().surveyId(999L).build(), "ip"));
        }

        @Test
        @DisplayName("問卷已關閉：拋出 IllegalArgumentException 且不儲存")
        void closedSurvey_Throws() {
            // Given
            survey.patchSettings(null, null, false, null);
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(survey));

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> submissionService.submitSurvey(SubmissionRequest.builder().surveyId(1L).build(), "ip"));
            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("未公開於大廳之問卷：未帶分享代碼或代碼不符即拒絕且不儲存，代碼相符才寫入")
        void linkOnlySurvey_RequiresShareToken() {
            // Given：僅限連結之問卷，分享代碼為 SECRET
            Survey linkOnly = Survey.builder().id(1L).title("私人").isOpen(true).isPublicToLobby(false).shareToken("SECRET").build();
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(linkOnly));
            when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> submissionService.submitSurvey(SubmissionRequest.builder().surveyId(1L).build(), "ip"));
            assertThrows(IllegalArgumentException.class,
                    () -> submissionService.submitSurvey(SubmissionRequest.builder().surveyId(1L).shareToken("GUESS").build(), "ip"));
            verify(submissionRepository, never()).save(any());

            submissionService.submitSurvey(SubmissionRequest.builder().surveyId(1L).shareToken("SECRET").build(), "ip");
            verify(submissionRepository).save(any(Submission.class));
        }
    }

    @Nested
    @DisplayName("各題型作答轉換")
    class AnswerMapping {

        @Test
        @DisplayName("單選題帶選項 ID：綁定選項，文字存為選項文字")
        void single_BySelectedOptionId() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(10L).selectedOptionId(102L).build()).getAnswers().get(0);

            assertEquals(102L, answer.getOption().getId());
            assertEquals("B", answer.getTextAnswer());
        }

        @Test
        @DisplayName("單選題只帶文字：忽略大小寫與空白反向比對選項")
        void single_ByText() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(10L).textAnswer(" a ").build()).getAnswers().get(0);

            assertEquals(101L, answer.getOption().getId());
        }

        @Test
        @DisplayName("多選題帶選項 ID 清單：每個勾選選項各一筆明細")
        void multi_BySelectedOptionIds() {
            List<SubmissionAnswer> answers = submit(SubmissionAnswerDto.builder().questionId(20L).selectedOptionIds(List.of(201L, 202L)).build()).getAnswers();

            assertEquals(2, answers.size());
            assertEquals(List.of("紅", "藍"), answers.stream().map(SubmissionAnswer::getTextAnswer).toList());
        }

        @Test
        @DisplayName("多選題以逗號分隔文字送出：拆分後逐一比對選項")
        void multi_ByCommaSeparatedText() {
            List<SubmissionAnswer> answers = submit(SubmissionAnswerDto.builder().questionId(20L).textAnswer("紅, 藍").build()).getAnswers();

            assertEquals(List.of(201L, 202L), answers.stream().map(a -> a.getOption().getId()).toList());
        }

        @Test
        @DisplayName("多選題文字完全比對不到：保留原始文字一筆，不綁定選項")
        void multi_UnmatchedText_KeepsRawText() {
            List<SubmissionAnswer> answers = submit(SubmissionAnswerDto.builder().questionId(20L).textAnswer(" 綠 ").build()).getAnswers();

            assertEquals(1, answers.size());
            assertNull(answers.get(0).getOption());
            assertEquals("綠", answers.get(0).getTextAnswer());
        }

        @Test
        @DisplayName("是非題送 true / 0：正規化為「是 / 否」後寬鬆比對到含該字之選項")
        void boolean_NormalizesAndMatchesLoosely() {
            List<SubmissionAnswer> answers = submit(
                    SubmissionAnswerDto.builder().questionId(30L).textAnswer("true").build(),
                    SubmissionAnswerDto.builder().questionId(30L).textAnswer("0").build()).getAnswers();

            assertEquals(301L, answers.get(0).getOption().getId());
            assertEquals(302L, answers.get(1).getOption().getId());
        }

        @Test
        @DisplayName("是非題文字無法辨識：保留原文字，不綁定選項")
        void boolean_UnknownText() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(30L).textAnswer("不確定").build()).getAnswers().get(0);

            assertNull(answer.getOption());
            assertEquals("不確定", answer.getTextAnswer());
        }

        @Test
        @DisplayName("評分題帶 ratingValue：直接存分數")
        void rating_ByValue() {
            assertEquals(5, submit(SubmissionAnswerDto.builder().questionId(40L).ratingValue(5).build()).getAnswers().get(0).getRatingValue());
        }

        @Test
        @DisplayName("評分題只帶文字：以 RatingScoreParser 解析分數")
        void rating_ByText() {
            assertEquals(5, submit(SubmissionAnswerDto.builder().questionId(40L).textAnswer("非常滿意").build()).getAnswers().get(0).getRatingValue());
        }

        @Test
        @DisplayName("文字題：原樣儲存，不解析分數")
        void text_StoredAsIs() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(50L).textAnswer("介面很好用").build()).getAnswers().get(0);

            assertEquals("介面很好用", answer.getTextAnswer());
            assertNull(answer.getRatingValue());
        }
    }

    @Nested
    @DisplayName("題目對應三段容錯")
    class QuestionResolution {

        @Test
        @DisplayName("questionId 不是題目 ID 但等於排序序號：以排序序號對應 (questionId 5 → 第 5 題文字題)")
        void fallbackToSortOrder() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(5L).textAnswer("備註").build()).getAnswers().get(0);

            assertEquals(50L, answer.getQuestion().getId());
        }

        @Test
        @DisplayName("ID 與序號都對不到：以作答在清單中的位置對應 (第 1 筆 → 第 1 題)")
        void fallbackToIndex() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(999L).selectedOptionId(101L).build()).getAnswers().get(0);

            assertEquals(10L, answer.getQuestion().getId());
        }

        @Test
        @DisplayName("三種方式都對不到 (作答筆數多於題數)：略過該筆，不建立明細")
        void unresolvable_IsSkipped() {
            // Given：6 筆作答，但問卷只有 5 題，第 6 筆的 ID / 序號 / 位置都對不到
            SubmissionAnswerDto[] answers = new SubmissionAnswerDto[6];
            for (int i = 0; i < 5; i++) {
                answers[i] = SubmissionAnswerDto.builder().questionId(50L).textAnswer("答案" + i).build();
            }
            answers[5] = SubmissionAnswerDto.builder().questionId(999L).textAnswer("多出來的").build();

            // When / Then
            assertEquals(5, submit(answers).getAnswers().size());
        }
    }

    @Nested
    @DisplayName("邊界情境")
    class EdgeCases {

        @Test
        @DisplayName("作答清單為 null 或空：只建立填答主記錄")
        void noAnswers() {
            assertEquals(0, submit().getAnswers().size());
        }

        @Test
        @DisplayName("多選題勾選不存在之選項 ID：仍建立明細但不綁定選項")
        void unknownSelectedOptionId() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(20L).selectedOptionIds(List.of(999L)).build()).getAnswers().get(0);

            assertNull(answer.getOption());
            assertNull(answer.getTextAnswer());
        }

        @Test
        @DisplayName("多選題文字為空白：視同文字作答原樣儲存")
        void multiBlankText() {
            assertEquals(" ", submit(SubmissionAnswerDto.builder().questionId(20L).textAnswer(" ").build()).getAnswers().get(0).getTextAnswer());
        }

        @Test
        @DisplayName("是非題 yes / false / no：正規化後綁定選項")
        void booleanOtherLiterals() {
            List<SubmissionAnswer> answers = submit(
                    SubmissionAnswerDto.builder().questionId(30L).textAnswer("YES").build(),
                    SubmissionAnswerDto.builder().questionId(30L).textAnswer("false").build(),
                    SubmissionAnswerDto.builder().questionId(30L).textAnswer("No").build(),
                    SubmissionAnswerDto.builder().questionId(30L).textAnswer("1").build()).getAnswers();

            assertEquals(List.of(301L, 302L, 302L, 301L), answers.stream().map(a -> a.getOption().getId()).toList());
        }

        @Test
        @DisplayName("單選題沒有任何作答文字與選項 ID：建立空白明細")
        void singleWithoutAnything() {
            SubmissionAnswer answer = submit(SubmissionAnswerDto.builder().questionId(10L).build()).getAnswers().get(0);

            assertNull(answer.getOption());
            assertNull(answer.getTextAnswer());
        }

        @Test
        @DisplayName("文字題帶 ratingValue 或評分題無文字：不從文字解析分數")
        void noRatingParsing() {
            List<SubmissionAnswer> answers = submit(
                    SubmissionAnswerDto.builder().questionId(50L).textAnswer("非常滿意").build(),
                    SubmissionAnswerDto.builder().questionId(40L).build()).getAnswers();

            assertNull(answers.get(0).getRatingValue());
            assertNull(answers.get(1).getRatingValue());
        }

        @Test
        @DisplayName("questionId 為 null：以作答在清單中的位置對應題目")
        void nullQuestionId_UsesIndex() {
            assertEquals(10L, submit(SubmissionAnswerDto.builder().questionId(null).selectedOptionId(101L).build())
                    .getAnswers().get(0).getQuestion().getId());
        }

        @Test
        @DisplayName("選項文字為 null 之資料：比對時略過，不拋出例外")
        void optionWithNullText() {
            survey.replaceQuestions(List.of(question(60L, 1, QuestionType.BOOLEAN, option(601L, null), option(602L, "是"))));

            assertEquals(602L, submit(SubmissionAnswerDto.builder().questionId(60L).textAnswer("是").build()).getAnswers().get(0).getOption().getId());
        }

        @Test
        @DisplayName("題目沒有排序序號：依序號比對時略過該題")
        void questionWithoutSortOrder() {
            survey.replaceQuestions(List.of(SurveyQuestion.builder().id(70L).type(QuestionType.TEXT).sortOrder(null).build()));

            // questionId 1 不是題目 ID，也找不到序號 1，最後以位置 0 對應到題目 70
            assertEquals(70L, submit(SubmissionAnswerDto.builder().questionId(1L).textAnswer("a").build()).getAnswers().get(0).getQuestion().getId());
        }
    }
}
