/**
 * ============================================================================
 * 檔案名稱: survey-analytics.service.ts
 * 所屬模組: 問卷統計與數據分析服務模組 (Survey Analytics Service Module)
 * 責任描述: 提供問卷填答統計總覽、題型深度統計、受訪者輪廓分佈、
 *           權限核對，以及 CSV / Excel / SPSS / JSON 四種格式匯出功能；
 *           預留接軌 Java Spring Boot + MySQL 之 REST API 介面。
 * 架構分層: Service Layer / Analytics & Aggregation Layer
 * 聯動關係:
 *   - 上游依賴: rxjs, ../@interfaces/survey-analytics-model, ../@interfaces/survey-model, ../datas/surveys.json
 *   - 下游消費: SurveyManagementComponent
 * 邊界處理: 管理員可檢視全站；會員僅能檢視所屬問卷；查無問卷時安全回退至第一筆種子資料
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ApiClientService } from './api-client.service';
import { SurveyExportService } from './survey-export.service';
import { SurveyAnalyticsFallbackService } from './survey-analytics-fallback.service';
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

@Injectable({
  providedIn: 'root',
})
export class SurveyAnalyticsService {

  // ─────────────────────────────────────────────────────────────────────────
  // 公開 API：權限核驗
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 驗證當前使用者是否具備檢視目標問卷數據分析之權限
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
    // 僅允許發起人本人或最高管理員存取數據分析報表，嚴格禁止任何魔術字串與後門
    const authorId = String(survey.authorId ?? (survey as any).userId ?? '');
    return authorId === String(currentUserId);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 公開 API：統計報表查詢
  // ─────────────────────────────────────────────────────────────────────────

  private readonly apiClient = inject(ApiClientService);

  /**
   * 取得指定問卷之數據分析統計報表 (優先連線後端 GET /api/analytics/surveys/{surveyId}，斷線平滑降級)
   *
   * @param surveyId 目標問卷編號
   * @returns Observable<SurveyAnalyticsReport> 數據分析報表酬載
   */
  public getSurveyAnalytics(surveyId: string): Observable<SurveyAnalyticsReport> {
    const numericId = parseInt(surveyId.replace(/\D/g, ''), 10);
    const targetUrl = isNaN(numericId)
      ? `/api/analytics/surveys/1`
      : `/api/analytics/surveys/${numericId}`;

    return this.apiClient.get<any>(targetUrl).pipe(
      map((res) => {
        if (res && res.success && res.data) {
          const d = res.data;
          const kpi = d.kpi || {};
          const avgMinutes = kpi.averageDurationSeconds
            ? Math.round((kpi.averageDurationSeconds / 60) * 10) / 10
            : 2.5;

          let cum = 0;
          const timeline: TimelineSubmissionItem[] = (d.trends || []).map((t: any) => {
            cum += t.count;
            return {
              date: t.date,
              count: t.count,
              cumulativeCount: cum,
            };
          });

          const questionsAnalytics: QuestionAnalyticsItem[] = (d.questionStats || []).map((qs: any, idx: number) => {
            const typeMap: Record<string, string> = {
              SINGLE: 'radio',
              MULTI: 'checkbox',
              RATING: 'scale',
              TEXT: 'text',
              BOOLEAN: 'radio',
              DATE: 'text',
              TIME: 'text',
            };
            const frontendType = typeMap[qs.type] || 'text';

            const optionStats: OptionStatItem[] = Object.keys(qs.optionCounts || {}).map((optText) => ({
              text: optText,
              count: qs.optionCounts[optText] || 0,
              percentage: qs.optionPercentages ? (qs.optionPercentages[optText] || 0) : 0,
            }));

            let scaleStats: ScaleStats | undefined;
            if (frontendType === 'scale') {
              const avg = qs.averageRating || 0;
              const minR = qs.minRating !== undefined && qs.minRating !== null ? qs.minRating : 1;
              const maxR = qs.maxRating !== undefined && qs.maxRating !== null ? qs.maxRating : (avg > 5 ? 10 : 5);
              const distMap: Record<string | number, number> = qs.ratingDistribution || {};
              const totalAnswers = qs.totalAnswers || 0;

              // 動態產生 minR 到 maxR 的完整分數刻度
              const scores: number[] = [];
              for (let s = minR; s <= maxR; s++) {
                scores.push(s);
              }

              const distribution: ScaleDistributionItem[] = scores.map((sc) => {
                const count = Number(distMap[sc] ?? distMap[String(sc)] ?? 0);
                const percentage = totalAnswers > 0 ? Math.round((count / totalAnswers) * 1000) / 10 : 0;
                return {
                  score: sc,
                  count,
                  percentage,
                };
              });

              // 最高分與最低分（從實際有作答人次之分數中取，若無作答則取預設範圍）
              const answeredScores = Object.keys(distMap)
                .map(Number)
                .filter((s) => !isNaN(s) && (distMap[s] || distMap[String(s)] || 0) > 0);
              const highestScore = answeredScores.length > 0 ? Math.max(...answeredScores) : maxR;
              const lowestScore = answeredScores.length > 0 ? Math.min(...answeredScores) : minR;

              scaleStats = {
                averageScore: avg,
                medianScore: qs.medianRating !== undefined && qs.medianRating !== null ? qs.medianRating : Math.round(avg * 10) / 10,
                modeScore: qs.modeRating !== undefined && qs.modeRating !== null ? qs.modeRating : highestScore,
                highestScore,
                lowestScore,
                standardDeviation: qs.standardDeviation !== undefined && qs.standardDeviation !== null ? qs.standardDeviation : 0,
                npsScore: qs.npsScore !== undefined && qs.npsScore !== null ? qs.npsScore : (kpi.netPromoterScore || 0),
                distribution,
              };
            }

            // 結構化文字填答摘錄清單
            const rawFeedbacks: any[] = qs.textFeedbacks || [];
            const textResponses: TextResponseItem[] = rawFeedbacks.map((item: any, fIdx: number) => {
              const content = typeof item === 'string' ? item : (item?.content || '');
              return {
                id: `TR-${qs.questionId || idx + 1}-${fIdx + 1}`,
                content: content,
                respondentName: typeof item === 'object' && item.respondentName
                  ? item.respondentName.replace('受訪者 #', '訪客 ').replace('填答者 #', '訪客 ')
                  : `訪客 ${fIdx + 1}`,
                submittedAt: typeof item === 'object' && item.submittedAt ? item.submittedAt : '近期填答',
                sentiment: 'neutral',
              };
            });

            const textStats = {
              totalCount: textResponses.length,
              averageLength: textResponses.length > 0
                ? Math.round(textResponses.reduce((acc, r) => acc + (r.content ? r.content.length : 0), 0) / textResponses.length)
                : 0,
            };

            const surveyTotal = kpi.totalResponses || 0;
            const validCount = qs.totalAnswers !== undefined && qs.totalAnswers !== null
              ? qs.totalAnswers
              : (frontendType === 'text' ? textResponses.length : surveyTotal);

            // [Why]: 根據問卷總回收數計算真實跳過筆數與真實答題率（拒絕虛假 100%）
            const skippedCount = surveyTotal > validCount ? (surveyTotal - validCount) : 0;
            const responseRate = surveyTotal > 0
              ? Math.round((validCount / surveyTotal) * 1000) / 10
              : (validCount > 0 ? 100 : 0);

            return {
              questionUid: qs.questionCode || `Q${idx + 1}`,
              questionId: idx + 1,
              description: qs.questionText || `題目 ${idx + 1}`,
              questionText: qs.questionText || `題目 ${idx + 1}`,
              type: frontendType,
              isRequired: true,
              totalAnswerCount: validCount,
              totalResponses: validCount,
              skippedCount: skippedCount,
              responseRate: responseRate,
              optionsStats: optionStats.length > 0 ? optionStats : undefined,
              scaleStats,
              textResponses: textResponses.length > 0 ? textResponses : undefined,
              textStats: frontendType === 'text' ? textStats : undefined,
              isConditionTriggered: qs.isConditionTriggered ?? false,
              triggerConditionDescription: qs.triggerConditionDescription,
              triggeredEligibleCount: qs.triggeredEligibleCount,
              triggeredResponseRate: qs.triggeredResponseRate,
            };
          });

            const surveyTotal = kpi.totalResponses || 0;

            // ─────────────────────────────────────────────────────────────
            // 受訪者輪廓人口統計學資料轉換 (後端 DemographicsDto -> 前端 DemographicStats)
            // ─────────────────────────────────────────────────────────────
            let demographics: DemographicStats | undefined;
            if (d.demographics) {
              const rawDemo = d.demographics;
              const gMap = rawDemo.genderDistribution || {};
              const male = Number(gMap['男'] ?? gMap['male'] ?? gMap['MALE'] ?? 0);
              const female = Number(gMap['女'] ?? gMap['female'] ?? gMap['FEMALE'] ?? 0);
              const other = Number(gMap['其他'] ?? gMap['other'] ?? gMap['OTHER'] ?? 0);

              const aMap = rawDemo.ageGroupDistribution || {};
              let under18 = 0;
              let age18to24 = 0;
              let age25to34 = 0;
              let age35to44 = 0;
              let age45plus = 0;

              for (const [k, v] of Object.entries(aMap)) {
                const count = Number(v) || 0;
                const kLower = k.toLowerCase();
                const numMatch = k.match(/\d+/);
                const ageNum = numMatch ? parseInt(numMatch[0], 10) : null;
                const isRange = k.includes('-') || k.includes('~') || k.includes('至');

                if (kLower.includes('under') || k.includes('18歲以下') || k.includes('未滿18') || k.includes('<18') || (ageNum !== null && !isRange && ageNum < 18)) {
                  under18 += count;
                } else if (k.includes('18-24') || k.includes('18~24') || k.includes('18至24') || (ageNum !== null && !isRange && ageNum >= 18 && ageNum <= 24)) {
                  age18to24 += count;
                } else if (k.includes('25-34') || k.includes('25~34') || k.includes('25至34') || (ageNum !== null && !isRange && ageNum >= 25 && ageNum <= 34)) {
                  age25to34 += count;
                } else if (k.includes('35-44') || k.includes('35~44') || k.includes('35至44') || (ageNum !== null && !isRange && ageNum >= 35 && ageNum <= 44)) {
                  age35to44 += count;
                } else if (k.includes('45') || k.includes('55') || k.includes('65') || k.includes('以上') || (ageNum !== null && !isRange && ageNum >= 45)) {
                  age45plus += count;
                } else {
                  // 其他非空資料納入青壯年組
                  age25to34 += count;
                }
              }

              // 前五大任職產業
              const indMap = rawDemo.industryDistribution || {};
              const topJobIndustries = Object.entries(indMap)
                .map(([industry, count]) => {
                  const c = Number(count) || 0;
                  const pct = surveyTotal > 0 ? Math.round((c / surveyTotal) * 1000) / 10 : 0;
                  return { industry, count: c, percentage: pct };
                })
                .sort((a, b) => b.count - a.count)
                .slice(0, 5);

              // 前五大居住地區
              const resMap = rawDemo.residenceDistribution || {};
              const topRegions = Object.entries(resMap)
                .map(([region, count]) => {
                  const c = Number(count) || 0;
                  const pct = surveyTotal > 0 ? Math.round((c / surveyTotal) * 1000) / 10 : 0;
                  return { region, count: c, percentage: pct };
                })
                .sort((a, b) => b.count - a.count)
                .slice(0, 5);

              const hasAgeData = under18 > 0 || age18to24 > 0 || age25to34 > 0 || age35to44 > 0 || age45plus > 0;
              const hasDemographicData =
                male > 0 || female > 0 || other > 0 || hasAgeData ||
                topJobIndustries.length > 0 || topRegions.length > 0;

              if (hasDemographicData) {
                demographics = {
                  genderDistribution: { male, female, other },
                  ageDistribution: { under18, age18to24, age25to34, age35to44, age45plus },
                  topJobIndustries,
                  topRegions,
                };
              }
            }

            // 臺灣時間格式化工具 (YYYY/MM/DD HH:mm:ss)
            const formatTaiwanDate = (target: Date = new Date()): string => {
              const y = target.getFullYear();
              const m = String(target.getMonth() + 1).padStart(2, '0');
              const day = String(target.getDate()).padStart(2, '0');
              const h = String(target.getHours()).padStart(2, '0');
              const min = String(target.getMinutes()).padStart(2, '0');
              const s = String(target.getSeconds()).padStart(2, '0');
              return `${y}/${m}/${day} ${h}:${min}:${s}`;
            };

            // 最新回覆時間：真實取自後端資料庫最新作答紀錄；若尚無任何作答則顯示「尚無回覆」
            let realLastResponse = '尚無回覆';
            if (d.lastResponseAt) {
              realLastResponse = d.lastResponseAt.includes('T')
                ? formatTaiwanDate(new Date(d.lastResponseAt))
                : d.lastResponseAt;
            } else if ((kpi.totalResponses || 0) > 0) {
              realLastResponse = '已回收作答';
            }

            const report: SurveyAnalyticsReport = {
              surveyId: String(d.surveyId),
              surveyTitle: d.surveyTitle,
              category: d.category || '綜合調查',
              authorId: d.authorUsername || '系統發起人',
              authorName: d.authorUsername || '系統發起人',
              isPublished: d.isPublicToLobby ?? true,
              startDate: d.startDate || undefined,
              endDate: d.endDate || undefined,
              generatedAt: formatTaiwanDate(new Date()),
              totalResponses: kpi.totalResponses || 0,
              completionRate: kpi.completionRate || 100,
              averageDurationMinutes: avgMinutes,
              lastResponseAt: realLastResponse,
              last24hCount: kpi.todayResponses || 0,
              npsScore: kpi.netPromoterScore || 65,
              timeline,
              demographics,
              questionsAnalytics,
            };
            return report;
          }
        throw new Error('查無問卷數據報表或後端服務異常');
      })
    );
  }

  private readonly exportService = inject(SurveyExportService);
  private readonly fallbackService = inject(SurveyAnalyticsFallbackService);

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
   * 取得離線降級模擬報表 (委派專職 SurveyAnalyticsFallbackService)
   */
  public generateFallbackReport(
    survey: SurveyPayload & { id?: string; authorId?: string },
    surveyId: string,
  ): SurveyAnalyticsReport {
    return this.fallbackService.generateFallbackReport(survey, surveyId);
  }
}
