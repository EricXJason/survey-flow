/**
 * ============================================================================
 * 檔案名稱: survey-analytics.service.spec.ts
 * 測試對象: SurveyAnalyticsService (權限判斷、後端統計轉報表：KPI、趨勢累計、選項 / 量表 / 文字統計、受訪者輪廓、年齡層歸類)
 * 測試方式: ApiClientService 與 SurveyExportService 以 Spy 取代
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SurveyAnalyticsReport } from '../@interfaces/survey-analytics-model';
import { SurveySummaryItem } from '../@interfaces/survey-model';
import { ApiClientService, ApiResponse } from './api-client.service';
import { SurveyAnalyticsService } from './survey-analytics.service';
import { SurveyExportService } from './survey-export.service';

describe('SurveyAnalyticsService', () => {
  let service: SurveyAnalyticsService;
  let api: jasmine.SpyObj<ApiClientService>;
  let exporter: jasmine.SpyObj<SurveyExportService>;

  beforeEach(() => {
    api = jasmine.createSpyObj('ApiClientService', ['get']);
    exporter = jasmine.createSpyObj('SurveyExportService', ['exportReport']);
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: SurveyExportService, useValue: exporter },
      ],
    });
    service = TestBed.inject(SurveyAnalyticsService);
  });

  /**
   * 讓後端回傳指定 data 並取得轉換後的報表
   * @param data 後端 AnalyticsResponse
   * @returns 前端報表
   */
  function reportOf(data: Record<string, unknown>): SurveyAnalyticsReport {
    api.get.and.returnValue(of({ status: 200, success: true, message: '', data, timestamp: '' } as ApiResponse<unknown>));
    let report!: SurveyAnalyticsReport;
    service.getSurveyAnalytics('1').subscribe((r) => (report = r));
    return report;
  }

  describe('canAccessAnalytics', () => {
    const survey = { id: '1', authorId: '7' } as SurveySummaryItem;

    it('管理員可看全部；會員只能看自己的問卷；未登入不可看', () => {
      expect(service.canAccessAnalytics(survey, '99', true)).toBeTrue();
      expect(service.canAccessAnalytics(survey, '7', false)).toBeTrue();
      expect(service.canAccessAnalytics(survey, '8', false)).toBeFalse();
      expect(service.canAccessAnalytics(survey, '', false)).toBeFalse();
      expect(service.canAccessAnalytics(null as never, '7', false)).toBeFalse();
    });

    it('沒有 authorId 時改用 userId 比對', () => {
      expect(service.canAccessAnalytics({ userId: 7 } as never, '7', false)).toBeTrue();
      expect(service.canAccessAnalytics({} as never, '7', false)).toBeFalse();
    });
  });

  describe('getSurveyAnalytics', () => {
    it('編號無法解析時直接回報錯誤，不發出請求', () => {
      let message = '';
      service.getSurveyAnalytics('abc').subscribe({ error: (e: Error) => (message = e.message) });

      expect(message).toBe('問卷編號格式錯誤');
      expect(api.get).not.toHaveBeenCalled();
    });

    it('回應不成功時拋出錯誤；請求失敗時錯誤原樣傳給呼叫端', () => {
      const errors: unknown[] = [];
      api.get.and.returnValue(of({ success: false } as ApiResponse<unknown>));
      service.getSurveyAnalytics('S-3').subscribe({ error: (e: Error) => errors.push(e.message) });
      api.get.and.returnValue(throwError(() => ({ status: 403 })));
      service.getSurveyAnalytics('3').subscribe({ error: (e) => errors.push(e) });

      expect(api.get).toHaveBeenCalledWith('/api/analytics/surveys/3');
      expect(errors).toEqual(['查無問卷數據報表或後端服務異常', { status: 403 }]);
    });

    it('KPI 與問卷資訊：平均作答秒數換成分鐘、趨勢計算累計、缺少欄位用預設值', () => {
      const report = reportOf({
        surveyId: 3, surveyTitle: '滿意度', authorUsername: 'owner', isPublicToLobby: false, startDate: '2026-01-01',
        kpi: { totalResponses: 4, completionRate: 80, averageDurationSeconds: 125, todayResponses: 2, netPromoterScore: 25 },
        trends: [{ date: '2026-01-01', count: 1 }, { date: '2026-01-02', count: 3 }],
        lastResponseAt: '2026-01-02T09:05:07',
      });

      expect(report).toEqual(jasmine.objectContaining({
        surveyId: '3', category: '綜合調查', authorName: 'owner', isPublished: false, startDate: '2026-01-01', endDate: undefined,
        totalResponses: 4, completionRate: 80, averageDurationMinutes: 2.1, last24hCount: 2, npsScore: 25,
        lastResponseAt: '2026/01/02 09:05:07', demographics: undefined, questionsAnalytics: [],
      }));
      expect(report.timeline.map((t) => t.cumulativeCount)).toEqual([1, 4]);
      expect(report.generatedAt).toMatch(/^\d{4}\/\d{2}\/\d{2} \d{2}:\d{2}:\d{2}$/);
    });

    it('最新回覆時間：非 ISO 格式原樣顯示；有作答但沒有時間顯示已回收作答；沒有作答顯示尚無回覆', () => {
      expect(reportOf({ lastResponseAt: '剛剛' }).lastResponseAt).toBe('剛剛');
      expect(reportOf({ kpi: { totalResponses: 1 } }).lastResponseAt).toBe('已回收作答');
      const empty = reportOf({});
      expect(empty.lastResponseAt).toBe('尚無回覆');
      expect(empty.authorId).toBe('系統發起人');
      expect(empty.isPublished).toBeTrue();
      expect(empty.averageDurationMinutes).toBe(0);
    });

    it('選項題：優先使用 optionsList，沒有時改用 optionCounts；跳過數與作答率以總回收數計算', () => {
      const report = reportOf({
        kpi: { totalResponses: 10 },
        questionStats: [
          { type: 'SINGLE', questionCode: 'Q1', questionText: '性別', totalAnswers: 8,
            optionsList: [{ optionId: 1, text: '男', count: 5, percentage: 62.5 }, { optionId: 2, text: '女' }] },
          { type: 'MULTI', totalAnswers: 10, optionCounts: { A: 3, B: 0 }, optionPercentages: { A: 30 } },
          { type: 'BOOLEAN', optionCounts: { 是: 2 } },
        ],
      });
      const [q1, q2, q3] = report.questionsAnalytics;

      expect(q1).toEqual(jasmine.objectContaining({
        questionUid: 'Q1', questionId: 1, description: '性別', type: 'radio', totalAnswerCount: 8, skippedCount: 2, responseRate: 80,
      }));
      expect(q1.optionsStats?.[1]).toEqual({ optionId: 2, text: '女', count: 0, percentage: 0 });
      expect(q2.type).toBe('checkbox');
      expect(q2.optionsStats).toEqual([{ text: 'A', count: 3, percentage: 30 }, { text: 'B', count: 0, percentage: 0 }]);
      expect(q3).toEqual(jasmine.objectContaining({ questionUid: 'Q3', description: '題目 3', totalAnswerCount: 10, skippedCount: 0 }));
      expect(q3.optionsStats?.[0].percentage).toBe(0);
    });

    it('量表題：補齊每個分數 (沒人選顯示 0)，最高 / 最低分取自實際有人選的分數', () => {
      const [question] = reportOf({
        kpi: { totalResponses: 4, netPromoterScore: 10 },
        questionStats: [{
          type: 'RATING', totalAnswers: 4, minRating: 1, maxRating: 5, averageRating: 3.5,
          medianRating: 4, modeRating: 4, standardDeviation: 1.1, npsScore: 50,
          ratingDistribution: { 2: 1, 4: 3, 5: 0 },
        }],
      }).questionsAnalytics;

      expect(question.scaleStats?.distribution.map((d) => d.count)).toEqual([0, 1, 0, 3, 0]);
      expect(question.scaleStats?.distribution[3].percentage).toBe(75);
      expect(question.scaleStats).toEqual(jasmine.objectContaining({
        averageScore: 3.5, medianScore: 4, modeScore: 4, highestScore: 4, lowestScore: 2, standardDeviation: 1.1, npsScore: 50,
      }));
    });

    it('量表題缺少統計值時的推算：平均 > 5 視為 10 分量表、中位數用平均、眾數用最高分、NPS 沿用問卷 NPS', () => {
      const [tenPoint, noAnswers] = reportOf({
        kpi: { netPromoterScore: 30 },
        questionStats: [
          { type: 'RATING', averageRating: 7.25, ratingDistribution: { 9: 2 } },
          { type: 'RATING' },
        ],
      }).questionsAnalytics;

      expect(tenPoint.scaleStats?.distribution.length).toBe(10);
      expect(tenPoint.scaleStats).toEqual(jasmine.objectContaining({ medianScore: 7.3, modeScore: 9, standardDeviation: 0, npsScore: 30 }));
      expect(tenPoint.scaleStats?.distribution[8].percentage).toBe(0);
      expect(noAnswers.scaleStats).toEqual(jasmine.objectContaining({ highestScore: 5, lowestScore: 1, averageScore: 0 }));
    });

    it('文字題：整理回覆摘錄與平均字數；受訪者名稱改成訪客；沒有 totalAnswers 時以回覆數為作答數', () => {
      const [text, date, empty] = reportOf({
        kpi: { totalResponses: 5 },
        questionStats: [
          { type: 'TEXT', questionId: 9, textFeedbacks: [
            '很好', { content: '服務親切', respondentName: '受訪者 #3', submittedAt: '2026-01-01' }, { respondentName: '填答者 #4' }, null,
          ] },
          { type: 'DATE', textFeedbacks: [] },
          { type: 'UNKNOWN', isConditionTriggered: true, triggerConditionDescription: '選是才出現' },
        ],
      }).questionsAnalytics;

      expect(text.textResponses?.map((r) => r.respondentName)).toEqual(['訪客 1', '訪客 3', '訪客 4', '訪客 4']);
      expect(text.textResponses?.[0]).toEqual(jasmine.objectContaining({ id: 'TR-9-1', content: '很好', submittedAt: '近期填答' }));
      expect(text.textStats).toEqual({ totalCount: 4, averageLength: 2 });
      expect(text.totalAnswerCount).toBe(4);
      expect(text.skippedCount).toBe(1);
      expect(date.type).toBe('text');
      expect(date.textResponses).toBeUndefined();
      expect(date.textStats).toEqual({ totalCount: 0, averageLength: 0 });
      expect(empty.isConditionTriggered).toBeTrue();
      expect(empty.triggerConditionDescription).toBe('選是才出現');
    });

    it('作答率：沒有總回收數時，有作答為 100%，沒作答為 0%', () => {
      const [answered, none] = reportOf({
        questionStats: [{ type: 'TEXT', totalAnswers: 2 }, { type: 'TEXT', totalAnswers: 0 }],
      }).questionsAnalytics;

      expect(answered.responseRate).toBe(100);
      expect(none.responseRate).toBe(0);
    });

    it('受訪者輪廓：性別支援中英文鍵、前五大產業依人數排序並計算佔比', () => {
      const report = reportOf({
        kpi: { totalResponses: 10 },
        demographics: {
          genderDistribution: { 男: 4, female: 5, OTHER: 1 },
          industryDistribution: { 資訊: 2, 金融: 5, 教育: 1, 醫療: 1, 製造: 1, 服務: 0 },
        },
      });

      expect(report.demographics?.genderDistribution).toEqual({ male: 4, female: 5, other: 1 });
      expect(report.demographics?.topJobIndustries[0]).toEqual({ industry: '金融', count: 5, percentage: 50 });
      expect(report.demographics?.topJobIndustries.length).toBe(5);
    });

    it('年齡層歸類：區間文字、單一年齡、英文鍵與無法辨識的鍵', () => {
      const report = reportOf({
        demographics: {
          ageGroupDistribution: {
            '18歲以下': 1, under18: 1, 16: 1,
            '18-24歲': 2, '20': 2,
            '25~34': 3, '未知': 3, '30': 'x',
            '35至44歲': 4, 40: 4,
            '45歲以上': 5, '55-64': 5, 70: 5,
          },
        },
      });

      expect(report.demographics?.ageDistribution).toEqual({
        under18: 3, age18to24: 4, age25to34: 6, age35to44: 8, age45plus: 15,
      });
    });

    it('受訪者輪廓全部沒有資料時不顯示該區塊；產業佔比在總回收數為 0 時為 0', () => {
      expect(reportOf({ demographics: { genderDistribution: {}, ageGroupDistribution: { '18-24歲': 0 } } }).demographics).toBeUndefined();
      expect(reportOf({ demographics: {} }).demographics).toBeUndefined();
      expect(reportOf({ demographics: { industryDistribution: { 資訊: 1 } } }).demographics?.topJobIndustries[0].percentage).toBe(0);
    });
  });

  it('exportReport 委派給 SurveyExportService', () => {
    const report = { surveyId: '1' } as SurveyAnalyticsReport;

    service.exportReport(report, 'csv');

    expect(exporter.exportReport).toHaveBeenCalledWith(report, 'csv');
  });
});
