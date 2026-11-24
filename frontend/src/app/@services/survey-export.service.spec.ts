/**
 * ============================================================================
 * 檔案名稱: survey-export.service.spec.ts
 * 測試對象: SurveyExportService (JSON / CSV / Excel / SPSS 匯出內容、BOM、CSV 跳脫與下載檔名)
 * 測試方式: 攔截 URL.createObjectURL 取得產生的檔案內容，並阻止真正下載
 * ============================================================================
 */

import { fakeAsync, tick } from '@angular/core/testing';
import { SurveyAnalyticsReport } from '../@interfaces/survey-analytics-model';
import { SurveyExportService } from './survey-export.service';

/** 測試用報表：含受訪者輪廓、選項題與量表題，題目文字含逗號與雙引號以驗證 CSV 跳脫 */
const REPORT = {
  surveyId: '3',
  surveyTitle: '滿意度, 2026',
  totalResponses: 3,
  completionRate: 90,
  averageDurationMinutes: 2.5,
  npsScore: null,
  demographics: {
    genderDistribution: { male: 1, female: 2, other: 0 },
    ageDistribution: { under18: 0, age18to24: 3, age25to34: 0, age35to44: 0, age45plus: 0 },
    topJobIndustries: [{ industry: '資訊', count: 2, percentage: 66.7 }],
  },
  questionsAnalytics: [
    { description: '你說"好"嗎', type: 'radio', totalAnswerCount: 3, skippedCount: 0, responseRate: 100,
      optionsStats: [{ text: '好', count: 3, percentage: 100 }] },
    { description: '評分', type: 'scale', totalAnswerCount: 2, responseRate: 66.7,
      scaleStats: { averageScore: 4, medianScore: 4, standardDeviation: 0.5 } },
  ],
} as unknown as SurveyAnalyticsReport;

describe('SurveyExportService', () => {
  let service: SurveyExportService;
  let downloads: { blob: Blob; filename: string }[];

  beforeEach(() => {
    service = new SurveyExportService();
    downloads = [];
    let lastBlob: Blob;
    spyOn(URL, 'createObjectURL').and.callFake((blob: Blob | MediaSource) => {
      lastBlob = blob as Blob;
      return 'blob:test';
    });
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      downloads.push({ blob: lastBlob, filename: this.download });
    });
  });

  /**
   * 讀出最後一次下載的檔案文字內容
   * @returns 檔案內容
   */
  const lastText = (): Promise<string> => downloads[downloads.length - 1].blob.text();

  /**
   * 判斷最後一次下載的檔案開頭是否為 UTF-8 BOM (EF BB BF)
   * [Why]: Blob.text() 解碼時會吃掉 BOM，必須直接檢查位元組
   * @returns 是否有 BOM
   */
  const lastHasBom = async (): Promise<boolean> => {
    const bytes = new Uint8Array(await downloads[downloads.length - 1].blob.arrayBuffer());
    return bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf;
  };

  it('JSON：完整報表內容，檔名含問卷編號', async () => {
    service.exportReport(REPORT, 'json');

    expect(downloads[0].filename).toMatch(/^SurveyFlow_Report_3_\d{8}_\d{4}\.json$/);
    expect(JSON.parse(await lastText()).surveyTitle).toBe('滿意度, 2026');
  });

  it('CSV：不加 BOM；含輪廓、選項與量表統計；含逗號或引號的欄位會被跳脫', async () => {
    service.exportReport(REPORT, 'csv');
    const text = await lastText();

    expect(downloads[0].filename).toMatch(/^SurveyFlow_CSV_3_.*\.csv$/);
    expect(await lastHasBom()).toBeFalse();
    expect(text).toContain('問卷名稱,"滿意度, 2026"');
    expect(text).toContain('淨推薦值 NPS,無');
    expect(text).toContain('女性,2,66.7%');
    expect(text).toContain('18 – 24 歲,3,100%');
    expect(text).toContain('第 1 名,資訊,2 人,66.7%');
    expect(text).toContain('題目 1,"你說""好""嗎",題型: radio');
    expect(text).toContain('作答人數,2,跳過人數,0,作答率,66.7%');
    expect(text).toContain('平均分,4,中位數,4,標準差,0.5');
    expect(text.split('\r\n').length).toBeGreaterThan(10);
  });

  it('Excel：加上 UTF-8 BOM (Excel 開啟中文才不會亂碼)', async () => {
    service.exportReport(REPORT, 'excel');

    expect(downloads[0].filename).toMatch(/^SurveyFlow_EXCEL_3_.*\.csv$/);
    expect(await lastHasBom()).toBeTrue();
  });

  it('SPSS：以變數名稱 Q01、Q02 列出題目，不含受訪者輪廓', async () => {
    service.exportReport(REPORT, 'spss');
    const text = await lastText();

    expect(downloads[0].filename).toMatch(/^SurveyFlow_SPSS_3_.*\.spss_compatible\.csv$/);
    expect(text).toContain('SPSS_VAR_NAME,QUESTION_TEXT');
    expect(text).toContain('Q02,評分,scale,2,0,66.7%');
    expect(text).not.toContain('受訪者背景輪廓');
  });

  it('未知格式以 Excel 匯出；沒有輪廓與產業資料時略過該區塊；總數為 0 時百分比為 0%', async () => {
    const report = {
      ...REPORT, totalResponses: 0, npsScore: 10,
      demographics: { ...REPORT.demographics!, topJobIndustries: [] },
    } as SurveyAnalyticsReport;
    service.exportReport(report, 'pdf' as never);
    const text = await lastText();

    expect(downloads[0].filename).toContain('EXCEL');
    expect(text).toContain('男性,1,0%');
    expect(text).toContain('淨推薦值 NPS,10');
    expect(text).not.toContain('前五大任職產業');

    service.exportReport({ ...REPORT, demographics: undefined } as SurveyAnalyticsReport, 'csv');
    expect(await lastText()).not.toContain('受訪者背景輪廓');
  });

  it('下載後 5 秒釋放 Blob URL', fakeAsync(() => {
    service.exportReport(REPORT, 'json');

    tick(5000);

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
  }));
});
