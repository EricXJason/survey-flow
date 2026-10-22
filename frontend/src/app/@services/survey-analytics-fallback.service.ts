/**
 * ============================================================================
 * 檔案名稱: survey-analytics-fallback.service.ts
 * 所屬模組: 統計報表離線降級服務
 * 責任描述: 當後端伺服器離線或開發單元測試時，依據問卷結構提供相容之模擬數據報表
 * 架構分層: Service Layer (SRP 單一職責原則落實)
 * 聯動關係:
 *   - 上游依賴: SurveyAnalyticsReport, SurveyPayload
 *   - 下游消費: SurveyAnalyticsService
 * 邊界處理: 提供符合真實商業邏輯之逼真樣本分佈，杜絕空指針
 * ============================================================================
 */

import { Injectable } from '@angular/core';
import {
  DemographicStats,
  QuestionAnalyticsItem,
  ScaleStats,
  SurveyAnalyticsReport,
  TimelineSubmissionItem,
} from '../@interfaces/survey-analytics-model';
import { SurveyPayload } from '../@interfaces/survey-model';

@Injectable({
  providedIn: 'root',
})
export class SurveyAnalyticsFallbackService {
  /**
   * 依據問卷結構產生離線降級模擬報表
   */
  public generateFallbackReport(
    survey: SurveyPayload & { id?: string; authorId?: string },
    surveyId: string,
  ): SurveyAnalyticsReport {
    const totalResponses = 128;
    const completionRate = 96.8;
    const avgDuration = 3.6;
    const npsScore = 48;

    const questionsAnalytics: QuestionAnalyticsItem[] = (survey.questions ?? []).map((q, idx) => {
      const qType = q.type || 'radio';

      if (qType === 'radio' || qType === 'boolean') {
        const rawOptions = q.options && q.options.length > 0
          ? q.options
          : ['非常滿意', '滿意', '普通', '不滿意', '非常不滿意'];
        const counts = this.distributeCounts(totalResponses, rawOptions.length);
        const optionsStats = rawOptions.map((text, oIdx) => ({
          text,
          count: counts[oIdx],
          percentage: Math.round((counts[oIdx] / totalResponses) * 1000) / 10,
          trendDelta: Math.round((Math.random() - 0.5) * 8 * 10) / 10,
        }));
        const skipped = Math.round(totalResponses * 0.02);

        return {
          questionUid: q.uid || `q_${idx}`,
          questionId: idx + 1,
          type: qType,
          description: q.description,
          isRequired: q.isRequired ?? true,
          totalAnswerCount: totalResponses - skipped,
          skippedCount: skipped,
          responseRate: Math.round(((totalResponses - skipped) / totalResponses) * 1000) / 10,
          optionsStats,
        };
      }

      if (qType === 'checkbox') {
        const rawOptions = q.options && q.options.length > 0
          ? q.options
          : ['功能豐富完整', '介面設計美觀', '操作流程順暢', '跨裝置支援良好', '資料安全可靠'];
        const baseCounts = this.distributeCounts(totalResponses * 1.8, rawOptions.length);
        const optionsStats = rawOptions.map((text, oIdx) => ({
          text,
          count: baseCounts[oIdx],
          percentage: Math.min(100, Math.round((baseCounts[oIdx] / totalResponses) * 1000) / 10),
          trendDelta: Math.round((Math.random() - 0.5) * 6 * 10) / 10,
        }));

        return {
          questionUid: q.uid || `q_${idx}`,
          questionId: idx + 1,
          type: qType,
          description: q.description,
          isRequired: q.isRequired ?? true,
          totalAnswerCount: totalResponses,
          skippedCount: 0,
          responseRate: 100,
          optionsStats,
        };
      }

      if (qType === 'scale') {
        const scores = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
        const weights = [1, 1, 2, 4, 8, 14, 22, 36, 26, 14];
        const distribution = scores.map((score, sIdx) => ({
          score,
          count: weights[sIdx],
          percentage: Math.round((weights[sIdx] / totalResponses) * 1000) / 10,
        }));

        const scaleStats: ScaleStats = {
          averageScore: 7.8,
          medianScore: 8.0,
          modeScore: 8.0,
          highestScore: 10,
          lowestScore: 1,
          standardDeviation: 1.42,
          npsScore: 52,
          distribution,
        };

        return {
          questionUid: q.uid || `q_${idx}`,
          questionId: idx + 1,
          type: qType,
          description: q.description,
          isRequired: q.isRequired ?? true,
          totalAnswerCount: totalResponses,
          skippedCount: 0,
          responseRate: 100,
          scaleStats,
        };
      }

      // 文字 / 時間 / 日期題
      return {
        questionUid: q.uid || `q_${idx}`,
        questionId: idx + 1,
        type: qType,
        description: q.description,
        isRequired: q.isRequired ?? true,
        totalAnswerCount: 118,
        skippedCount: 10,
        responseRate: 92.2,
        textResponses: [
          { id: 'TR-1', respondentName: '訪客 1', content: '整體使用體驗非常順暢，題目指引很明確。', submittedAt: '2026-10-21 14:32' },
          { id: 'TR-2', respondentName: '訪客 2', content: '手機端填寫介面自適應良好，排版乾淨。', submittedAt: '2026-10-21 16:05' },
          { id: 'TR-3', respondentName: '訪客 3', content: '希望未來能增加更多自訂主題色系功能。', submittedAt: '2026-10-22 09:18' },
        ],
      };
    });

    return {
      surveyId,
      surveyTitle: survey.title || '問卷數據分析報告',
      category: survey.category || '綜合調研',
      authorId: survey.authorId || 'author',
      authorName: '調研發起人',
      isPublished: true,
      generatedAt: new Date().toLocaleString('zh-TW'),
      totalResponses,
      completionRate,
      averageDurationMinutes: avgDuration,
      lastResponseAt: '剛剛',
      npsScore,
      timeline: this.buildTimeline(totalResponses),
      questionsAnalytics,
      demographics: this.buildDemographics(totalResponses),
    };
  }

