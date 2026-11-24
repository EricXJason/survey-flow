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

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.dto.analytics.*;
import org.jasonproduction.surveyflow.entity.*;
import org.jasonproduction.surveyflow.repository.SubmissionAnswerRepository;
import org.jasonproduction.surveyflow.repository.SubmissionRepository;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jasonproduction.surveyflow.common.converter.RatingScoreParser;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SurveyRepository surveyRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository answerRepository;
    private final UserRepository userRepository;

    /** 年齡標準分組名稱 */
    private static final String AGE_UNDER_18 = "18歲以下";
    private static final String AGE_18_24 = "18-24歲";
    private static final String AGE_25_34 = "25-34歲";
    private static final String AGE_35_44 = "35-44歲";
    private static final String AGE_45_PLUS = "45歲以上";

    /** 年齡區間文字對照表 (依插入順序比對) */
    private static final Map<String, List<String>> AGE_GROUP_KEYWORDS = new LinkedHashMap<>();

    static {
        AGE_GROUP_KEYWORDS.put(AGE_UNDER_18, List.of("18歲以下", "未滿18", "<18"));
        AGE_GROUP_KEYWORDS.put(AGE_18_24, List.of("18-24", "18~24", "18至24"));
        AGE_GROUP_KEYWORDS.put(AGE_25_34, List.of("25-34", "25~34", "25至34"));
        AGE_GROUP_KEYWORDS.put(AGE_35_44, List.of("35-44", "35~44", "35至44"));
        AGE_GROUP_KEYWORDS.put(AGE_45_PLUS, List.of("45歲以上", "45以上", ">=45"));
    }

    /** 擷取字串中第一個整數 (預先編譯，避免每次呼叫重建) */
    private static final Pattern FIRST_INTEGER = Pattern.compile("\\d+");

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

        User user = userRepository.findByUsernameNative(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無當前使用者"));

        boolean isOwner = survey.isOwnedBy(user.getId());
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
                .authorUsername(survey.getCreator().getUsername())
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
     * 計算 5 大核心 KPI 指標 (總填答數、平均作答秒數、完成率、淨推薦值 NPS、今日新增填答數)
     * @param survey 問卷實體物件
     * @param submissions 填答實體清單
     * @param totalResponses 總填答筆數
     * @return 聚合後之 KpiDto 物件
     */
    private KpiDto calculateKpi(Survey survey, List<Submission> submissions, long totalResponses) {
        Double avgDuration = submissionRepository.getAverageDurationSecondsNative(survey.getId());
        int averageSeconds = avgDuration != null ? avgDuration.intValue() : 0;

        LocalDate today = LocalDate.now();
        long todayCount = submissions.stream()
                .filter(s -> s.getSubmittedAt() != null && s.getSubmittedAt().toLocalDate().isEqual(today))
                .count();

        // 完成率計算 (以瀏覽數為基底)
        int views = survey.getViewCount();
        double completionRate = views > 0 ? Math.min(100.0, (double) totalResponses / views * 100.0) : (totalResponses > 0 ? 100.0 : 0.0);
        completionRate = Math.round(completionRate * 10.0) / 10.0;

        // 計算 NPS (若問卷有包含 RATING 題型則計算平均 NPS)
        Integer nps = calculateSurveyNps(survey);

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
     * @param submissions 填答實體清單
     * @return 依日期排序之近 7 日 TrendDto 清單
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
            if (s.getSubmittedAt() == null) {
                continue;
            }
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
     * 統計受訪者人口輪廓 (性別、年齡層、職業產業、居住地)
     * @param submissions 填答實體清單
     * @return 人口特徵 DemographicsDto 物件，若無有效人口輪廓則回傳 null
     */
    private DemographicsDto calculateDemographics(List<Submission> submissions) {
        Map<String, Long> genderMap = new HashMap<>();
        Map<String, Long> ageMap = new HashMap<>();
        Map<String, Long> industryMap = new HashMap<>();

        for (Submission s : submissions) {
            SubmissionBasicInfo info = s.getBasicInfo();
            if (info == null) continue;

            if (info.getGender() != null && !info.getGender().trim().isEmpty()) {
                genderMap.merge(info.getGender().trim(), 1L, Long::sum);
            }
            if (info.getAgeGroup() != null && !info.getAgeGroup().trim().isEmpty()) {
                String standardAge = normalizeAgeGroup(info.getAgeGroup());
                ageMap.merge(standardAge, 1L, Long::sum);
            }
            if (info.getIndustry() != null && !info.getIndustry().trim().isEmpty()) {
                industryMap.merge(info.getIndustry().trim(), 1L, Long::sum);
            }
        }

        // 若無任何有效的人口輪廓特徵（如完全匿名問卷或僅收集姓名/信箱/自由文字地址），回傳 null 避免前端產生假圖表
        if (genderMap.isEmpty() && ageMap.isEmpty() && industryMap.isEmpty()) {
            return null;
        }

        return DemographicsDto.builder()
                .genderDistribution(genderMap)
                .ageGroupDistribution(ageMap)
                .industryDistribution(industryMap)
                .residenceDistribution(Collections.emptyMap())
                .build();
    }

    /**
     * 將受訪者年齡資料標準化歸類至五大標準分組
     * 第一步：比對 AGE_GROUP_KEYWORDS 中之區間文字 (如「25-34」「未滿18」)。
     * 第二步：取出字串中第一個整數 (如 "22歲" → 22) 依年齡區間歸類。
     * 第三步：皆無法辨識時原樣回傳，避免資訊遺失。
     * @param raw 原始年齡字串 (如 "22", "22歲", "25-34", "未滿18")
     * @return 標準分組字串 (18歲以下, 18-24歲, 25-34歲, 35-44歲, 45歲以上)，或無法辨識之原字串
     */
    private String normalizeAgeGroup(String raw) {
        String text = raw.trim();
        for (Map.Entry<String, List<String>> group : AGE_GROUP_KEYWORDS.entrySet()) {
            if (group.getValue().stream().anyMatch(text::contains)) {
                return group.getKey();
            }
        }
        Matcher matcher = FIRST_INTEGER.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        int age = Integer.parseInt(matcher.group());
        if (age < 18) return AGE_UNDER_18;
        if (age <= 24) return AGE_18_24;
        if (age <= 34) return AGE_25_34;
        if (age <= 44) return AGE_35_44;
        return AGE_45_PLUS;
    }

    /**
     * 聚合各題型作答明細 (單選、多選、是非、量表、文字簡答)
     * @param survey 問卷實體物件
     * @param totalResponses 總填答筆數
     * @return 各題統計分析 QuestionStatDto 清單
     */
    private List<QuestionStatDto> calculateQuestionStats(Survey survey, long totalResponses) {
        List<QuestionStatDto> stats = new ArrayList<>();
        Map<String, String[]> triggerInfoMap = buildTriggerInfoMap(survey);

        for (SurveyQuestion q : survey.getQuestions()) {
            QuestionStatDto.QuestionStatDtoBuilder builder = QuestionStatDto.builder()
                    .questionId(q.getId())
                    .questionCode(q.getQuestionCode())
                    .questionText(q.getQuestionText())
                    .type(q.getType());

            populateTriggerCondition(q, triggerInfoMap, builder);

            switch (q.getType()) {
                case SINGLE, MULTI, BOOLEAN -> populateChoiceQuestionStats(q, builder, totalResponses);
                case RATING -> populateRatingQuestionStats(q, builder);
                case TEXT, TIME, DATE -> populateTextQuestionStats(q, builder);
            }

            QuestionStatDto itemDto = builder.build();
            if (Boolean.TRUE.equals(itemDto.getIsConditionTriggered()) && itemDto.getTriggeredEligibleCount() != null) {
                long eligible = itemDto.getTriggeredEligibleCount();
                long ans = itemDto.getTotalAnswers() != null ? itemDto.getTotalAnswers() : 0L;
                double rate = eligible > 0 ? Math.min(100.0, Math.round(((double) ans / eligible) * 1000.0) / 10.0) : 100.0;
                itemDto = itemDto.toBuilder().triggeredResponseRate(rate).build();
            }
            stats.add(itemDto);
        }

        return stats;
    }

    /**
     * 建立前置解鎖條件快查表 (目標題 questionCode -> 觸發源題目名稱、選項文字與已選次數)
     */
    private Map<String, String[]> buildTriggerInfoMap(Survey survey) {
        Map<String, String[]> map = new HashMap<>();
        for (SurveyQuestion parentQ : survey.getQuestions()) {
            for (SurveyOption opt : parentQ.getOptions()) {
                if (opt.getTriggerQuestionUid() != null && !opt.getTriggerQuestionUid().isBlank()) {
                    Long count = answerRepository.countSelectionsByOptionIdNative(opt.getId());
                    map.put(opt.getTriggerQuestionUid().trim(), new String[]{
                            parentQ.getQuestionText(),
                            opt.getOptionText(),
                            String.valueOf(count != null ? count : 0L)
                    });
                }
            }
        }
        return map;
    }

    /**
     * 填充題目條件解鎖狀態
     */
    private void populateTriggerCondition(SurveyQuestion q, Map<String, String[]> triggerInfoMap, QuestionStatDto.QuestionStatDtoBuilder builder) {
        String qCode = q.getQuestionCode();
        if (qCode != null && triggerInfoMap.containsKey(qCode.trim())) {
            String[] info = triggerInfoMap.get(qCode.trim());
            builder.isConditionTriggered(true)
                    .triggerConditionDescription("依據前置題「" + info[0] + "」選取「" + info[1] + "」時解鎖開啟")
                    .triggeredEligibleCount(Long.parseLong(info[2]));
        }
    }

    /**
     * 統計單選/多選/是非題型作答數據
     */
    private void populateChoiceQuestionStats(SurveyQuestion q, QuestionStatDto.QuestionStatDtoBuilder builder, long totalResponses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Double> percentages = new LinkedHashMap<>();

        List<SurveyOption> sortedOptions = q.getOptions().stream()
                .sorted(Comparator.comparingInt(SurveyOption::getSortOrder))
                .collect(Collectors.toList());

        if (q.getType() == QuestionType.BOOLEAN) {
            counts.put("是", 0L);
            counts.put("否", 0L);
            for (SurveyOption o : sortedOptions) {
                counts.putIfAbsent(o.getOptionText(), 0L);
            }
        } else {
            for (SurveyOption o : sortedOptions) {
                counts.put(o.getOptionText(), 0L);
            }
        }

        // 1. 以 Option ID 為絕對主鍵統計得票 (支援同名選項完全獨立)
        Map<Long, Long> optIdCounts = new HashMap<>();
        for (SurveyOption o : sortedOptions) {
            optIdCounts.put(o.getId(), 0L);
        }

        List<Map<String, Object>> rawCounts = answerRepository.countSelectionsByQuestionNative(q.getId());
        long sumSelections = 0;

        for (Map<String, Object> row : rawCounts) {
            Long optId = getLong(getCaseInsensitive(row, "optionId"));
            Long count = getLong(getCaseInsensitive(row, "selectedCount"));
            final long finalCount = count != null ? count : 0L;

            if (optId != null && optIdCounts.containsKey(optId)) {
                optIdCounts.put(optId, optIdCounts.get(optId) + finalCount);
            }
            sumSelections += finalCount;
        }

        // 2. 文字作答降級補全（僅當 option_id 統計為 0 且存在純文字作答時才補全，嚴防與 option_id 重複累加）
        if (sumSelections == 0) {
            List<String> rawTexts = answerRepository.findTextAnswersByQuestionNative(q.getId());
            if (rawTexts != null && !rawTexts.isEmpty()) {
                long textBackfillCount = backfillChoiceFromTextAnswers(q.getType(), rawTexts, counts);
                if (textBackfillCount > 0) {
                    sumSelections = textBackfillCount;
                }
            }
        }

        final long denom = totalResponses > 0 ? totalResponses : 1;

        // 3. 建構獨立選項清單 (以 optionId 驅動)
        List<OptionStatItemDto> optionsList = new ArrayList<>();
        if (!sortedOptions.isEmpty()) {
            for (SurveyOption o : sortedOptions) {
                long c = optIdCounts.getOrDefault(o.getId(), 0L);
                // 若為純文字補全情境且 optIdCounts 為 0，自 counts 取文字得票
                if (sumSelections > 0 && optIdCounts.values().stream().allMatch(v -> v == 0L)) {
                    c = counts.getOrDefault(o.getOptionText(), 0L);
                }
                double pct = Math.round(((double) c / denom * 100.0) * 10.0) / 10.0;
                optionsList.add(OptionStatItemDto.builder()
                        .optionId(o.getId())
                        .text(o.getOptionText())
                        .sortOrder(o.getSortOrder())
                        .count(c)
                        .percentage(pct)
                        .build());

                // 向下相容 counts 與 percentages（若選項文字不同）
                counts.put(o.getOptionText(), c);
                percentages.put(o.getOptionText(), pct);
            }
        } else {
            // 無實體 options 之虛擬題目 (例如 是非題預設 是/否)
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                double pct = Math.round(((double) entry.getValue() / denom * 100.0) * 10.0) / 10.0;
                percentages.put(entry.getKey(), pct);
                optionsList.add(OptionStatItemDto.builder()
                        .text(entry.getKey())
                        .count(entry.getValue())
                        .percentage(pct)
                        .build());
            }
        }

        long effectiveAnswers = sumSelections;
        if (q.getType() != QuestionType.MULTI && effectiveAnswers > totalResponses && totalResponses > 0) {
            effectiveAnswers = totalResponses;
        }

        builder.totalAnswers(effectiveAnswers)
                .optionCounts(counts)
                .optionPercentages(percentages)
                .optionsList(optionsList);
    }

    /**
     * 自文字作答反向補全選項得票數
     */
    private long backfillChoiceFromTextAnswers(QuestionType type, List<String> rawTexts, Map<String, Long> counts) {
        long count = 0;
        for (String t : rawTexts) {
            if (t == null || t.isBlank()) continue;
            String trimmed = t.trim();

            if (type == QuestionType.BOOLEAN) {
                if ("是".equals(trimmed) || "true".equalsIgnoreCase(trimmed) || "1".equals(trimmed) || "yes".equalsIgnoreCase(trimmed)) {
                    counts.put("是", counts.getOrDefault("是", 0L) + 1);
                    count++;
                } else if ("否".equals(trimmed) || "false".equalsIgnoreCase(trimmed) || "0".equals(trimmed) || "no".equalsIgnoreCase(trimmed)) {
                    counts.put("否", counts.getOrDefault("否", 0L) + 1);
                    count++;
                }
            } else if (type == QuestionType.SINGLE) {
                boolean matched = false;
                for (String optKey : counts.keySet()) {
                    if (optKey.equalsIgnoreCase(trimmed)) {
                        counts.put(optKey, counts.getOrDefault(optKey, 0L) + 1);
                        count++;
                        matched = true;
                        break;
                    }
                }
                if (!matched && !counts.isEmpty()) {
                    counts.put(trimmed, counts.getOrDefault(trimmed, 0L) + 1);
                    count++;
                }
            } else if (type == QuestionType.MULTI) {
                for (String part : trimmed.split(",")) {
                    String clean = part.trim();
                    if (clean.isEmpty()) continue;
                    for (String optKey : counts.keySet()) {
                        if (optKey.equalsIgnoreCase(clean)) {
                            counts.put(optKey, counts.getOrDefault(optKey, 0L) + 1);
                            count++;
                            break;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * 統計評分量表題型作答數據 (平均分、NPS、標準差、中位數、眾數)
     */
    private void populateRatingQuestionStats(SurveyQuestion q, QuestionStatDto.QuestionStatDtoBuilder builder) {
        Map<String, Object> ratingStats = answerRepository.calculateRatingStatsNative(q.getId());
        Double avg = getDouble(getCaseInsensitive(ratingStats, "avgRating"));
        Long count = getLong(getCaseInsensitive(ratingStats, "totalCount"));
        long totalCount = count != null ? count : 0L;

        int minR = q.getMinRating() != null ? q.getMinRating() : 1;
        int maxR = q.getMaxRating() != null ? q.getMaxRating() : (avg != null && avg > 5.0 ? 10 : 5);

        List<Map<String, Object>> rawDist = answerRepository.countRatingDistributionByQuestionNative(q.getId());
        Map<Integer, Long> distMap = new TreeMap<>();

        for (Map<String, Object> row : rawDist) {
            Long rVal = getLong(getCaseInsensitive(row, "ratingValue"));
            if (rVal != null) {
                if (rVal < minR) minR = rVal.intValue();
                if (rVal > maxR) maxR = rVal.intValue();
            }
        }

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

                int category = classifyNpsCategory(score, maxR);
                if (category < 0) detractorCount += rCnt;
                else if (category == 0) passiveCount += rCnt;
                else promoterCount += rCnt;
            }
        }

        // 若無原生數字作答，自文字作答解析補全
        if (totalCount == 0L) {
            List<String> rawTexts = answerRepository.findTextAnswersByQuestionNative(q.getId());
            for (String t : rawTexts) {
                Integer score = RatingScoreParser.parseRating(t);
                if (score != null) {
                    totalCount++;
                    distMap.put(score, distMap.getOrDefault(score, 0L) + 1);
                    allScores.add(score);
                }
            }
            if (totalCount > 0) {
                avg = allScores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            }
        }

        Collections.sort(allScores);
        double median = 0.0;
        if (!allScores.isEmpty()) {
            int size = allScores.size();
            median = (size % 2 == 1) ? allScores.get(size / 2) : (allScores.get(size / 2 - 1) + allScores.get(size / 2)) / 2.0;
        }

        double stdDev = 0.0;
        if (totalCount > 1 && avg != null) {
            double sumSqDiff = 0.0;
            for (int score : allScores) {
                sumSqDiff += Math.pow(score - avg, 2);
            }
            stdDev = Math.round(Math.sqrt(sumSqDiff / totalCount) * 10.0) / 10.0;
        }

        int itemNps = totalCount > 0 ? (int) Math.round(((double) (promoterCount - detractorCount) / totalCount) * 100.0) : 0;
        Map<String, Long> npsBreakdown = Map.of(
                "detractor", detractorCount,
                "passive", passiveCount,
                "promoter", promoterCount
        );

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
    }

    /**
     * 統計文字簡答/日期/時間題型作答明細
     */
    private void populateTextQuestionStats(SurveyQuestion q, QuestionStatDto.QuestionStatDtoBuilder builder) {
        List<Map<String, Object>> structuredFeedbacks = answerRepository.findStructuredTextAnswersByQuestionNative(q.getId());
        builder.totalAnswers((long) structuredFeedbacks.size())
                .textFeedbacks(new ArrayList<>(structuredFeedbacks.stream().limit(100).collect(Collectors.toList())));
    }

    /**
     * 判定量表分數之 NPS 分組
     * - 10 分量表 (上限 > 5)：0~6 批評者、7~8 被動者、9~10 推薦者 (標準 NPS 定義)
     * - 5 分量表：1~2 批評者、3 被動者、4~5 推薦者
     * @param score 分數
     * @param maxRating 量表上限 (必須先掃過全部分數再決定，不可邊讀邊推斷)
     * @return 1 推薦者、0 被動者、-1 批評者
     */
    private int classifyNpsCategory(int score, int maxRating) {
        if (maxRating > 5) {
            return score <= 6 ? -1 : (score <= 8 ? 0 : 1);
        }
        return score <= 2 ? -1 : (score == 3 ? 0 : 1);
    }

    /**
     * 計算問卷整體 NPS 淨推薦值 (-100 ~ 100)，以問卷中第一個評分題為準
     * [Why]: 沒有評分題或尚無評分作答時回傳 null (前端顯示 0)，不以任何假基準值填充
     * @param survey 問卷實體物件
     * @return NPS 分值；無資料時為 null
     */
    private Integer calculateSurveyNps(Survey survey) {
        Optional<SurveyQuestion> ratingQ = survey.getQuestions().stream()
                .filter(q -> q.getType() == QuestionType.RATING)
                .findFirst();

        if (ratingQ.isEmpty()) {
            return null;
        }

        Long qId = ratingQ.get().getId();
        List<Map<String, Object>> rawDist = answerRepository.countRatingDistributionByQuestionNative(qId);
        long detractorCount = 0L;
        long promoterCount = 0L;
        long totalCount = 0L;
        // [Why]: 量表上限須先看完所有分數才能決定 (例如出現 9 分即為 10 分量表)，
        //        若邊讀邊推斷，結果會因資料順序不同而改變
        int configuredMax = ratingQ.get().getMaxRating() != null ? ratingQ.get().getMaxRating() : 5;
        int maxR = rawDist.stream()
                .map(row -> getLong(getCaseInsensitive(row, "ratingValue")))
                .filter(Objects::nonNull)
                .mapToInt(Long::intValue)
                .reduce(configuredMax, Math::max);

        for (Map<String, Object> row : rawDist) {
            Long rVal = getLong(getCaseInsensitive(row, "ratingValue"));
            Long rCnt = getLong(getCaseInsensitive(row, "ratingCount"));
            if (rVal != null && rCnt != null) {
                int score = rVal.intValue();
                totalCount += rCnt;
                int category = classifyNpsCategory(score, maxR);
                if (category < 0) detractorCount += rCnt;
                else if (category > 0) promoterCount += rCnt;
            }
        }

        if (totalCount == 0) {
            return null;
        }
        return (int) Math.round(((double) (promoterCount - detractorCount) / totalCount) * 100.0);
    }

    /**
     * 不區分大小寫自 Map 讀取屬性值，相容不同資料庫驅動欄位別名慣例
     * @param map 來源 Map 映射表
     * @param key 欲查找之鍵值
     * @return 屬性物件，查無時回傳 null
     */
    private Object getCaseInsensitive(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 將資料庫聚合結果轉為 Double (MySQL 驅動可能回傳 BigDecimal、Double 等不同 Number 型別)
     * @param val 聚合欄位值
     * @return Double 數值；為 null (例如 AVG 於無資料時) 時回傳 null
     */
    private Double getDouble(Object val) {
        return val instanceof Number number ? number.doubleValue() : null;
    }

    /**
     * 將資料庫聚合結果轉為 Long (MySQL 驅動可能回傳 BigInteger、Long、Integer 等不同 Number 型別)
     * @param val 聚合欄位值
     * @return Long 數值；為 null 時回傳 null
     */
    private Long getLong(Object val) {
        return val instanceof Number number ? number.longValue() : null;
    }

    /**
     * 全站維運與營運概覽統計 KPI (Admin 專屬業務邏輯)
     * @return 全站統計數據 (總會員數、總問卷數、總填答數、伺服器時間)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformOverview() {
        long totalUsers = userRepository.countActiveUsersNative();
        long totalSurveys = surveyRepository.countActiveSurveysNative();
        long totalSubmissions = submissionRepository.countTotalSubmissionsNative();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalSurveys", totalSurveys);
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("serverTime", LocalDateTime.now());

        return stats;
    }
}
