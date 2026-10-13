/**
 * ============================================================================
 * 檔案名稱: AnalyticsService.java
 * 所屬模組: Service 業務邏輯層 - 數據分析模組
 * 責任描述: 負責問卷統計數據即時聚合運算，包含 KPI、每日走勢、受訪者畫像與各題型深度統計
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRepository, SubmissionRepository, SubmissionAnswerRepository, UserRepository
 *   - 下游消費: AnalyticsController, 前端 SurveyManagementComponent 數據儀表板
 * 邊界處理: 嚴格核對發布者或管理員權限，無權限者拋出 AccessDeniedException
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.QuestionType;
import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.analytics.*;
import com.surveyflow.entity.*;
import com.surveyflow.repository.SubmissionAnswerRepository;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import com.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SurveyRepository surveyRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final UserRepository userRepository;

    /**
     * 產出問卷全維度數據分析報表
     * @param surveyId 問卷 ID
     * @param currentUsername 當前操作者帳號
     * @return 完整聚合分析結果
     */
    @Transactional(readOnly = true)
    public SurveyAnalyticsResponse getSurveyAnalytics(Long surveyId, String currentUsername) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("查無此問卷專案"));

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無當前使用者"));

        boolean isOwner = survey.getCreator().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == RoleType.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("您無權限查閱此問卷之統計數據報表");
        }

        List<Submission> submissions = submissionRepository.findBySurveyIdNative(survey.getId());
        long totalResponses = submissions.size();

        // 1. KPI 計算
        KpiDto kpi = calculateKpi(survey, submissions, totalResponses);

        // 2. 近 7 日填答時間趨勢
        List<TrendDto> trends = calculateTrends(submissions);

        // 3. 受訪者人口特徵畫像
        DemographicsDto demographics = calculateDemographics(submissions);

        // 4. 各題型詳細數據分析
        List<QuestionStatDto> questionStats = calculateQuestionStats(survey, totalResponses);

        return SurveyAnalyticsResponse.builder()
                .surveyId(survey.getId())
                .surveyTitle(survey.getTitle())
                .kpi(kpi)
                .trends(trends)
                .demographics(demographics)
                .questionStats(questionStats)
                .build();
    }

    /**
     * 計算 5 大核心 KPI 指標
     */
    private KpiDto calculateKpi(Survey survey, List<Submission> submissions, long totalResponses) {
        Double avgDuration = submissionRepository.getAverageDurationSecondsNative(survey.getId());
        int averageSeconds = avgDuration != null ? avgDuration.intValue() : 0;

        LocalDate today = LocalDate.now();
        long todayCount = submissions.stream()
                .filter(s -> s.getSubmittedAt().toLocalDate().isEqual(today))
                .count();

        // 完成率計算 (以瀏覽數為基底)
        int views = survey.getViewCount() != null ? survey.getViewCount() : 0;
        double completionRate = views > 0 ? Math.min(100.0, (double) totalResponses / views * 100.0) : (totalResponses > 0 ? 100.0 : 0.0);
        completionRate = Math.round(completionRate * 10.0) / 10.0;

        // 計算 NPS (若問卷有包含 RATING 題型則計算平均 NPS)
        int nps = calculateSurveyNps(survey);

        return KpiDto.builder()
                .totalResponses(totalResponses)
                .averageDurationSeconds(averageSeconds)
                .completionRate(completionRate)
                .netPromoterScore(nps)
                .todayResponses(todayCount)
                .build();
    }

    /**
     * 計算近 7 日每日填答走勢
     */
    private List<TrendDto> calculateTrends(List<Submission> submissions) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
        Map<String, Long> dateMap = new LinkedHashMap<>();

        // 初始化近 7 天
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            dateMap.put(d.format(formatter), 0L);
        }

        // 統計計數
        for (Submission s : submissions) {
            String dateKey = s.getSubmittedAt().toLocalDate().format(formatter);
            if (dateMap.containsKey(dateKey)) {
                dateMap.put(dateKey, dateMap.get(dateKey) + 1);
            }
        }

        return dateMap.entrySet().stream()
                .map(e -> TrendDto.builder().date(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());
    }

    /**
     * 統計受訪者人口輪廓
     */
    private DemographicsDto calculateDemographics(List<Submission> submissions) {
        Map<String, Long> genderMap = new HashMap<>();
        Map<String, Long> ageMap = new HashMap<>();
        Map<String, Long> industryMap = new HashMap<>();
        Map<String, Long> residenceMap = new HashMap<>();

        for (Submission s : submissions) {
            SubmissionBasicInfo info = s.getBasicInfo();
            if (info == null) continue;

            if (info.getGender() != null) {
                genderMap.merge(info.getGender(), 1L, Long::sum);
            }
            if (info.getAgeGroup() != null) {
                ageMap.merge(info.getAgeGroup(), 1L, Long::sum);
            }
            if (info.getIndustry() != null) {
                industryMap.merge(info.getIndustry(), 1L, Long::sum);
            }
            if (info.getResidence() != null) {
                residenceMap.merge(info.getResidence(), 1L, Long::sum);
            }
        }

        return DemographicsDto.builder()
                .genderDistribution(genderMap)
                .ageGroupDistribution(ageMap)
                .industryDistribution(industryMap)
                .residenceDistribution(residenceMap)
                .build();
    }

    /**
     * 聚合各題型作答明細
     */
    private List<QuestionStatDto> calculateQuestionStats(Survey survey, long totalResponses) {
        List<QuestionStatDto> stats = new ArrayList<>();

        for (SurveyQuestion q : survey.getQuestions()) {
            QuestionStatDto.QuestionStatDtoBuilder builder = QuestionStatDto.builder()
                    .questionId(q.getId())
                    .questionCode(q.getQuestionCode())
                    .questionText(q.getQuestionText())
                    .type(q.getType());

            if (q.getType() == QuestionType.SINGLE || q.getType() == QuestionType.MULTI) {
                Map<String, Long> counts = new HashMap<>();
                Map<String, Double> percentages = new HashMap<>();

                // 初始化選項
                for (SurveyOption o : q.getOptions()) {
                    counts.put(o.getOptionText(), 0L);
                }

                List<Map<String, Object>> rawCounts = answerRepository.countSelectionsByQuestionNative(q.getId());
                long sumSelections = 0;
                for (Map<String, Object> row : rawCounts) {
                    Long optId = (Long) row.get("optionId");
                    Long count = (Long) row.get("selectedCount");
                    sumSelections += count;

                    q.getOptions().stream().filter(o -> o.getId().equals(optId)).findFirst().ifPresent(o -> {
                        counts.put(o.getOptionText(), count);
                    });
                }

                // 計算百分比
                final long denom = totalResponses > 0 ? totalResponses : 1;
                for (Map.Entry<String, Long> entry : counts.entrySet()) {
                    double pct = Math.round(((double) entry.getValue() / denom * 100.0) * 10.0) / 10.0;
                    percentages.put(entry.getKey(), pct);
                }

                builder.totalAnswers(sumSelections)
                        .optionCounts(counts)
                        .optionPercentages(percentages);

            } else if (q.getType() == QuestionType.RATING) {
                Map<String, Object> ratingStats = answerRepository.calculateRatingStatsNative(q.getId());
                Double avg = (Double) ratingStats.get("avgRating");
                Long count = (Long) ratingStats.get("totalCount");

                builder.totalAnswers(count != null ? count : 0L)
                        .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);

            } else if (q.getType() == QuestionType.TEXT) {
                List<String> feedbacks = answerRepository.findTextAnswersByQuestionNative(q.getId());
                builder.totalAnswers((long) feedbacks.size())
                        .textFeedbacks(feedbacks.stream().limit(20).collect(Collectors.toList()));
            }

            stats.add(builder.build());
        }

        return stats;
    }

    /**
     * 計算問卷整體 NPS 淨推薦值
     */
    private int calculateSurveyNps(Survey survey) {
        Optional<SurveyQuestion> ratingQ = survey.getQuestions().stream()
                .filter(q -> q.getType() == QuestionType.RATING)
                .findFirst();

        if (ratingQ.isEmpty()) return 50; // 預設滿意基準值

        Map<String, Object> stats = answerRepository.calculateRatingStatsNative(ratingQ.get().getId());
        Double avg = (Double) stats.get("avgRating");
        if (avg == null) return 50;

        // 若 5 分制換算 NPS (4-5 分推薦, 3 分被動, 1-2 分貶損)
        return (int) Math.round((avg / 5.0) * 100 - 30);
    }
}