  private distributeCounts(total: number, parts: number): number[] {
    const raw = Array.from({ length: parts }, () => Math.random() + 0.2);
    const sum = raw.reduce((a, b) => a + b, 0);
    return raw.map((r) => Math.max(1, Math.round((r / sum) * total)));
  }

  private buildTimeline(total: number): TimelineSubmissionItem[] {
    const days = 14;
    const items: TimelineSubmissionItem[] = [];
    let cum = 0;
    const now = new Date();

    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now.getTime() - i * 86400000);
      const dateStr = `${d.getMonth() + 1}/${d.getDate()}`;
      const dailyCount = Math.round(total / days + (Math.random() - 0.5) * 6);
      cum += dailyCount;
      items.push({ date: dateStr, submissionsCount: dailyCount, cumulativeCount: cum });
    }
    return items;
  }

  private buildDemographics(total: number): DemographicStats {
    return {
      genderDistribution: {
        male: Math.round(total * 0.48),
        female: Math.round(total * 0.46),
        other: Math.round(total * 0.06),
      },
      ageDistribution: {
        under18: Math.round(total * 0.05),
        age18to24: Math.round(total * 0.22),
        age25to34: Math.round(total * 0.41),
        age35to44: Math.round(total * 0.23),
        age45plus: Math.round(total * 0.09),
      },
      topJobIndustries: [
        { industry: '資訊科技 / 軟體', count: Math.round(total * 0.38), percentage: 38.0 },
        { industry: '教育與學術研究', count: Math.round(total * 0.24), percentage: 24.0 },
        { industry: '金融與商業服務', count: Math.round(total * 0.18), percentage: 18.0 },
        { industry: '製造與零售業', count: Math.round(total * 0.12), percentage: 12.0 },
        { industry: '其他領域', count: Math.round(total * 0.08), percentage: 8.0 },
      ],
    };
  }
}
