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

        // 5. 計算資料庫真實最新回覆時間 (以臺灣標準時間格式化，若尚無作答回覆則為 null)
        String lastResponseAt = submissions.stream()
                .map(Submission::getSubmittedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(dt -> dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")))
                .orElse(null);

        return SurveyAnalyticsResponse.builder()
                .surveyId(survey.getId())
                .surveyTitle(survey.getTitle())
                .category(survey.getCategory())
                .authorUsername(survey.getCreator() != null ? survey.getCreator().getUsername() : "系統發起人")
                .isPublicToLobby(survey.getIsPublicToLobby())
                .isOpen(survey.getIsOpen())
                .startDate(survey.getStartDate() != null ? survey.getStartDate().toLocalDate().toString() : null)
                .endDate(survey.getEndDate() != null ? survey.getEndDate().toLocalDate().toString() : null)
                .kpi(kpi)
                .lastResponseAt(lastResponseAt)
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

            if (info.getGender() != null && !info.getGender().trim().isEmpty()) {
                genderMap.merge(info.getGender().trim(), 1L, Long::sum);
            }
            if (info.getAgeGroup() != null && !info.getAgeGroup().trim().isEmpty()) {
                ageMap.merge(info.getAgeGroup().trim(), 1L, Long::sum);
            }
            if (info.getIndustry() != null && !info.getIndustry().trim().isEmpty()) {
                industryMap.merge(info.getIndustry().trim(), 1L, Long::sum);
            }
            if (info.getResidence() != null && !info.getResidence().trim().isEmpty()) {
                residenceMap.merge(info.getResidence().trim(), 1L, Long::sum);
            }
        }

        // 若無任何有效的人口輪廓特徵（如完全匿名問卷或僅收集姓名/信箱），回傳 null 避免前端產生假圖表
        if (genderMap.isEmpty() && ageMap.isEmpty() && industryMap.isEmpty() && residenceMap.isEmpty()) {
            return null;
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

            if (q.getType() == QuestionType.SINGLE || q.getType() == QuestionType.MULTI || q.getType() == QuestionType.BOOLEAN) {
                Map<String, Long> counts = new HashMap<>();
                Map<String, Double> percentages = new HashMap<>();

                // 初始化選項
                for (SurveyOption o : q.getOptions()) {
                    counts.put(o.getOptionText(), 0L);
                }

                List<Map<String, Object>> rawCounts = answerRepository.countSelectionsByQuestionNative(q.getId());
                long sumSelections = 0;
                for (Map<String, Object> row : rawCounts) {
                    Long optId = getLong(getCaseInsensitive(row, "optionId"));
                    Long count = getLong(getCaseInsensitive(row, "selectedCount"));
                    sumSelections += count;

                    if (optId != null) {
                        q.getOptions().stream().filter(o -> o.getId().equals(optId)).findFirst().ifPresent(o -> {
                            counts.put(o.getOptionText(), count);
                        });
                    }
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
                Double avg = getDouble(getCaseInsensitive(ratingStats, "avgRating"));
                Long count = getLong(getCaseInsensitive(ratingStats, "totalCount"));
                long totalCount = count != null ? count : 0L;

                int minR = q.getMinRating() != null ? q.getMinRating() : 1;
                int maxR = q.getMaxRating() != null ? q.getMaxRating() : (avg != null && avg > 5.0 ? 10 : 5);

                List<Map<String, Object>> rawDist = answerRepository.countRatingDistributionByQuestionNative(q.getId());
                Map<Integer, Long> distMap = new TreeMap<>();
                
                // 動態判斷真實出現的分數範圍
                for (Map<String, Object> row : rawDist) {
                    Long rVal = getLong(getCaseInsensitive(row, "ratingValue"));
                    if (rVal != null) {
                        if (rVal < minR) minR = rVal.intValue();
                        if (rVal > maxR) maxR = rVal.intValue();
                    }
                }
                
                // 初始化全區間為 0
                for (int score = minR; score <= maxR; score++) {
                    distMap.put(score, 0L);
                }

                List<Integer> allScores = new ArrayList<>();
                long maxFrequency = 0L;
                int modeScore = minR;
                long detractorCount = 0L;
                long passiveCount = 0L;
                long promoterCount = 0L;

                for (Map<String, Object> row : rawDist) {
                    Long rVal = getLong(getCaseInsensitive(row, "ratingValue"));
                    Long rCnt = getLong(getCaseInsensitive(row, "ratingCount"));
                    if (rVal != null && rCnt != null) {
                        int score = rVal.intValue();
                        distMap.put(score, rCnt);
                        for (int k = 0; k < rCnt; k++) {
                            allScores.add(score);
                        }
                        if (rCnt > maxFrequency) {
                            maxFrequency = rCnt;
                            modeScore = score;
                        }

                        // NPS 分組計算
                        if (maxR >= 10) {
                            if (score <= 6) detractorCount += rCnt;
                            else if (score <= 8) passiveCount += rCnt;
                            else promoterCount += rCnt;
                        } else {
                            if (score <= 2) detractorCount += rCnt;
                            else if (score == 3) passiveCount += rCnt;
                            else promoterCount += rCnt;
                        }
                    }
                }

                // 計算中位數
                Collections.sort(allScores);
                double median = 0.0;
                if (!allScores.isEmpty()) {
                    int size = allScores.size();
                    if (size % 2 == 1) {
                        median = allScores.get(size / 2);
                    } else {
                        median = (allScores.get(size / 2 - 1) + allScores.get(size / 2)) / 2.0;
                    }
                }

                // 計算標準差
                double stdDev = 0.0;
                if (totalCount > 1 && avg != null) {
                    double sumSqDiff = 0.0;
                    for (int score : allScores) {
                        sumSqDiff += Math.pow(score - avg, 2);
                    }
                    stdDev = Math.round(Math.sqrt(sumSqDiff / totalCount) * 10.0) / 10.0;
                }

                // 計算該量表題之真實 NPS
                int itemNps = 0;
                if (totalCount > 0) {
                    itemNps = (int) Math.round(((double) (promoterCount - detractorCount) / totalCount) * 100.0);
                }

                Map<String, Long> npsBreakdown = new HashMap<>();
                npsBreakdown.put("detractor", detractorCount);
                npsBreakdown.put("passive", passiveCount);
                npsBreakdown.put("promoter", promoterCount);

                builder.totalAnswers(totalCount)
                        .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0)
                        .minRating(minR)
                        .maxRating(maxR)
                        .ratingDistribution(distMap)
                        .modeRating(modeScore)
                        .medianRating(median)
                        .standardDeviation(stdDev)
                        .npsScore(itemNps)
                        .npsBreakdown(npsBreakdown);

            } else if (q.getType() == QuestionType.TEXT || q.getType() == QuestionType.TIME || q.getType() == QuestionType.DATE) {
                List<Map<String, Object>> structuredFeedbacks = answerRepository.findStructuredTextAnswersByQuestionNative(q.getId());
                builder.totalAnswers((long) structuredFeedbacks.size())
                        .textFeedbacks(new ArrayList<>(structuredFeedbacks.stream().limit(100).collect(Collectors.toList())));
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

        Long qId = ratingQ.get().getId();
        List<Map<String, Object>> rawDist = answerRepository.countRatingDistributionByQuestionNative(qId);
        long detractorCount = 0L;
        long passiveCount = 0L;
        long promoterCount = 0L;
        long totalCount = 0L;

        int maxR = ratingQ.get().getMaxRating() != null ? ratingQ.get().getMaxRating() : 5;

        for (Map<String, Object> row : rawDist) {
            Long rVal = getLong(getCaseInsensitive(row, "ratingValue"));
            Long rCnt = getLong(getCaseInsensitive(row, "ratingCount"));
            if (rVal != null && rCnt != null) {
                int score = rVal.intValue();
                totalCount += rCnt;
                if (score > maxR) maxR = score;

                if (maxR >= 10) {
                    if (score <= 6) detractorCount += rCnt;
                    else if (score <= 8) passiveCount += rCnt;
                    else promoterCount += rCnt;
                } else {
                    if (score <= 2) detractorCount += rCnt;
                    else if (score == 3) passiveCount += rCnt;
                    else promoterCount += rCnt;
                }
            }
        }

        if (totalCount == 0) return 50;
        return (int) Math.round(((double) (promoterCount - detractorCount) / totalCount) * 100.0);
    }

    /**
     * 不區分大小寫自 Map 讀取屬性值，相容不同資料庫驅動欄位別名慣例
     */
    private Object getCaseInsensitive(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 安全轉型為 Double，防止 BigDecimal / Double / Long 之 ClassCastException
     */
    private Double getDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.valueOf(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全轉型為 Long，防止 BigInteger / Integer / Long 之 ClassCastException
     */
    private Long getLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.valueOf(val.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 全站維運與營運概覽統計 KPI (Admin 專屬業務邏輯)
     * @return 全站統計數據 (總會員數、總問卷數、總填答數、伺服器時間)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformOverview() {
        long totalUsers = userRepository.count();
        long totalSurveys = surveyRepository.count();
        long totalSubmissions = submissionRepository.countTotalSubmissionsNative();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalSurveys", totalSurveys);
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("serverTime", java.time.LocalDateTime.now());

        return stats;
    }
}
