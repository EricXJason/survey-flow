/**
 * ============================================================================
 * 檔案名稱: SubmissionService.java
 * 所屬模組: Service 業務邏輯層 - 填答模組
 * 責任描述: 處理受訪者填答提交、作答時間紀錄、基本輪廓存儲與多題型作答明細持久化
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: SubmissionRepository, SurveyRepository
 *   - 下游消費: SubmissionController
 * 邊界處理: 驗證問卷狀態為 PUBLISHED 且當前時間介於有效區間，否則阻斷提交
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.QuestionType;
import com.surveyflow.dto.submission.*;
import com.surveyflow.entity.*;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.modelmapper.ModelMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SurveyRepository surveyRepository;
    private final ModelMapper modelMapper;

    /**
     * 處理受訪者送出問卷填答
     * @param request 填答請求資料
     * @param clientIp 客戶端 IP 位址
     * @return 提交結果回應
     */
    @Transactional
    public SubmissionResponse submitSurvey(SubmissionRequest request, String clientIp) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(request.getSurveyId())
                .orElseThrow(() -> new IllegalArgumentException("填答之問卷不存在或已刪除"));

        if (Boolean.FALSE.equals(survey.getIsOpen())) {
            throw new IllegalArgumentException("此問卷目前尚未開放填答，或已由發起人手動關閉");
        }

        LocalDateTime now = LocalDateTime.now();
        if (survey.getStartDate() != null && now.isBefore(survey.getStartDate())) {
            throw new IllegalArgumentException("問卷尚未到達開放填答時間");
        }
        if (survey.getEndDate() != null && now.isAfter(survey.getEndDate())) {
            throw new IllegalArgumentException("目前非此問卷開放填答之有效時間範圍，填答已截止");
        }

        // 1. 建立填答主記錄
        Submission submission = Submission.builder()
                .survey(survey)
                .ipAddress(clientIp)
                .durationSeconds(request.getDurationSeconds())
                .submittedAt(now)
                .build();

        // 2. 建立受訪者基本輪廓 (統一透過 ModelMapper 自動映射)
        SubmissionBasicInfoDto basicDto = request.getBasicInfo();
        if (basicDto != null) {
            SubmissionBasicInfo basicInfo = modelMapper.map(basicDto, SubmissionBasicInfo.class);
            basicInfo.setId(null);
            basicInfo.setSubmission(submission);
            submission.setBasicInfo(basicInfo);
        }

        // 3. 建立各題作答明細
        if (request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            List<SubmissionAnswer> answers = new ArrayList<>();
            for (SubmissionAnswerDto aDto : request.getAnswers()) {
                SurveyQuestion question = survey.getQuestions().stream()
                        .filter(q -> q.getId().equals(aDto.getQuestionId()))
                        .findFirst()
                        .orElse(null);

                if (question == null) continue;

                // 若為多選題且傳入 selectedOptionIds
                if (aDto.getSelectedOptionIds() != null && !aDto.getSelectedOptionIds().isEmpty()) {
                    for (Long optId : aDto.getSelectedOptionIds()) {
                        SurveyOption option = question.getOptions().stream()
                                .filter(o -> o.getId().equals(optId))
                                .findFirst()
                                .orElse(null);

                        answers.add(SubmissionAnswer.builder()
                                .submission(submission)
                                .question(question)
                                .option(option)
                                .build());
                    }
                } else if (aDto.getSelectedOptionId() != null) {
                    // 單選題或是非題 (精確透過選項 ID 綁定)
                    SurveyOption option = question.getOptions().stream()
                            .filter(o -> o.getId().equals(aDto.getSelectedOptionId()))
                            .findFirst()
                            .orElse(null);

                    answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .option(option)
                            .build());
                } else if ((question.getType() == QuestionType.SINGLE || question.getType() == QuestionType.BOOLEAN) 
                        && aDto.getTextAnswer() != null && !aDto.getTextAnswer().isBlank()) {
                    // [健壯防禦]: 單選或是非題若僅帶文字答案，依文字內容反向匹配對應之選項實體
                    String trimmedText = aDto.getTextAnswer().trim();
                    if (question.getType() == QuestionType.BOOLEAN) {
                        if ("true".equalsIgnoreCase(trimmedText) || "1".equals(trimmedText)) {
                            trimmedText = "是";
                        } else if ("false".equalsIgnoreCase(trimmedText) || "0".equals(trimmedText)) {
                            trimmedText = "否";
                        }
                    }

                    final String finalText = trimmedText;
                    SurveyOption option = question.getOptions().stream()
                            .filter(o -> o.getOptionText().equalsIgnoreCase(finalText))
                            .findFirst()
                            .orElse(null);

                    answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .option(option)
                            .textAnswer(finalText)
                            .build());
                } else if (question.getType() == QuestionType.MULTI && aDto.getTextAnswer() != null && !aDto.getTextAnswer().isBlank()) {
                    // [健壯防禦]: 多選題若傳入逗號分隔之文字，拆分並反向匹配各個選項實體
                    String[] tokens = aDto.getTextAnswer().split(",");
                    boolean matchedAny = false;
                    for (String token : tokens) {
                        String trimmed = token.trim();
                        SurveyOption option = question.getOptions().stream()
                                .filter(o -> o.getOptionText().equalsIgnoreCase(trimmed))
                                .findFirst()
                                .orElse(null);
                        if (option != null) {
                            matchedAny = true;
                            answers.add(SubmissionAnswer.builder()
                                    .submission(submission)
                                    .question(question)
                                    .option(option)
                                    .build());
                        }
                    }
                    if (!matchedAny) {
                        answers.add(SubmissionAnswer.builder()
                                .submission(submission)
                                .question(question)
                                .textAnswer(aDto.getTextAnswer())
                                .build());
                    }
                } else {
                    // 文字簡答題或評分量表題
                    Integer rating = aDto.getRatingValue();
                    if (rating == null && question.getType() == QuestionType.RATING && aDto.getTextAnswer() != null) {
                        String rawTxt = aDto.getTextAnswer().trim();
                        try {
                            rating = Integer.parseInt(rawTxt);
                        } catch (NumberFormatException ignored) {
                            // [Why]: 支援文字量表（滿意度、認同度、重要度、頻率）智能反向轉譯整數分值
                            if (rawTxt.contains("非常滿意") || rawTxt.contains("非常同意") || rawTxt.contains("非常重要") || rawTxt.contains("非常喜歡") || rawTxt.contains("總是")) {
                                rating = 5;
                            } else if (rawTxt.contains("滿意") || rawTxt.contains("同意") || rawTxt.contains("重要") || rawTxt.contains("喜歡") || rawTxt.contains("經常")) {
                                rating = 4;
                            } else if (rawTxt.contains("普通") || rawTxt.contains("有時")) {
                                rating = 3;
                            } else if (rawTxt.contains("非常不") || rawTxt.contains("從不")) {
                                rating = 1;
                            } else if (rawTxt.contains("不滿意") || rawTxt.contains("不同意") || rawTxt.contains("不重要") || rawTxt.contains("不喜歡") || rawTxt.contains("很少")) {
                                rating = 2;
                            }
                        }
                    }
                    answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .textAnswer(aDto.getTextAnswer())
                            .ratingValue(rating)
                            .build());
                }
            }
            submission.setAnswers(answers);
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
}
