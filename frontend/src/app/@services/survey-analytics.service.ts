/**
 * ============================================================================
 * 檔案名稱: survey-analytics.service.ts
 * 所屬模組: 問卷統計與數據分析服務模組 (Survey Analytics Service Module)
 * 責任描述: 提供問卷填答統計總覽、題型深度統計、受訪者輪廓分佈、
 *           權限核對，以及 CSV / Excel / SPSS / JSON 四種格式匯出功能；
 *           報表資料一律取自後端 REST API，不產生任何模擬數據。
 * 架構分層: Service Layer / Analytics & Aggregation Layer
 * 聯動關係:
 *   - 上游依賴: ApiClientService, SurveyExportService, ../@interfaces/survey-analytics-model
 *   - 下游消費: SurveyManagementComponent
 * 邊界處理: 權限由後端核對 (管理員可檢視全站、會員僅限所屬問卷)；查無資料時拋出錯誤
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiClientService } from './api-client.service';
import { SurveyExportService } from './survey-export.service';
import {
  SurveyAnalyticsReport,
  QuestionAnalyticsItem,
  DemographicStats,
  TimelineSubmissionItem,
  ScaleStats,
  ScaleDistributionItem,
  OptionStatItem,
  TextResponseItem,
  ExportFormat,
} from '../@interfaces/survey-analytics-model';
import { SurveyPayload, SurveySummaryItem } from '../@interfaces/survey-model';

/** 後端題型 → 分析頁顯示題型 (是非題以單選圖表呈現；日期、時間以文字清單呈現) */
const ANALYTICS_QUESTION_TYPE: Record<string, string> = {
  SINGLE: 'radio',
  MULTI: 'checkbox',
  RATING: 'scale',
  TEXT: 'text',
  BOOLEAN: 'radio',
  DATE: 'text',
  TIME: 'text',
};

/** 年齡層分組鍵 (對應 DemographicStats.ageDistribution 的欄位) */
type AgeBucket = keyof DemographicStats['ageDistribution'];

/**
 * 計算百分比並四捨五入到小數 1 位 (分母為 0 時為 0)
 * @param count 分子
 * @param total 分母
 * @returns 百分比數值，例如 33.3
 */
function toPercentage(count: number, total: number): number {
  return total > 0 ? Math.round((count / total) * 1000) / 10 : 0;
}

/**
 * 格式化為臺灣慣用之本地時間文字 (YYYY/MM/DD HH:mm:ss)
 * @param target 要格式化之時間
 * @returns 時間文字
 */
