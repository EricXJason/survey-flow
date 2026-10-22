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
   * 執行報表檔案匯出
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
   * 匯出 JSON 格式完整數據報表
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
   * 觸發瀏覽器下載 Blob 物件
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
   * 產生時間戳格式字串
   */
  private getTimestampStr(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}`;
  }
}
