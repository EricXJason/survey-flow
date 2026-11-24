/**
 * ============================================================================
 * 檔案名稱: SubmissionService.java
 * 所屬模組: Service 業務邏輯層 - 填答模組
 * 責任描述: 處理受訪者填答提交、作答時間紀錄、基本輪廓存儲與多題型作答明細持久化
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: SubmissionRepository, SurveyRepository
 *   - 下游消費: SubmissionController
 * 邊界處理: 問卷需為開放狀態且當前時間介於起訖時窗內 (Survey.ensureAcceptingSubmissions)，否則阻斷提交；
 *           作答對應題目採三段容錯 (題目 ID → 排序序號 → 作答索引)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.converter.RatingScoreParser;
import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.jasonproduction.surveyflow.dto.submission.*;
import org.jasonproduction.surveyflow.entity.*;
import org.jasonproduction.surveyflow.repository.SubmissionRepository;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SurveyRepository surveyRepository;

    /** 是非題正規化後之「是 / 否」文字 */
    private static final String BOOLEAN_YES = "是";
    private static final String BOOLEAN_NO = "否";

    /**
     * 處理受訪者送出問卷填答
     * @param request 填答請求資料
     * @param clientIp 客戶端 IP 位址
     * @return 提交結果回應
     * @throws IllegalArgumentException 問卷不存在、未公開於大廳且分享代碼不符、關閉或不在填答時窗內
     */
    @Transactional(rollbackFor = Exception.class)
    public SubmissionResponse submitSurvey(SubmissionRequest request, String clientIp) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(request.getSurveyId())
                .orElseThrow(() -> new IllegalArgumentException("填答之問卷不存在或已刪除"));
        // [Why]: 提交端點只收問卷 ID，若不核對分享代碼，任何人仍可對猜到 ID 的私人問卷直接灌入填答
        survey.ensureReachableBy(request.getShareToken());

        LocalDateTime now = LocalDateTime.now();
        survey.ensureAcceptingSubmissions(now);

        Submission submission = Submission.builder()
                .survey(survey)
                .ipAddress(clientIp)
                .durationSeconds(request.getDurationSeconds())
                .submittedAt(now)
                .build();

        if (request.getBasicInfo() != null) {
            submission.assignBasicInfo(toBasicInfoEntity(request.getBasicInfo()));
        }
        if (request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            submission.replaceAnswers(buildAnswers(submission, survey, request.getAnswers()));
        }

        submission = submissionRepository.save(submission);
        log.info("受訪者成功提交問卷填答: submissionId={}, surveyId={}", submission.getId(), survey.getId());

        return SubmissionResponse.builder()
                .submissionId(submission.getId())
                .surveyId(survey.getId())
                .message("感謝您的填答！資料已成功記錄。")
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    /**
     * 將全部作答 DTO 轉換為作答明細實體；找不到對應題目之作答直接略過
     * @param submission 所屬填答主記錄
     * @param survey 所屬問卷
     * @param answerDtos 前端送出之作答清單
     * @return 作答明細實體清單
     */
    private List<SubmissionAnswer> buildAnswers(Submission submission, Survey survey, List<SubmissionAnswerDto> answerDtos) {
        List<SubmissionAnswer> answers = new ArrayList<>();
        List<SurveyQuestion> surveyQuestions = survey.getQuestions();

        for (int answerIndex = 0; answerIndex < answerDtos.size(); answerIndex++) {
            SubmissionAnswerDto answerDto = answerDtos.get(answerIndex);
            Optional<SurveyQuestion> question = resolveQuestion(surveyQuestions, answerDto, answerIndex);
            if (question.isEmpty()) {
                continue;
            }
            SurveyQuestion target = question.get();
            QuestionType type = target.getType();

            if (answerDto.getSelectedOptionIds() != null && !answerDto.getSelectedOptionIds().isEmpty()) {
                answers.addAll(buildSelectedOptionAnswers(submission, target, answerDto.getSelectedOptionIds()));
            } else if (type == QuestionType.MULTI && answerDto.getTextAnswer() != null && !answerDto.getTextAnswer().isBlank()) {
                answers.addAll(buildCommaSeparatedMultiAnswers(submission, target, answerDto.getTextAnswer()));
            } else if (type == QuestionType.SINGLE || type == QuestionType.BOOLEAN) {
                answers.add(buildSingleChoiceAnswer(submission, target, answerDto));
            } else {
                answers.add(buildTextOrRatingAnswer(submission, target, answerDto));
            }
        }
        return answers;
    }

    /**
     * 以三段容錯找出作答對應之題目：題目 ID → 排序序號 (前端暫定序號) → 作答清單索引
     * @param surveyQuestions 問卷題目清單
     * @param answerDto 作答 DTO
     * @param answerIndex 作答於清單中之索引
     * @return 對應題目，找不到時為空
     */
    private Optional<SurveyQuestion> resolveQuestion(List<SurveyQuestion> surveyQuestions, SubmissionAnswerDto answerDto, int answerIndex) {
        Long questionId = answerDto.getQuestionId();
        Optional<SurveyQuestion> byId = surveyQuestions.stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst();
        if (byId.isPresent()) {
            return byId;
        }
        if (questionId != null) {
            int targetOrder = questionId.intValue();
            Optional<SurveyQuestion> bySortOrder = surveyQuestions.stream()
                    .filter(q -> q.getSortOrder() != null && q.getSortOrder().equals(targetOrder))
                    .findFirst();
            if (bySortOrder.isPresent()) {
                return bySortOrder;
            }
        }
        return answerIndex < surveyQuestions.size() ? Optional.of(surveyQuestions.get(answerIndex)) : Optional.empty();
    }

    /**
     * 依前端勾選之選項 ID 清單建立作答明細 (多選題主路徑)
     * @param submission 所屬填答
     * @param question 所屬題目
     * @param selectedOptionIds 勾選之選項 ID
     * @return 每個勾選選項一筆之作答明細
     */
    private List<SubmissionAnswer> buildSelectedOptionAnswers(Submission submission, SurveyQuestion question, List<Long> selectedOptionIds) {
        List<SubmissionAnswer> answers = new ArrayList<>();
        for (Long optionId : selectedOptionIds) {
            SurveyOption option = optionsOf(question).stream()
                    .filter(o -> o.getId().equals(optionId))
                    .findFirst()
                    .orElse(null);
            answers.add(SubmissionAnswer.builder()
                    .submission(submission)
                    .question(question)
                    .option(option)
                    .textAnswer(option != null ? option.getOptionText() : null)
                    .build());
        }
        return answers;
    }

    /**
     * 多選題以逗號分隔文字送出時，拆分並反向比對選項；完全比對不到時保留原始文字一筆
     * @param submission 所屬填答
     * @param question 所屬題目
     * @param rawText 逗號分隔之作答文字
     * @return 作答明細清單
     */
    private List<SubmissionAnswer> buildCommaSeparatedMultiAnswers(Submission submission, SurveyQuestion question, String rawText) {
        List<SubmissionAnswer> answers = new ArrayList<>();
        for (String token : rawText.split(",")) {
            String trimmed = token.trim();
            optionsOf(question).stream()
                    .filter(o -> o.getOptionText().equalsIgnoreCase(trimmed))
                    .findFirst()
                    .ifPresent(option -> answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .option(option)
                            .textAnswer(trimmed)
                            .build()));
        }
        if (answers.isEmpty()) {
            answers.add(SubmissionAnswer.builder()
                    .submission(submission)
                    .question(question)
                    .textAnswer(rawText.trim())
                    .build());
        }
        return answers;
    }

    /**
     * 單選題或是非題：優先以選項 ID 綁定，否則以正規化文字反向比對選項 (是非題支援 true/false/1/0/yes/no)
     * @param submission 所屬填答
     * @param question 所屬題目
     * @param answerDto 作答 DTO
     * @return 單筆作答明細
     */
    private SubmissionAnswer buildSingleChoiceAnswer(Submission submission, SurveyQuestion question, SubmissionAnswerDto answerDto) {
        List<SurveyOption> options = optionsOf(question);
        SurveyOption targetOption = null;
        if (answerDto.getSelectedOptionId() != null) {
            targetOption = options.stream()
                    .filter(o -> o.getId().equals(answerDto.getSelectedOptionId()))
                    .findFirst()
                    .orElse(null);
        }

        String rawText = answerDto.getTextAnswer() != null ? answerDto.getTextAnswer().trim() : null;
        String normalizedText = question.getType() == QuestionType.BOOLEAN ? normalizeBooleanText(rawText) : rawText;

        if (targetOption == null && normalizedText != null && !normalizedText.isBlank()) {
            targetOption = findOptionByText(options, normalizedText);
            if (targetOption == null && question.getType() == QuestionType.BOOLEAN) {
                targetOption = findBooleanOptionLoosely(options, normalizedText);
            }
        }

        return SubmissionAnswer.builder()
                .submission(submission)
                .question(question)
                .option(targetOption)
                .textAnswer(targetOption != null ? targetOption.getOptionText() : normalizedText)
                .build();
    }

    /**
     * 文字簡答、日期、時間或評分題；評分題未帶數值時嘗試從文字解析分數
     * @param submission 所屬填答
     * @param question 所屬題目
     * @param answerDto 作答 DTO
     * @return 單筆作答明細
     */
    private SubmissionAnswer buildTextOrRatingAnswer(Submission submission, SurveyQuestion question, SubmissionAnswerDto answerDto) {
        Integer rating = answerDto.getRatingValue();
        if (rating == null && question.getType() == QuestionType.RATING && answerDto.getTextAnswer() != null) {
            rating = RatingScoreParser.parseRating(answerDto.getTextAnswer());
        }
        return SubmissionAnswer.builder()
                .submission(submission)
                .question(question)
                .textAnswer(answerDto.getTextAnswer())
                .ratingValue(rating)
                .build();
    }

    /**
     * 將是非題常見布林文字正規化為「是 / 否」，無法辨識時原樣回傳
     * @param rawText 原始文字 (已 trim)，可為 null
     * @return 正規化文字
     */
    private String normalizeBooleanText(String rawText) {
        if (rawText == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(rawText) || "1".equals(rawText) || "yes".equalsIgnoreCase(rawText)) {
            return BOOLEAN_YES;
        }
        if ("false".equalsIgnoreCase(rawText) || "0".equals(rawText) || "no".equalsIgnoreCase(rawText)) {
            return BOOLEAN_NO;
        }
        return rawText;
    }

    /**
     * 以選項文字 (忽略大小寫與前後空白) 精確比對選項
     * @param options 題目選項
     * @param text 比對文字
     * @return 比對到之選項，否則 null
     */
    private SurveyOption findOptionByText(List<SurveyOption> options, String text) {
        return options.stream()
                .filter(o -> o.getOptionText() != null && o.getOptionText().trim().equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是非題寬鬆比對：選項文字包含「是 / 否」或為 true / false 即視為命中
     * @param options 題目選項
     * @param normalizedText 已正規化之「是」或「否」
     * @return 比對到之選項，否則 null
     */
    private SurveyOption findBooleanOptionLoosely(List<SurveyOption> options, String normalizedText) {
        String englishLiteral;
        if (BOOLEAN_YES.equals(normalizedText)) {
            englishLiteral = "true";
        } else if (BOOLEAN_NO.equals(normalizedText)) {
            englishLiteral = "false";
        } else {
            return null;
        }
        return options.stream()
                .filter(o -> o.getOptionText() != null
                        && (o.getOptionText().contains(normalizedText) || o.getOptionText().equalsIgnoreCase(englishLiteral)))
                .findFirst()
                .orElse(null);
    }

    /**
     * 取得題目選項清單 (JPA 集合不會為 null，無選項時為空清單)
     * @param question 題目
     * @return 選項清單
     */
    private List<SurveyOption> optionsOf(SurveyQuestion question) {
        return question.getOptions();
    }

    /**
     * 將受訪者基本資料 DTO 轉換為實體 (尚未綁定填答主記錄)
     * @param dto 受訪者基本資料 DTO
     * @return 受訪者基本資料實體
     */
    private SubmissionBasicInfo toBasicInfoEntity(SubmissionBasicInfoDto dto) {
        return SubmissionBasicInfo.builder()
                .name(dto.getName())
                .gender(dto.getGender())
                .ageGroup(dto.getAgeGroup())
                .birthday(dto.getBirthday())
                .nationalId(dto.getNationalId())
                .phone(dto.getPhone())
                .homePhone(dto.getHomePhone())
                .email(dto.getEmail())
                .industry(dto.getIndustry())
                .jobTitle(dto.getJobTitle())
                .department(dto.getDepartment())
                .employeeId(dto.getEmployeeId())
                .academicMajor(dto.getAcademicMajor())
                .className(dto.getClassName())
                .studentId(dto.getStudentId())
                .height(dto.getHeight())
                .weight(dto.getWeight())
                .residence(dto.getResidence())
                .registeredAddress(dto.getRegisteredAddress())
                .build();
    }
}
