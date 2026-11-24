/**
 * ============================================================================
 * 檔案名稱: survey-export.service.ts
 * 所屬模組: 統計報表檔案匯出服務
 * 責任描述: 負責各類統計資料格式化 (CSV, Excel UTF-8 BOM, SPSS, JSON) 與瀏覽器檔案下載
 * 架構分層: Service Layer (SRP 單一職責原則落實)
 * 聯動關係:
 *   - 上游依賴: SurveyAnalyticsReport, ExportFormat
 *   - 下游消費: SurveyAnalyticsService, SurveyManagementComponent
 * 邊界處理: CSV 欄位含特殊字元雙引號轉義、UTF-8 BOM 防 Excel 亂碼、定時釋放 Blob URL 防記憶體洩漏
 * ============================================================================
 */

import { Injectable } from '@angular/core';
import {
  ExportFormat,
  SurveyAnalyticsReport,
} from '../@interfaces/survey-analytics-model';

@Injectable({
  providedIn: 'root',
})
export class SurveyExportService {
  /**
   * 分析頁按下匯出時呼叫：依格式分派 (json → JSON；csv → 無 BOM 之 CSV；excel / spss → 附 BOM 之 CSV 變體)
   * @param report 統計報表酬載
   * @param format 欲匯出之檔案格式
   */
  public exportReport(report: SurveyAnalyticsReport, format: ExportFormat): void {
    switch (format) {
      case 'json':
        this.exportJson(report);
        break;
      case 'csv':
        this.exportCsv(report, false);
        break;
      case 'excel':
        this.exportCsv(report, true, 'excel');
        break;
      case 'spss':
        this.exportCsv(report, true, 'spss');
        break;
      default:
        this.exportCsv(report, true, 'excel');
    }
  }

