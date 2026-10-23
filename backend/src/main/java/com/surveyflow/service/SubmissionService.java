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
            List<SurveyQuestion> surveyQuestions = survey.getQuestions() != null ? survey.getQuestions() : new ArrayList<>();

            for (int ansIdx = 0; ansIdx < request.getAnswers().size(); ansIdx++) {
                SubmissionAnswerDto aDto = request.getAnswers().get(ansIdx);

                // 容錯 1: 依 questionId 查找
                SurveyQuestion question = surveyQuestions.stream()
                        .filter(q -> q.getId().equals(aDto.getQuestionId()))
                        .findFirst()
                        .orElse(null);

                // 容錯 2: 依 sortOrder (或前端暫定序號) 查找
                if (question == null && aDto.getQuestionId() != null) {
                    final int targetOrder = aDto.getQuestionId().intValue();
                    question = surveyQuestions.stream()
                            .filter(q -> q.getSortOrder() != null && q.getSortOrder().equals(targetOrder))
                            .findFirst()
                            .orElse(null);
                }

                // 容錯 3: 依作答清單索引直接對應題目
                if (question == null && ansIdx < surveyQuestions.size()) {
                    question = surveyQuestions.get(ansIdx);
                }

                if (question == null) continue;

                List<SurveyOption> qOptions = question.getOptions() != null ? question.getOptions() : new ArrayList<>();

                // (A) 多選題處理：若有 selectedOptionIds 清單
                if (aDto.getSelectedOptionIds() != null && !aDto.getSelectedOptionIds().isEmpty()) {
                    for (Long optId : aDto.getSelectedOptionIds()) {
                        SurveyOption option = qOptions.stream()
                                .filter(o -> o.getId().equals(optId))
                                .findFirst()
                                .orElse(null);

                        answers.add(SubmissionAnswer.builder()
                                .submission(submission)
                                .question(question)
                                .option(option)
                                .textAnswer(option != null ? option.getOptionText() : null)
                                .build());
                    }
                } else if (question.getType() == QuestionType.MULTI && aDto.getTextAnswer() != null && !aDto.getTextAnswer().isBlank()) {
                    // [健壯防禦]: 多選題若傳入逗號分隔之文字，拆分並反向匹配各個選項實體
                    String[] tokens = aDto.getTextAnswer().split(",");
                    boolean matchedAny = false;
                    for (String token : tokens) {
                        String trimmed = token.trim();
                        SurveyOption option = qOptions.stream()
                                .filter(o -> o.getOptionText().equalsIgnoreCase(trimmed))
                                .findFirst()
                                .orElse(null);
                        if (option != null) {
                            matchedAny = true;
                            answers.add(SubmissionAnswer.builder()
                                    .submission(submission)
                                    .question(question)
                                    .option(option)
                                    .textAnswer(trimmed)
                                    .build());
                        }
                    }
                    if (!matchedAny) {
                        answers.add(SubmissionAnswer.builder()
                                .submission(submission)
                                .question(question)
                                .textAnswer(aDto.getTextAnswer().trim())
                                .build());
                    }
                } else if (question.getType() == QuestionType.SINGLE || question.getType() == QuestionType.BOOLEAN) {
                    // (B) 單選題或是非題：精確雙向綁定選項
                    SurveyOption targetOption = null;

                    // 優先依 selectedOptionId 查找
                    if (aDto.getSelectedOptionId() != null) {
                        targetOption = qOptions.stream()
                                .filter(o -> o.getId().equals(aDto.getSelectedOptionId()))
                                .findFirst()
                                .orElse(null);
                    }

                    // 文字正規化分析（支援布林值 true/false/1/0/yes/no 與文字選項文字）
                    String rawText = aDto.getTextAnswer() != null ? aDto.getTextAnswer().trim() : null;
                    String normText = rawText;
                    if (question.getType() == QuestionType.BOOLEAN && rawText != null) {
                        if ("true".equalsIgnoreCase(rawText) || "1".equals(rawText) || "yes".equalsIgnoreCase(rawText)) {
                            normText = "是";
                        } else if ("false".equalsIgnoreCase(rawText) || "0".equals(rawText) || "no".equalsIgnoreCase(rawText)) {
                            normText = "否";
                        }
                    }

                    // 若尚未匹配到選項且有文字答案，反向在題目的選項清單中搜尋
                    if (targetOption == null && normText != null && !normText.isBlank()) {
                        final String searchText = normText;
                        targetOption = qOptions.stream()
                                .filter(o -> o.getOptionText() != null && o.getOptionText().trim().equalsIgnoreCase(searchText))
                                .findFirst()
                                .orElse(null);

                        // 是非題寬鬆匹配
                        if (targetOption == null && question.getType() == QuestionType.BOOLEAN) {
                            if ("是".equals(searchText)) {
                                targetOption = qOptions.stream()
                                        .filter(o -> o.getOptionText() != null && (o.getOptionText().contains("是") || o.getOptionText().equalsIgnoreCase("true")))
                                        .findFirst().orElse(null);
                            } else if ("否".equals(searchText)) {
                                targetOption = qOptions.stream()
                                        .filter(o -> o.getOptionText() != null && (o.getOptionText().contains("否") || o.getOptionText().equalsIgnoreCase("false")))
                                        .findFirst().orElse(null);
                            }
                        }
                    }

                    String textToStore = targetOption != null ? targetOption.getOptionText() : (normText != null ? normText : rawText);

                    answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .option(targetOption)
                            .textAnswer(textToStore)
                            .build());
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
