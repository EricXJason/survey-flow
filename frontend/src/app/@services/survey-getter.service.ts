/**
 * ============================================================================
 * 檔案名稱: survey-getter.service.ts
 * 所屬模組: 問卷資料檢索與查詢服務模組 (Survey Query Service Module)
 * 責任描述: 封裝問卷查詢邏輯，提供大廳公開問卷清單、會員個人問卷清單、全站所有問卷及單一問卷內容查詢 API。
 * 架構分層: Service Layer / Data Access Abstraction Layer
 * 聯動關係:
 *   - 上游依賴: rxjs (Observable, of), ../@interfaces/survey-model, ../datas/surveys.json
 *   - 下游消費: LobbyComponent, SurveyManagementComponent
 * 邊界處理: 查無指定 ID 問卷時優雅回退至預設問卷或 null，防止前端清單崩潰
 * ============================================================================
 */

import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { SurveyPayload, SurveySummaryItem } from '../@interfaces/survey-model';
import surveys from '../datas/surveys.json';

/**
 * 問卷資料檢索服務 (SurveyGetterService)
 * 提供大廳問卷、會員問卷及問卷明細的查詢能力。
 */
@Injectable({
  providedIn: 'root',
})
export class SurveyGetterService {
  /**
   * 取得大廳公開問卷清單
   */
  public getLobbySurveys(): Observable<SurveySummaryItem[]> {
    // 待後端就緒後替換為 GET /api/surveys?published=true
    const rawSurveys =
      (surveys as unknown as (SurveyPayload & { id?: string })[]) ?? [];

    const summaries: SurveySummaryItem[] = rawSurveys
      .filter((survey) => survey.isPublished)
      .map((survey, index) => ({
        id: survey.id ?? `survey_${index + 1}`,
        title: survey.title,
        category: survey.category ?? '一般問卷',
        authorId: survey.authorId,
        startDate: survey.timeLimit?.startDate ?? '',
        endDate: survey.timeLimit?.endDate ?? '',
        isPublished: survey.isPublished,
      }));

    return of(summaries);
  }

  /**
   * 依使用者編號取得個人問卷清單
   */
  public getMemberSurveys(userId: string): Observable<SurveySummaryItem[]> {
    // 待後端就緒後替換為 GET /api/surveys?authorId={userId}
    const rawSurveys =
      (surveys as unknown as (SurveyPayload & {
        id?: string;
        authorId?: string;
        userId?: string;
      })[]) ?? [];

    const memberSurveys = rawSurveys
      .filter((survey) => survey.authorId === userId || survey.userId === userId)
      .map((survey, index) => ({
        id: survey.id ?? `survey_${index + 1}`,
        title: survey.title,
        category: survey.category ?? '一般問卷',
        authorId: survey.authorId || userId,
        startDate: survey.timeLimit?.startDate ?? '',
        endDate: survey.timeLimit?.endDate ?? '',
        isPublished: survey.isPublished,
      }));

    return of(memberSurveys);
  }

  /**
   * 取得全系統所有問卷清單
   */
  public getAllSurveys(): Observable<SurveySummaryItem[]> {
    // 待後端就緒後替換為 GET /api/surveys/all
    const rawSurveys =
      (surveys as unknown as (SurveyPayload & { id?: string })[]) ?? [];

    const summaries: SurveySummaryItem[] = rawSurveys.map((survey, index) => ({
      id: survey.id ?? `survey_${index + 1}`,
      title: survey.title,
      category: survey.category ?? '一般問卷',
      authorId: survey.authorId,
      startDate: survey.timeLimit?.startDate ?? '',
      endDate: survey.timeLimit?.endDate ?? '',
      isPublished: survey.isPublished,
    }));

    return of(summaries);
  }

  /**
   * 依問卷編號取得單一問卷內容
   */
  public getSurveyById(id: string): Observable<SurveyPayload | null> {
    // 待後端就緒後替換為 GET /api/surveys/{id}
    const rawSurveys =
      (surveys as unknown as (SurveyPayload & { id?: string })[]) ?? [];
    const matched =
      rawSurveys.find((survey) => survey.id === id) ?? rawSurveys[0] ?? null;

    return of(matched);
  }
}
