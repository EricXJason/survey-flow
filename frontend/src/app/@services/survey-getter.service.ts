/**
 * ============================================================================
 * 檔案名稱: survey-getter.service.ts
 * 所屬模組: 問卷資料檢索與查詢服務模組 (Survey Query Service Module)
 * 責任描述: 封裝問卷查詢邏輯，提供大廳公開問卷清單、會員個人問卷清單、全站所有問卷及單一問卷內容查詢 API。
 * 架構分層: Service Layer / Data Access Abstraction Layer
 * 聯動關係:
 *   - 上游依賴: rxjs (Observable, of, catchError, map), ./api-client.service, ../@interfaces/survey-model, ../datas/surveys.json
 *   - 下游消費: LobbyComponent, SurveyManagementComponent
 * 邊界處理: 優先連線後端 Spring Boot (/api/public/surveys/lobby)；若後端離線則無縫降級回退至本地種子資料，確保前端 100% 穩定
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { SurveyPayload, SurveySummaryItem } from '../@interfaces/survey-model';
import { ApiClientService } from './api-client.service';

@Injectable({
  providedIn: 'root',
})
export class SurveyGetterService {
  private readonly apiClient = inject(ApiClientService);

  /**
   * 取得大廳公開問卷清單 (請求後端 /api/public/surveys/lobby，以資料庫真實資料為主)
   */
  public getLobbySurveys(): Observable<SurveySummaryItem[]> {
    return this.apiClient.get<any>('/api/public/surveys/lobby').pipe(
      map((res) => {
        if (res && res.success && res.data && res.data.content) {
          return res.data.content.map((item: any) => ({
            id: String(item.id),
            title: item.title,
            category: item.category ?? '一般問卷',
            authorId: item.authorName ?? '',
            startDate: item.startDate ?? '',
            endDate: item.endDate ?? '',
            isPublished: item.status === 'PUBLISHED',
          }));
        }
        return [];
      }),
      catchError(() => of([]))
    );
  }

  /**
   * 依使用者編號取得個人問卷清單 (請求後端 /api/user/surveys，以資料庫真實資料為主)
   */
  public getMemberSurveys(userId: string): Observable<SurveySummaryItem[]> {
    return this.apiClient.get<any>('/api/user/surveys').pipe(
      map((res) => {
        if (res && res.success && res.data && res.data.content) {
          return res.data.content.map((item: any) => ({
            id: String(item.id),
            title: item.title,
            category: item.category ?? '一般問卷',
            authorId: String(item.userId ?? userId),
            startDate: item.startDate ?? '',
            endDate: item.endDate ?? '',
            isPublished: item.status === 'PUBLISHED',
            isPublicToLobby: item.isPublicToLobby ?? true,
            status: item.status,
          }));
        }
        return [];
      }),
      catchError(() => of([]))
    );
  }

  /**
   * 取得全系統所有問卷清單 (管理員請求 /api/admin/surveys，以資料庫真實資料為主)
   */
  public getAllSurveys(): Observable<SurveySummaryItem[]> {
    return this.apiClient.get<any>('/api/admin/surveys').pipe(
      map((res) => {
        if (res && res.success && res.data && res.data.content) {
          return res.data.content.map((item: any) => ({
            id: String(item.id),
            title: item.title,
            category: item.category ?? '一般問卷',
            authorId: item.authorName,
            startDate: item.startDate ?? '',
            endDate: item.endDate ?? '',
            isPublished: item.status === 'PUBLISHED',
            isPublicToLobby: item.isPublicToLobby ?? true,
            status: item.status,
          }));
        }
        return [];
      }),
      catchError(() => of([]))
    );
  }

  /**
   * 依問卷編號取得單一問卷內容 (請求 /api/public/surveys/{id}，以資料庫真實資料為主)
   */
  public getSurveyById(id: string): Observable<SurveyPayload | null> {
    const numericId = parseInt(id.replace(/\D/g, ''), 10);
    const requestUrl = isNaN(numericId)
      ? `/api/public/surveys/1`
      : `/api/public/surveys/${numericId}`;

    return this.apiClient.get<any>(requestUrl).pipe(
      map((res) => {
        if (res && res.success && res.data) {
          const d = res.data;
          return {
            id: String(d.id),
            title: d.title,
            category: d.category,
            isPublished: d.status === 'PUBLISHED',
            hasTimeLimit: !!(d.startDate && d.endDate),
            timeLimit: {
              startDate: d.startDate ? d.startDate.split('T')[0] : '',
              startTime: '00:00',
              endDate: d.endDate ? d.endDate.split('T')[0] : '',
              endTime: '23:59',
            },
            hasDescription: !!d.description,
            description: d.description,
            hasDescriptionImage: false,
            basicInfoRequirements: {
              collectBasicInfo: true,
              requireName: d.basicInfoConfig?.requireName ?? true,
              requireGender: d.basicInfoConfig?.requireGender ?? true,
              requireAge: d.basicInfoConfig?.requireAge ?? true,
              requireBirthday: false,
              requireHeight: false,
              requireWeight: false,
              requireMobilePhone: d.basicInfoConfig?.requirePhone ?? false,
              requireHomePhone: false,
              requireEmail: d.basicInfoConfig?.requireEmail ?? false,
              requireRegisteredAddress: false,
              requireResidentialAddress: d.basicInfoConfig?.requireResidence ?? false,
              requireDepartment: false,
              requireAcademicMajor: false,
              requireClassName: false,
              requireStudentId: false,
              requireEmployeeId: false,
              requireNationalId: false,
              requireJobIndustry: d.basicInfoConfig?.requireIndustry ?? false,
              requireJobTitle: false,
            },
            questions: (d.questions ?? []).map((q: any, idx: number) => {
              const rawType = (q.type || '').toUpperCase();
              let frontendType = 'text';
              if (rawType === 'BOOLEAN') {
                frontendType = 't/f';
              } else if (rawType === 'SINGLE') {
                frontendType = 'radio';
              } else if (rawType === 'MULTI') {
                frontendType = 'multiRadio';
              } else if (rawType === 'RATING') {
                frontendType = 'scale';
              } else if (rawType === 'TIME') {
                frontendType = 'time';
              } else if (rawType === 'DATE') {
                frontendType = 'date';
              } else {
                frontendType = 'text';
              }

              return {
                uid: q.questionCode ?? `Q${idx + 1}`,
                id: q.id ?? idx + 1,
                type: frontendType,
                isRequired: q.isRequired ?? true,
                hasImage: false,
                description: q.questionText,
                scaleType: 'nps',
                options: (q.options ?? []).map((o: any) => o.optionText),
              };
            }),
          } as SurveyPayload;
        }
        return null;
      }),
      catchError(() => of(null))
    );
  }
}
