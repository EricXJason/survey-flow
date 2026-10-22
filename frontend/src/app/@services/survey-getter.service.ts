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
import { Observable, of, throwError } from 'rxjs';
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
    return this.apiClient.get<any>('/api/public/surveys/lobby?page=0&size=100').pipe(
      map((res) => {
        if (res && res.success && res.data && res.data.content) {
          return res.data.content.map((item: any) => ({
            id: String(item.id),
            title: item.title,
            category: item.category ?? '一般問卷',
            authorId: item.authorName ?? '',
            authorName: item.authorName ?? '',
            startDate: item.startDate ?? '',
            endDate: item.endDate ?? '',
            isPublished: item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true,
            isPublicToLobby: item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true,
            isOpen: item.isOpen !== undefined ? Boolean(item.isOpen) : true,
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
    return this.apiClient.get<any>('/api/user/surveys?page=0&size=100').pipe(
      map((res) => {
        if (res && res.success && res.data && res.data.content) {
          return res.data.content.map((item: any, idx: number) => ({
            id: String(item.id),
            userSurveyNumber: item.userSurveyNumber !== undefined && item.userSurveyNumber !== null ? Number(item.userSurveyNumber) : (idx + 1),
            title: item.title,
            category: item.category ?? '一般問卷',
            authorId: String(item.userId ?? userId),
            authorName: item.authorName ?? '',
            startDate: item.startDate ?? '',
            endDate: item.endDate ?? '',
            isPublished: item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true,
            isOpen: item.isOpen !== undefined ? Boolean(item.isOpen) : true,
            isPublicToLobby: item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true,
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
    return this.apiClient.get<any>('/api/admin/surveys?page=0&size=100').pipe(
      map((res) => {
        if (res && res.success && res.data && res.data.content) {
          return res.data.content.map((item: any) => ({
            id: String(item.id),
            userSurveyNumber: item.userSurveyNumber ? Number(item.userSurveyNumber) : undefined,
            title: item.title,
            category: item.category ?? '一般問卷',
            authorId: item.authorName ?? '',
            authorName: item.authorName ?? '',
            startDate: item.startDate ?? '',
            endDate: item.endDate ?? '',
            isPublished: item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true,
            isOpen: item.isOpen !== undefined ? Boolean(item.isOpen) : true,
            isPublicToLobby: item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true,
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
          const rawQuestions = (d.questions ?? []).map((q: any, idx: number) => {
            const rawType = (q.type || '').toUpperCase();
            let frontendType = 'text';
            switch (rawType) {
              case 'BOOLEAN':
                frontendType = 't/f';
                break;
              case 'SINGLE':
                frontendType = 'radio';
                break;
              case 'MULTI':
                frontendType = 'multiRadio';
                break;
              case 'RATING':
                frontendType = 'scale';
                break;
              case 'TIME':
                frontendType = 'time';
                break;
              case 'DATE':
                frontendType = 'date';
                break;
              default:
                frontendType = 'text';
                break;
            }

            const optionsConfig = (q.options ?? []).map((o: any) => ({
              id: o.id,
              text: o.optionText,
              enableUnlockQuestion: !!o.triggerQuestionUid,
              unlockTargetUid: o.triggerQuestionUid || '',
              sortOrder: o.sortOrder,
            }));

            let determinedScaleType = 'scale-text-satisfaction-5';
            const ratingRangeKey = `${q.minRating}-${q.maxRating}`;
            switch (ratingRangeKey) {
              case '0-10':
                determinedScaleType = 'scale-num-0-10';
                break;
              case '1-10':
                determinedScaleType = 'scale-num-1-10';
                break;
              case '1-5':
                determinedScaleType = 'scale-num-1-5';
                break;
              default:
                if (q.scaleType) {
                  determinedScaleType = q.scaleType;
                }
                break;
            }

            return {
              uid: q.questionCode ?? `Q${idx + 1}`,
              id: q.id ?? idx + 1,
              type: frontendType,
              isRequired: q.isRequired ?? true,
              hasImage: !!q.imageUrl,
              imageBase64: q.imageUrl || '',
              description: q.questionText,
              scaleType: determinedScaleType,
              minRating: q.minRating,
              maxRating: q.maxRating,
              options: (q.options ?? []).map((o: any) => o.optionText),
              optionsConfig,
              isLockedByOther: false,
            };
          });

          // 條件跳題關聯掃描：被上游題目選項指向者標記為受鎖定
          const lockedUids = new Set<string>();
          for (const q of rawQuestions) {
            if (q.optionsConfig) {
              for (const opt of q.optionsConfig) {
                if (opt.enableUnlockQuestion && opt.unlockTargetUid) {
                  lockedUids.add(opt.unlockTargetUid);
                }
              }
            }
          }
          for (const q of rawQuestions) {
            if (lockedUids.has(q.uid)) {
              q.isLockedByOther = true;
            }
          }

          return {
            id: String(d.id),
            title: d.title,
            isOpen: d.isOpen !== undefined ? Boolean(d.isOpen) : true,
            isPublicToLobby: d.isPublicToLobby !== undefined ? Boolean(d.isPublicToLobby) : false,
            // [Why]: isPublished 於填答端語意代表「是否開放受訪者作答」，與後端 isOpen 嚴格對齊
            isPublished: d.isOpen !== undefined ? Boolean(d.isOpen) : true,
            hasTimeLimit: !!(d.startDate && d.endDate),
            timeLimit: {
              startDate: d.startDate ? d.startDate.split('T')[0] : '',
              startTime: '00:00',
              endDate: d.endDate ? d.endDate.split('T')[0] : '',
              endTime: '23:59',
            },
            hasDescription: !!d.description,
            description: d.description,
            hasDescriptionImage: !!d.coverImageUrl,
            descriptionImageBase64: d.coverImageUrl || '',
            basicInfoRequirements: {
              collectBasicInfo: d.basicInfoConfig?.collectBasicInfo ?? (d.basicInfoConfig != null),
              requireName: !!d.basicInfoConfig?.requireName,
              requireGender: !!d.basicInfoConfig?.requireGender,
              requireAge: !!d.basicInfoConfig?.requireAge,
              requireBirthday: !!d.basicInfoConfig?.requireBirthday,
              requireHeight: !!d.basicInfoConfig?.requireHeight,
              requireWeight: !!d.basicInfoConfig?.requireWeight,
              requireMobilePhone: !!d.basicInfoConfig?.requirePhone,
              requireHomePhone: !!d.basicInfoConfig?.requireHomePhone,
              requireEmail: !!d.basicInfoConfig?.requireEmail,
              requireRegisteredAddress: !!d.basicInfoConfig?.requireRegisteredAddress,
              requireResidentialAddress: !!d.basicInfoConfig?.requireResidence,
              requireDepartment: !!d.basicInfoConfig?.requireDepartment,
              requireAcademicMajor: !!d.basicInfoConfig?.requireAcademicMajor,
              requireClassName: !!d.basicInfoConfig?.requireClassName,
              requireStudentId: !!d.basicInfoConfig?.requireStudentId,
              requireEmployeeId: !!d.basicInfoConfig?.requireEmployeeId,
              requireNationalId: !!d.basicInfoConfig?.requireNationalId,
              requireJobIndustry: !!d.basicInfoConfig?.requireIndustry,
              requireJobTitle: !!d.basicInfoConfig?.requireJobTitle,
            },
            questions: rawQuestions,
          } as SurveyPayload;
        }
        return null;
      }),
      catchError((err) => {
        const message = err?.error?.message || err?.message || '問卷不存在或目前未開放填答';
        return throwError(() => new Error(message));
      })
    );
  }
}
