/**
 * ============================================================================
 * 檔案名稱: question-type-count.pipe.ts
 * 所屬模組: 問卷統計數據管道模組 (Survey Analytics Pipe Module)
 * 責任描述: 計算問卷題型分析清單中特定題型的出現次數，供模板直接渲染題型結構摘要。
 * 架構分層: Presentation Layer / Pure Pipe
 * 聯動關係:
 *   - 上游依賴: ../@interfaces/survey-analytics-model
 *   - 下游消費: SurveyManagementComponent 模板
 * 邊界處理: 輸入陣列為 null/空時回傳 0；題型比對採完全字串匹配
 * ============================================================================
 */

import { Pipe, PipeTransform } from '@angular/core';
import { QuestionAnalyticsItem } from '../@interfaces/survey-analytics-model';

/**
 * QuestionTypeCountPipe：計算分析清單中特定題型的數量
 * 使用範例：{{ report.questionsAnalytics | questionTypeCount: 'radio' }}
 *
 * @param items 問卷題目統計分析清單
 * @param type 目標題型字串（'radio' | 'checkbox' | 'scale' | 'text' | 'boolean'）
 * @returns 符合指定題型的題目數量
 */
@Pipe({
  name: 'questionTypeCount',
  standalone: true,
  pure: true,
})
export class QuestionTypeCountPipe implements PipeTransform {
  public transform(items: QuestionAnalyticsItem[] | null, type: string): number {
    if (!items) return 0;
    return items.filter((q) => q.type === type).length;
  }
}