  /**
   * 把整份報表以縮排 JSON 下載 (檔名含問卷編號與時間戳)，供程式再處理或備份
   * @param report 統計報表酬載
   */
  public exportJson(report: SurveyAnalyticsReport): void {
    const jsonStr = JSON.stringify(report, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json;charset=utf-8;' });
    const filename = `SurveyFlow_Report_${report.surveyId}_${this.getTimestampStr()}.json`;
    this.downloadBlob(blob, filename);
  }

  /**
   * 匯出 CSV / Excel / SPSS 檔案
   * @param report 報表資料
   * @param withBom 是否附加 UTF-8 BOM（Excel 必備）
   * @param variant 格式變體
   */
  public exportCsv(
    report: SurveyAnalyticsReport,
    withBom = true,
    variant: 'csv' | 'excel' | 'spss' = 'csv',
  ): void {
    const rows: Array<Array<string | number>> = [];

    // 1. 報表主體基本資訊
    rows.push(['【問卷統計分析綜合報告】']);
    rows.push(['問卷編號', report.surveyId]);
    rows.push(['問卷名稱', report.surveyTitle]);
    rows.push(['匯出時間', new Date().toLocaleString('zh-TW')]);
    rows.push([]);

    // 2. 核心指標 KPI
    rows.push(['【核心統計指標 KPI】']);
    rows.push(['總填答量', report.totalResponses]);
    rows.push(['完成率 (%)', report.completionRate]);
    rows.push(['平均作答時間 (分鐘)', report.averageDurationMinutes]);
    rows.push(['淨推薦值 NPS', report.npsScore ?? '無']);
    rows.push([]);

    // 2.5 受訪者基本輪廓 Demographics
    if (report.demographics && variant !== 'spss') {
      const demo = report.demographics;
      rows.push(['【受訪者背景輪廓分佈】']);
      rows.push(['生理性別分佈']);
      rows.push(['男性', demo.genderDistribution.male, this.percentOf(demo.genderDistribution.male, report.totalResponses)]);
      rows.push(['女性', demo.genderDistribution.female, this.percentOf(demo.genderDistribution.female, report.totalResponses)]);
      rows.push(['其他', demo.genderDistribution.other, this.percentOf(demo.genderDistribution.other, report.totalResponses)]);
      rows.push([]);

      rows.push(['年齡層分佈']);
      rows.push(['18 歲以下', demo.ageDistribution.under18, this.percentOf(demo.ageDistribution.under18, report.totalResponses)]);
      rows.push(['18 – 24 歲', demo.ageDistribution.age18to24, this.percentOf(demo.ageDistribution.age18to24, report.totalResponses)]);
      rows.push(['25 – 34 歲', demo.ageDistribution.age25to34, this.percentOf(demo.ageDistribution.age25to34, report.totalResponses)]);
      rows.push(['35 – 44 歲', demo.ageDistribution.age35to44, this.percentOf(demo.ageDistribution.age35to44, report.totalResponses)]);
      rows.push(['45 歲以上', demo.ageDistribution.age45plus, this.percentOf(demo.ageDistribution.age45plus, report.totalResponses)]);
      rows.push([]);

      if (demo.topJobIndustries && demo.topJobIndustries.length > 0) {
        rows.push(['前五大任職產業分佈']);
        demo.topJobIndustries.forEach((job, idx) => {
          rows.push([`第 ${idx + 1} 名`, job.industry, `${job.count} 人`, `${job.percentage}%`]);
        });
        rows.push([]);
      }

    }

    // 3. 各題統計明細
    rows.push(['【題目統計分析明細】']);
    if (variant === 'spss') {
      rows.push(['SPSS_VAR_NAME', 'QUESTION_TEXT', 'QUESTION_TYPE', 'RESPONSE_COUNT', 'SKIPPED_COUNT', 'RESPONSE_RATE']);
      report.questionsAnalytics.forEach((q, idx) => {
        const varName = `Q${String(idx + 1).padStart(2, '0')}`;
        rows.push([
          varName,
          q.description,
          q.type,
          q.totalAnswerCount,
          q.skippedCount ?? 0,
          `${q.responseRate}%`,
        ]);
      });
      rows.push([]);
    } else {
      report.questionsAnalytics.forEach((q, idx) => {
        rows.push([`題目 ${idx + 1}`, q.description, `題型: ${q.type}`]);
        rows.push([
          '作答人數',
          q.totalAnswerCount,
          '跳過人數',
          q.skippedCount ?? 0,
          '作答率',
          `${q.responseRate}%`,
        ]);

        if (q.optionsStats && q.optionsStats.length > 0) {
          rows.push(['選項內容', '選擇次數', '佔比百分比 (%)']);
          q.optionsStats.forEach((opt) => {
            rows.push([opt.text, opt.count, `${opt.percentage}%`]);
          });
        }

        if (q.scaleStats) {
          rows.push([
            '平均分',
            q.scaleStats.averageScore,
            '中位數',
            q.scaleStats.medianScore,
            '標準差',
            q.scaleStats.standardDeviation,
          ]);
        }
        rows.push([]);
      });
    }

    // 4. 轉換為 CSV 字串
    const csvContent = rows
      .map((row) =>
        row
          .map((cell) => {
            const str = String(cell ?? '');
            if (str.includes(',') || str.includes('\n') || str.includes('"')) {
              return `"${str.replace(/"/g, '""')}"`;
            }
            return str;
          })
          .join(','),
      )
      .join('\r\n');

    const bom = withBom ? '\uFEFF' : '';
    const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8;' });
    const ext = variant === 'spss' ? 'spss_compatible.csv' : 'csv';
    const filename = `SurveyFlow_${variant.toUpperCase()}_${report.surveyId}_${this.getTimestampStr()}.${ext}`;
    this.downloadBlob(blob, filename);
  }

  /**
   * 建立暫時的 <a download> 連結並點擊，觸發瀏覽器下載；5 秒後釋放 Blob URL 避免記憶體洩漏
   * @param blob 檔案內容
   * @param filename 下載檔名
   */
  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }

  /**
   * 計算人數佔總填答數之百分比文字 (四捨五入到小數 1 位；總數為 0 時為 0%)
   * @param count 該分類人數
   * @param total 總填答數
   * @returns 例如 "33.3%"
   */
  private percentOf(count: number, total: number): string {
    const percentage = total > 0 ? Math.round((count / total) * 1000) / 10 : 0;
    return `${percentage}%`;
  }

  /**
   * 產生下載檔名使用之本地時間戳 (YYYYMMDD_HHmm)
   * @returns 時間戳字串
   */
  private getTimestampStr(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}`;
  }
}
