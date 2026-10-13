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

import com.surveyflow.common.enums.SurveyStatus;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SurveyRepository surveyRepository;

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

        if (survey.getStatus() != SurveyStatus.PUBLISHED) {
            throw new IllegalArgumentException("此問卷目前非公開徵集狀態，無法送出回答");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(survey.getStartDate()) || now.isAfter(survey.getEndDate())) {
            throw new IllegalArgumentException("目前非此問卷開放填答之有效時間範圍");
        }

        // 1. 建立填答主記錄
        Submission submission = Submission.builder()
                .survey(survey)
                .ipAddress(clientIp)
                .durationSeconds(request.getDurationSeconds())
                .submittedAt(now)
                .build();

        // 2. 建立受訪者基本輪廓
        SubmissionBasicInfoDto basicDto = request.getBasicInfo();
        if (basicDto != null) {
            SubmissionBasicInfo basicInfo = SubmissionBasicInfo.builder()
                    .submission(submission)
                    .name(basicDto.getName())
                    .gender(basicDto.getGender())
                    .ageGroup(basicDto.getAgeGroup())
                    .phone(basicDto.getPhone())
                    .email(basicDto.getEmail())
                    .industry(basicDto.getIndustry())
                    .residence(basicDto.getResidence())
                    .build();
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
                    // 單選題
                    SurveyOption option = question.getOptions().stream()
                            .filter(o -> o.getId().equals(aDto.getSelectedOptionId()))
                            .findFirst()
                            .orElse(null);

                    answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .option(option)
                            .build());
                } else {
                    // 文字簡答題或評分量表題
                    answers.add(SubmissionAnswer.builder()
                            .submission(submission)
                            .question(question)
                            .textAnswer(aDto.getTextAnswer())
                            .ratingValue(aDto.getRatingValue())
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