function formatTaiwanDate(target: Date): string {
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${target.getFullYear()}/${pad(target.getMonth() + 1)}/${pad(target.getDate())} `
    + `${pad(target.getHours())}:${pad(target.getMinutes())}:${pad(target.getSeconds())}`;
}

/**
 * 把後端年齡層鍵 (例如「18-24歲」、「under18」、單一年齡「30」) 歸入前端五個年齡層之一
 * @param key 後端 ageGroupDistribution 的鍵
 * @returns 年齡層分組鍵；無法辨識者歸入 25~34 歲 (人數最多的青壯年組)
 */
function classifyAgeBucket(key: string): AgeBucket {
  const numMatch = key.match(/\d+/);
  const isRange = key.includes('-') || key.includes('~') || key.includes('至');
  // [Why]: 只有「單一年齡數字」才用數值判斷；區間文字 (如 25-34歲) 以字面比對，避免取到區間的第一個數字誤判
  const singleAge = numMatch && !isRange ? parseInt(numMatch[0], 10) : null;
  const inRange = (min: number, max: number): boolean => singleAge !== null && singleAge >= min && singleAge <= max;
  const hasRangeText = (from: number, to: number): boolean =>
    ['-', '~', '至'].some((separator) => key.includes(`${from}${separator}${to}`));

  if (key.toLowerCase().includes('under') || key.includes('18歲以下') || key.includes('未滿18') || key.includes('<18') || inRange(0, 17)) {
    return 'under18';
  }
  if (hasRangeText(18, 24) || inRange(18, 24)) {
    return 'age18to24';
  }
  if (hasRangeText(25, 34) || inRange(25, 34)) {
    return 'age25to34';
  }
  if (hasRangeText(35, 44) || inRange(35, 44)) {
    return 'age35to44';
  }
  if (['45', '55', '65', '以上'].some((token) => key.includes(token)) || inRange(45, Number.MAX_SAFE_INTEGER)) {
    return 'age45plus';
  }
  return 'age25to34';
}

@Injectable({
  providedIn: 'root',
})
export class SurveyAnalyticsService {
  private readonly apiClient = inject(ApiClientService);
  private readonly exportService = inject(SurveyExportService);

  /**
   * 驗證當前使用者是否具備檢視目標問卷數據分析之權限 (只決定是否顯示分析按鈕，真正把關在後端)
   * 規範：
   *   1. 系統管理員 (isAdmin = true) 具備全域權限，可檢視全站所有問卷數據。
   *   2. 一般會員僅能檢視自身發起擁有之問卷 (survey.authorId === currentUserId)。
   *
   * @param survey 目標問卷摘要或實體物件
   * @param currentUserId 當前登入者 ID
   * @param isAdmin 當前登入者是否為管理員
   * @returns boolean 是否具備存取權限
   */
  public canAccessAnalytics(
    survey: SurveySummaryItem | SurveyPayload,
    currentUserId: string,
    isAdmin: boolean,
  ): boolean {
    if (isAdmin) {
      return true;
    }
    if (!survey || !currentUserId) {
      return false;
    }
    const authorId = String(survey.authorId ?? (survey as any).userId ?? '');
    return authorId === String(currentUserId);
  }

  /**
   * 取得指定問卷之數據分析統計報表 (GET /api/analytics/surveys/{surveyId})，並轉成分析頁使用之 SurveyAnalyticsReport；
   * 後端無資料或異常時拋出錯誤由呼叫端顯示
   *
   * @param surveyId 目標問卷編號 (可含非數字前綴，只取數字)
   * @returns Observable<SurveyAnalyticsReport> 數據分析報表酬載
   */
  public getSurveyAnalytics(surveyId: string): Observable<SurveyAnalyticsReport> {
    const numericId = parseInt(surveyId.replace(/\D/g, ''), 10);
    // [Why]: 無法解析之編號直接回報錯誤，不可退回第 1 號問卷之報表
    if (isNaN(numericId)) {
      return throwError(() => new Error('問卷編號格式錯誤'));
    }
    return this.apiClient.get<any>(`/api/analytics/surveys/${numericId}`).pipe(
      map((res) => {
        if (res && res.success && res.data) {
          return this.toReport(res.data);
        }
        throw new Error('查無問卷數據報表或後端服務異常');
      }),
    );
  }

  /**
   * 匯出問卷數據分析報表為指定格式並觸發瀏覽器下載 (委派專職 SurveyExportService)
   *
   * @param report 完整問卷分析報表物件
   * @param format 匯出格式種類 (json / csv / excel / spss)
   */
  public exportReport(report: SurveyAnalyticsReport, format: ExportFormat): void {
    this.exportService.exportReport(report, format);
  }

  /**
   * 把後端 AnalyticsResponse 組成前端報表：KPI、每日趨勢 (含累計)、各題統計、受訪者輪廓與最新回覆時間
   * @param d 後端回應之 data
   * @returns 分析頁報表
   */
  private toReport(d: any): SurveyAnalyticsReport {
    const kpi = d.kpi || {};
    const surveyTotal = kpi.totalResponses || 0;
    // [Why]: 無作答紀錄時顯示 0，嚴禁以假數值填充
    const avgMinutes = kpi.averageDurationSeconds ? Math.round((kpi.averageDurationSeconds / 60) * 10) / 10 : 0;

    let cumulative = 0;
    const timeline: TimelineSubmissionItem[] = (d.trends || []).map((t: any) => {
      cumulative += t.count;
      return { date: t.date, count: t.count, cumulativeCount: cumulative };
    });

    // 最新回覆時間：取自後端最新作答紀錄；有作答但後端沒給時間時顯示「已回收作答」
    let lastResponseAt = '尚無回覆';
    if (d.lastResponseAt) {
      lastResponseAt = d.lastResponseAt.includes('T') ? formatTaiwanDate(new Date(d.lastResponseAt)) : d.lastResponseAt;
    } else if (surveyTotal > 0) {
      lastResponseAt = '已回收作答';
    }

    return {
      surveyId: String(d.surveyId),
      surveyTitle: d.surveyTitle,
      category: d.category || '綜合調查',
      authorId: d.authorUsername || '系統發起人',
      authorName: d.authorUsername || '系統發起人',
      isPublished: d.isPublicToLobby ?? true,
      startDate: d.startDate || undefined,
      endDate: d.endDate || undefined,
      generatedAt: formatTaiwanDate(new Date()),
      totalResponses: surveyTotal,
      completionRate: kpi.completionRate ?? 0,
      averageDurationMinutes: avgMinutes,
      lastResponseAt,
      last24hCount: kpi.todayResponses || 0,
      npsScore: kpi.netPromoterScore ?? 0,
      timeline,
      demographics: d.demographics ? this.toDemographics(d.demographics, surveyTotal) : undefined,
      questionsAnalytics: (d.questionStats || []).map((qs: any, idx: number) => this.toQuestionAnalytics(qs, idx, kpi)),
    };
  }

  /**
   * 把單題統計轉成分析頁格式：選項分佈、量表統計、文字回覆摘錄，以及作答數、跳過數與作答率
   * @param qs 後端單題統計
   * @param idx 題目順序 (從 0 開始)
   * @param kpi 問卷 KPI (取總回收數與問卷 NPS)
   * @returns 單題分析資料
   */
  private toQuestionAnalytics(qs: any, idx: number, kpi: any): QuestionAnalyticsItem {
    const frontendType = ANALYTICS_QUESTION_TYPE[qs.type] || 'text';

    // 優先讀取後端結構化 optionsList (以 optionId 分組，同名選項各自統計)，沒有時改讀 optionCounts
    const optionStats: OptionStatItem[] = (qs.optionsList && qs.optionsList.length > 0)
      ? qs.optionsList.map((opt: any) => ({
          optionId: opt.optionId,
          text: opt.text,
          count: opt.count || 0,
          percentage: opt.percentage || 0,
        }))
      : Object.keys(qs.optionCounts || {}).map((optText) => ({
          text: optText,
          count: qs.optionCounts[optText] || 0,
          percentage: qs.optionPercentages ? (qs.optionPercentages[optText] || 0) : 0,
        }));

    const textResponses: TextResponseItem[] = (qs.textFeedbacks || []).map((item: any, fIdx: number) => ({
      id: `TR-${qs.questionId || idx + 1}-${fIdx + 1}`,
      content: typeof item === 'string' ? item : (item?.content || ''),
      // [Why]: typeof null 也是 'object'，一律用 ?. 讀取，避免後端回傳 null 項目時整份報表失敗
      respondentName: item?.respondentName
        ? String(item.respondentName).replace('受訪者 #', '訪客 ').replace('填答者 #', '訪客 ')
        : `訪客 ${fIdx + 1}`,
      submittedAt: item?.submittedAt || '近期填答',
    }));
    const textStats = {
      totalCount: textResponses.length,
      averageLength: textResponses.length > 0
        ? Math.round(textResponses.reduce((acc, r) => acc + (r.content ? r.content.length : 0), 0) / textResponses.length)
        : 0,
    };

    const surveyTotal = kpi.totalResponses || 0;
    const validCount = qs.totalAnswers ?? (frontendType === 'text' ? textResponses.length : surveyTotal);
    // [Why]: 以問卷總回收數計算真實跳過筆數與答題率 (不顯示虛假的 100%)
    const skippedCount = surveyTotal > validCount ? surveyTotal - validCount : 0;
    const responseRate = surveyTotal > 0 ? toPercentage(validCount, surveyTotal) : (validCount > 0 ? 100 : 0);
    const questionText = qs.questionText || `題目 ${idx + 1}`;

    return {
      questionUid: qs.questionCode || `Q${idx + 1}`,
      questionId: idx + 1,
      description: questionText,
      questionText,
      type: frontendType,
      isRequired: true,
      totalAnswerCount: validCount,
      totalResponses: validCount,
      skippedCount,
      responseRate,
      optionsStats: optionStats.length > 0 ? optionStats : undefined,
      scaleStats: frontendType === 'scale' ? this.toScaleStats(qs, kpi) : undefined,
      textResponses: textResponses.length > 0 ? textResponses : undefined,
      textStats: frontendType === 'text' ? textStats : undefined,
      isConditionTriggered: qs.isConditionTriggered ?? false,
      triggerConditionDescription: qs.triggerConditionDescription,
      triggeredEligibleCount: qs.triggeredEligibleCount,
      triggeredResponseRate: qs.triggeredResponseRate,
    };
  }

  /**
   * 組出量表題統計：補齊 min~max 每個分數的人數與佔比 (沒人選的分數顯示 0)，以及平均、中位數、眾數、最高最低分、標準差、NPS
   * @param qs 後端單題統計 (評分題)
   * @param kpi 問卷 KPI (單題沒有 NPS 時沿用問卷 NPS)
   * @returns 量表統計
   */
  private toScaleStats(qs: any, kpi: any): ScaleStats {
    const avg = qs.averageRating || 0;
    const minR = qs.minRating ?? 1;
    // [Why]: 舊資料沒有 maxRating 時，平均超過 5 分代表必定是 10 分量表
    const maxR = qs.maxRating ?? (avg > 5 ? 10 : 5);
    const distMap: Record<string, number> = qs.ratingDistribution || {};
    const totalAnswers = qs.totalAnswers || 0;

    const distribution: ScaleDistributionItem[] = [];
    for (let score = minR; score <= maxR; score++) {
      const count = Number(distMap[score] ?? 0);
      distribution.push({ score, count, percentage: toPercentage(count, totalAnswers) });
    }

    // 最高分與最低分：從實際有人選的分數中取，沒有作答時用量表範圍
    const answeredScores = Object.keys(distMap)
      .map(Number)
      .filter((score) => !isNaN(score) && (distMap[score] || 0) > 0);
    const highestScore = answeredScores.length > 0 ? Math.max(...answeredScores) : maxR;
    const lowestScore = answeredScores.length > 0 ? Math.min(...answeredScores) : minR;

    return {
      averageScore: avg,
      medianScore: qs.medianRating ?? Math.round(avg * 10) / 10,
      modeScore: qs.modeRating ?? highestScore,
      highestScore,
      lowestScore,
      standardDeviation: qs.standardDeviation ?? 0,
      npsScore: qs.npsScore ?? (kpi.netPromoterScore || 0),
      distribution,
    };
  }

  /**
   * 把後端受訪者輪廓 (性別、年齡層、產業分佈) 轉為前端格式；全部都沒有資料時回傳 undefined (分析頁不顯示該區塊)
   * @param rawDemo 後端 demographics
   * @param surveyTotal 問卷總回收數 (計算產業佔比)
   * @returns 受訪者輪廓，或 undefined
   */
  private toDemographics(rawDemo: any, surveyTotal: number): DemographicStats | undefined {
    const gMap = rawDemo.genderDistribution || {};
    const male = Number(gMap['男'] ?? gMap['male'] ?? gMap['MALE'] ?? 0);
    const female = Number(gMap['女'] ?? gMap['female'] ?? gMap['FEMALE'] ?? 0);
    const other = Number(gMap['其他'] ?? gMap['other'] ?? gMap['OTHER'] ?? 0);

    const ageDistribution: DemographicStats['ageDistribution'] = {
      under18: 0, age18to24: 0, age25to34: 0, age35to44: 0, age45plus: 0,
    };
    for (const [key, value] of Object.entries(rawDemo.ageGroupDistribution || {})) {
      ageDistribution[classifyAgeBucket(key)] += Number(value) || 0;
    }

    // 前五大任職產業 (依人數由多到少)
    const topJobIndustries = Object.entries(rawDemo.industryDistribution || {})
      .map(([industry, count]) => {
        const c = Number(count) || 0;
        return { industry, count: c, percentage: toPercentage(c, surveyTotal) };
      })
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);

    const hasAgeData = Object.values(ageDistribution).some((count) => count > 0);
    const hasDemographicData = male > 0 || female > 0 || other > 0 || hasAgeData || topJobIndustries.length > 0;
    if (!hasDemographicData) {
      return undefined;
    }
    return {
      genderDistribution: { male, female, other },
      ageDistribution,
      topJobIndustries,
    };
  }
}
