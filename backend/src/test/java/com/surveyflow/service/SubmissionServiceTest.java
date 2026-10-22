/**
 * ============================================================================
 * 檔案名稱: SubmissionServiceTest.java
 * 所屬模組: Test 測試層 - 問卷填答業務單元測試
 * 責任描述: 採用 JUnit 5 與 Mockito 測試問卷填答提交、時效判定、題型答案封裝與受訪者輪廓持久化
 * 架構分層: Test Layer / Unit Test
 * 聯動關係:
 *   - 測試目標: SubmissionService
 *   - 模擬對象: SubmissionRepository, SurveyRepository, ModelMapper
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.QuestionType;
import com.surveyflow.dto.submission.SubmissionAnswerDto;
import com.surveyflow.dto.submission.SubmissionBasicInfoDto;
import com.surveyflow.dto.submission.SubmissionRequest;
import com.surveyflow.dto.submission.SubmissionResponse;
import com.surveyflow.entity.*;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SurveyRepository surveyRepository;

    @Spy
    private ModelMapper modelMapper = new com.surveyflow.config.ModelMapperConfig().modelMapper();

    @InjectMocks
    private SubmissionService submissionService;

    private Survey activeSurvey;
    private SurveyQuestion singleQuestion;
    private SurveyQuestion booleanQuestion;
    private SurveyQuestion ratingQuestion;

    @BeforeEach
    void setUp() {
        activeSurvey = Survey.builder()
                .id(1L)
                .title("2026 數位產品體驗調查")
                .isOpen(true)
                .isPublicToLobby(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .questions(new ArrayList<>())
                .build();

        singleQuestion = SurveyQuestion.builder()
                .id(10L)
                .survey(activeSurvey)
                .questionCode("Q1")
                .type(QuestionType.SINGLE)
                .questionText("您每週使用幾次此產品？")
                .isRequired(true)
                .options(new ArrayList<>())
                .build();

        SurveyOption opt1 = SurveyOption.builder().id(101L).question(singleQuestion).optionText("1-2 次").sortOrder(1).build();
        SurveyOption opt2 = SurveyOption.builder().id(102L).question(singleQuestion).optionText("3-5 次").sortOrder(2).build();
        singleQuestion.getOptions().add(opt1);
        singleQuestion.getOptions().add(opt2);

        booleanQuestion = SurveyQuestion.builder()
                .id(20L)
                .survey(activeSurvey)
                .questionCode("Q2")
                .type(QuestionType.BOOLEAN)
                .questionText("您是否願意推薦給朋友？")
                .isRequired(true)
                .options(new ArrayList<>())
                .build();

        SurveyOption yesOpt = SurveyOption.builder().id(201L).question(booleanQuestion).optionText("是").sortOrder(1).build();
        SurveyOption noOpt = SurveyOption.builder().id(202L).question(booleanQuestion).optionText("否").sortOrder(2).build();
        booleanQuestion.getOptions().add(yesOpt);
        booleanQuestion.getOptions().add(noOpt);

        ratingQuestion = SurveyQuestion.builder()
                .id(30L)
                .survey(activeSurvey)
                .questionCode("Q3")
                .type(QuestionType.RATING)
                .questionText("整體滿意度評分")
                .isRequired(true)
                .minRating(1)
                .maxRating(5)
                .options(new ArrayList<>())
                .build();

        activeSurvey.getQuestions().add(singleQuestion);
        activeSurvey.getQuestions().add(booleanQuestion);
        activeSurvey.getQuestions().add(ratingQuestion);
    }

    @Test
    @DisplayName("受訪者成功送出問卷填答（包含基本個資與各題型答案）")
    void submitSurvey_Success() {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(activeSurvey));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission sub = invocation.getArgument(0);
            sub.setId(888L);
            return sub;
        });

        SubmissionBasicInfoDto basicInfo = SubmissionBasicInfoDto.builder()
                .name("王小明")
                .gender("male")
                .ageGroup("25-34歲")
                .email("ming@example.com")
                .phone("0912345678")
                .build();

        List<SubmissionAnswerDto> answers = List.of(
                SubmissionAnswerDto.builder().questionId(10L).selectedOptionId(101L).build(),
                SubmissionAnswerDto.builder().questionId(20L).selectedOptionId(201L).build(),
                SubmissionAnswerDto.builder().questionId(30L).ratingValue(5).build()
        );

        SubmissionRequest request = SubmissionRequest.builder()
                .surveyId(1L)
                .durationSeconds(45)
                .basicInfo(basicInfo)
                .answers(answers)
                .build();

        SubmissionResponse response = submissionService.submitSurvey(request, "192.168.1.1");

        assertNotNull(response);
        assertEquals(888L, response.getSubmissionId());
        assertEquals(1L, response.getSurveyId());
        assertNotNull(response.getMessage());
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }

    @Test
    @DisplayName("填答不存在之問卷應拋出 IllegalArgumentException")
    void submitSurvey_SurveyNotFound() {
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(999L)).thenReturn(Optional.empty());

        SubmissionRequest request = SubmissionRequest.builder().surveyId(999L).build();

        assertThrows(IllegalArgumentException.class, () -> submissionService.submitSurvey(request, "127.0.0.1"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("問卷尚未開放（isOpen = false）應拒絕提交")
    void submitSurvey_SurveyClosed() {
        activeSurvey.setIsOpen(false);
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(activeSurvey));

        SubmissionRequest request = SubmissionRequest.builder().surveyId(1L).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> submissionService.submitSurvey(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("尚未開放"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("問卷已過截止期限應拒絕提交")
    void submitSurvey_SurveyExpired() {
        activeSurvey.setEndDate(LocalDateTime.now().minusDays(1));
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(1L)).thenReturn(Optional.of(activeSurvey));

        SubmissionRequest request = SubmissionRequest.builder().surveyId(1L).build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> submissionService.submitSurvey(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("截止"));
        verify(submissionRepository, never()).save(any());
    }
}
