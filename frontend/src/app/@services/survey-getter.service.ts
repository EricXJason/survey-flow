/**
 * ============================================================================
 * 檔案名稱: survey-getter.service.ts
 * 所屬模組: 問卷資料檢索與查詢服務模組 (Survey Query Service Module)
 * 責任描述: 封裝問卷查詢邏輯，提供大廳公開問卷清單、會員個人問卷清單、全站所有問卷，以及依問卷 ID 或分享代碼查詢單一問卷內容之 API。
 * 架構分層: Service Layer / Data Access Abstraction Layer
 * 聯動關係:
 *   - 上游依賴: rxjs (Observable, of, throwError, catchError, map), ./api-client.service, ../@interfaces/survey-model
 *   - 下游消費: LobbyComponent, SurveyManagementComponent
 * 邊界處理: 清單查詢失敗時回傳空陣列 (畫面顯示無資料 / 伺服器離線提示)，不以任何本地假資料替代
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
          return res.data.content.map((item: any) => this.toSummaryItem(item));
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
          return res.data.content.map((item: any, idx: number) =>
            this.toSummaryItem(item, { fallbackNumber: idx + 1, fallbackAuthorId: userId }),
          );
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
          return res.data.content.map((item: any) => this.toSummaryItem(item));
        }
        return [];
      }),
      catchError(() => of([]))
    );
  }

  /**
   * 依問卷編號取得單一問卷內容 (請求 /api/public/surveys/{id})；用於大廳進入填答與建立者預覽。
   * 未公開於大廳之問卷以此端點讀取會回 400「問卷不存在或已下架」，必須改用 getSurveyByShareToken。
   * @param id 問卷識別碼
   * @param isPreview 是否為預覽請求（預覽模式傳入 true 免受時限阻擋）
   * @returns 轉換後之填答結構；編號無法解析時為 null；後端拒絕時以繁中訊息之 Error 結束
   */
  public getSurveyById(id: string, isPreview: boolean = false): Observable<SurveyPayload | null> {
    const numericId = parseInt(id.replace(/\D/g, ''), 10);
    // [Why]: 無法解析之編號直接視為查無問卷，不可退回其他問卷內容
    if (isNaN(numericId)) {
      return of(null);
    }
    const queryParam = isPreview ? '?preview=true' : '';
    return this.fetchSurveyPayload(`/api/public/surveys/${numericId}${queryParam}`);
  }

  /**
   * 依分享代碼取得單一問卷內容 (請求 /api/public/surveys/shared/{shareToken})；受訪者由專屬填答連結進入時使用。
   * @param shareToken 連結 ?share= 參數中的分享代碼
   * @returns 轉換後之填答結構；代碼不存在或問卷未開放時以繁中訊息之 Error 結束
   */
  public getSurveyByShareToken(shareToken: string): Observable<SurveyPayload | null> {
    return this.fetchSurveyPayload(`/api/public/surveys/shared/${encodeURIComponent(shareToken)}`);
  }

  /**
   * 請求單一問卷端點，並把後端 SurveyResponse 轉成填答頁使用之 SurveyPayload
   * (題型代碼轉換、量表類型判定、條件跳題來源摘要、基本資料收集設定)
   * @param requestUrl 問卷端點 (依 ID 或依分享代碼)
   * @returns 轉換後之填答結構；回應格式不符時為 null；請求失敗時以繁中訊息之 Error 結束
   */
  private fetchSurveyPayload(requestUrl: string): Observable<SurveyPayload | null> {
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

          // 條件跳題關聯掃描：精準識別被上游題目指定選項解鎖之目標題目，並生成易讀的解鎖來源摘要文字
          for (let tIdx = 0; tIdx < rawQuestions.length; tIdx++) {
            const targetQ = rawQuestions[tIdx];
            const targetUid = String(targetQ.uid || '').trim();
            const targetId = String(targetQ.id ?? '').trim();
            const lockSources: string[] = [];

            for (let pIdx = 0; pIdx < rawQuestions.length; pIdx++) {
              const parentQ = rawQuestions[pIdx];
              if (!parentQ.optionsConfig) continue;

              for (let oIdx = 0; oIdx < parentQ.optionsConfig.length; oIdx++) {
                const opt = parentQ.optionsConfig[oIdx];
                if (opt.enableUnlockQuestion && opt.unlockTargetUid) {
                  const key = String(opt.unlockTargetUid).trim();
                  const isMatch =
                    key === targetUid ||
                    (targetId && key === targetId);

                  if (isMatch) {
                    const qNum = (pIdx + 1).toString().padStart(2, '0');
                    const optNum = (oIdx + 1).toString().padStart(2, '0');
                    const optText = opt.text ? `（${opt.text}）` : '';
                    lockSources.push(`【問題 ${qNum}】選項 ${optNum}${optText}`);
                  }
                }
              }
            }

            if (lockSources.length > 0) {
              targetQ.isLockedByOther = true;
              targetQ.lockedBySummary = lockSources.join(' 或 ');
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
              startTime: d.startDate && d.startDate.includes('T') ? d.startDate.split('T')[1].slice(0, 5) : '00:00',
              endDate: d.endDate ? d.endDate.split('T')[0] : '',
              endTime: d.endDate && d.endDate.includes('T') ? d.endDate.split('T')[1].slice(0, 5) : '23:59',
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
      // [Why]: 轉成只帶繁中訊息的 Error，呼叫端 (填答頁、預覽) 直接顯示 message，不會看到 Angular 原始英文錯誤
      catchError((err) => throwError(() => new Error(this.apiClient.resolveErrorMessage(err, '問卷不存在或目前未開放填答')))),
    );
  }

  /**
   * 將後端 SurveySummaryResponse 轉為前端清單項目 (大廳、個人、全站清單共用)
   * @param item 後端回應之單筆問卷摘要
   * @param options fallbackNumber：後端未提供個人序號時使用之序號；fallbackAuthorId：後端未提供 userId 時之建立者 ID
   * @returns 前端問卷清單項目
   */
  private toSummaryItem(
    item: any,
    options: { fallbackNumber?: number; fallbackAuthorId?: string } = {},
  ): SurveySummaryItem {
    const isPublicToLobby = item.isPublicToLobby !== undefined ? Boolean(item.isPublicToLobby) : true;
    const userSurveyNumber = item.userSurveyNumber !== undefined && item.userSurveyNumber !== null
      ? Number(item.userSurveyNumber)
      : options.fallbackNumber;
    return {
      id: String(item.id),
      userSurveyNumber,
      title: item.title,
      category: item.category ?? '一般問卷',
      authorId: item.userId !== undefined && item.userId !== null
        ? String(item.userId)
        : (options.fallbackAuthorId ?? item.authorName ?? ''),
      authorName: item.authorName ?? '',
      startDate: item.startDate ?? '',
      endDate: item.endDate ?? '',
      isPublished: isPublicToLobby,
      isPublicToLobby,
      isOpen: item.isOpen !== undefined ? Boolean(item.isOpen) : true,
      version: item.version ?? undefined,
      shareToken: item.shareToken ?? undefined,
    };
  }
}
