/**
 * ============================================================================
 * 檔案名稱: survey-action.service.ts
 * 所屬模組: 問卷生命週期與填答操作服務 (Survey Action Domain Service)
 * 責任描述: 負責問卷建立、發布、刪除、開放狀態開關、填答提交等問卷領域核心操作
 * 架構分層: Service Layer (SRP 單一職責原則落實)
 * 聯動關係:
 *   - 上游依賴: ApiClientService, DialogService, Router
 *   - 下游消費: SurveyDesignComponent, SurveyManagementComponent, SurveyRunnerComponent, UserEmitterService
 * 邊界處理: 提供二次確認防手滑對話框、錯誤平滑提示、填答送出平滑導向大廳或管理中心
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiClientService } from './api-client.service';
import { DialogService, DialogData } from './dialog.service';
import {
  SurveyPayload,
  SurveySubmissionPayload,
  SurveySettingsPayload,
} from '../@interfaces/survey-model';

/** 前端題型 → 後端題型 (含別名；名稱含 multi 者另行判斷為複選，未列出者視為文字題) */
const BACKEND_QUESTION_TYPE: Record<string, string> = {
  't/f': 'BOOLEAN',
  boolean: 'BOOLEAN',
  radio: 'SINGLE',
  single: 'SINGLE',
  scale: 'RATING',
  rating: 'RATING',
  time: 'TIME',
  date: 'DATE',
};

/** 量表代碼 → 評分範圍 [最小值, 最大值] (未列出者沿用題目自帶範圍，再不然為 1~5) */
const RATING_RANGE_BY_SCALE: Record<string, [number, number]> = {
  'scale-num-0-10': [0, 10],
  nps: [0, 10],
  'scale-num-1-10': [1, 10],
  'scale-text-satisfaction-4': [1, 4],
};

/** 文字量表答案 → 1~5 分 (量表答案可能以文字形式傳入，例如「非常滿意」) */
const TEXT_SCALE_SCORE: Record<string, number> = {
  '非常不滿意': 1, '極不滿意': 1, '非常不同意': 1, '極不同意': 1,
  '不滿意': 2, '不同意': 2, '滿意度低': 2,
  '普通': 3, '尚可': 3, '中立': 3, '無意見': 3,
  '滿意': 4, '同意': 4, '滿意度高': 4,
  '非常滿意': 5, '極滿意': 5, '非常同意': 5, '極同意': 5,
};

/**
 * 將前端題型轉為後端題型代碼 (saveSurvey 組裝請求時使用)
 * @param frontendType 前端題型 (如 radio、multiRadio、scale)
 * @returns 後端題型 (BOOLEAN / SINGLE / MULTI / RATING / TIME / DATE / TEXT)
 */
function toBackendQuestionType(frontendType: string | undefined): string {
  const type = (frontendType || '').toLowerCase();
  if (type.includes('multi')) {
    return 'MULTI';
  }
  return BACKEND_QUESTION_TYPE[type] ?? 'TEXT';
}

/**
 * 將年齡數字歸入後端統計使用之年齡層 (與分析頁的年齡層分組一致)
 * @param age 年齡
 * @returns 年齡層文字
 */
function ageToGroup(age: number): string {
  if (age < 18) return '18歲以下';
  if (age <= 24) return '18-24歲';
  if (age <= 34) return '25-34歲';
  if (age <= 44) return '35-44歲';
  return '45歲以上';
}

@Injectable({
  providedIn: 'root',
})
export class SurveyActionService {
  private readonly router = inject(Router);
  private readonly dialogService = inject(DialogService);
  private readonly apiClient = inject(ApiClientService);

  /**
   * 委託 ApiClientService 將後端錯誤與 HTTP 狀態碼轉譯為臺灣繁體中文訊息
   * @param err HTTP 錯誤物件
   * @param fallbackMessage 無法對應時顯示之預設訊息
   * @returns 繁中錯誤訊息
   */
  private resolveErrorMessage(err: any, fallbackMessage: string): string {
    return this.apiClient.resolveErrorMessage(err, fallbackMessage);
  }

  /**
   * 問卷設計頁按下儲存或發布時呼叫：先跳確認對話框，確認後把前端設計稿轉成後端 SurveyCreateRequest
   * (起訖時間補秒數、題型與量表範圍轉換、是非題自動產生「是 / 否」選項、跳題設定轉為 triggerQuestionUid)，
   * 再 POST /api/user/surveys；成功後提示並導回問卷管理頁，失敗顯示轉譯後之原因。
   * @param payload 問卷設計頁的設計稿 (isPublished=true 代表發布到大廳)
   */
  public saveSurvey(payload: SurveyPayload): void {
    const confirmData: DialogData = {
      title: payload.isPublished ? '確認發布問卷' : '確認保存問卷',
      message: payload.isPublished
        ? `確定要立即發布問卷「${payload.title}」至問卷大廳嗎？發布後受訪者即可公開填答。`
        : `確定要將問卷「${payload.title}」保存為草稿嗎？`,
      mode: 'checkMode',
      confirmText: payload.isPublished ? '立即發布' : '保存草稿',
      cancelText: '繼續編輯',
      showCloseButton: true,
    };

    this.dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      let startDate: string | null = null;
      let endDate: string | null = null;

      if (payload.hasTimeLimit && payload.timeLimit?.startDate) {
        const sTime = payload.timeLimit.startTime && payload.timeLimit.startTime.trim()
          ? payload.timeLimit.startTime.trim()
          : '00:00';
        startDate = `${payload.timeLimit.startDate}T${sTime.length === 5 ? sTime + ':00' : sTime}`;
      }
      if (payload.hasTimeLimit && payload.timeLimit?.endDate) {
        const eTime = payload.timeLimit.endTime && payload.timeLimit.endTime.trim()
          ? payload.timeLimit.endTime.trim()
          : '23:59';
        endDate = `${payload.timeLimit.endDate}T${eTime.length === 5 ? eTime + ':59' : eTime}`;
      }

      const requestBody = {
        title: payload.title,
        description: payload.description || '',
        category: payload.category || '綜合調查',
        coverImageUrl: payload.descriptionImageBase64 || '',
        isPublicToLobby: !!payload.isPublished,
        isOpen: true,
        startDate,
        endDate,
        basicInfoConfig: {
          collectBasicInfo: !!payload.basicInfoRequirements?.collectBasicInfo,
          requireName: !!payload.basicInfoRequirements?.requireName,
          requireGender: !!payload.basicInfoRequirements?.requireGender,
          requireAge: !!payload.basicInfoRequirements?.requireAge,
          requireBirthday: !!payload.basicInfoRequirements?.requireBirthday,
          requireNationalId: !!payload.basicInfoRequirements?.requireNationalId,
          requirePhone: !!payload.basicInfoRequirements?.requireMobilePhone,
          requireHomePhone: !!payload.basicInfoRequirements?.requireHomePhone,
          requireEmail: !!payload.basicInfoRequirements?.requireEmail,
          requireIndustry: !!payload.basicInfoRequirements?.requireJobIndustry,
          requireJobTitle: !!payload.basicInfoRequirements?.requireJobTitle,
          requireDepartment: !!payload.basicInfoRequirements?.requireDepartment,
          requireEmployeeId: !!payload.basicInfoRequirements?.requireEmployeeId,
          requireAcademicMajor: !!payload.basicInfoRequirements?.requireAcademicMajor,
          requireClassName: !!payload.basicInfoRequirements?.requireClassName,
          requireStudentId: !!payload.basicInfoRequirements?.requireStudentId,
          requireHeight: !!payload.basicInfoRequirements?.requireHeight,
          requireWeight: !!payload.basicInfoRequirements?.requireWeight,
          requireResidence: !!payload.basicInfoRequirements?.requireResidentialAddress,
          requireRegisteredAddress: !!payload.basicInfoRequirements?.requireRegisteredAddress,
        },
        questions: (payload.questions || []).map((q, idx) => {
          const qType = toBackendQuestionType(q.type);
          let minR = 1;
          let maxR = 5;
          if (qType === 'RATING') {
            const presetRange = RATING_RANGE_BY_SCALE[q.scaleType || ''];
            if (presetRange) {
              [minR, maxR] = presetRange;
            } else if (q.minRating != null && q.maxRating != null) {
              minR = q.minRating;
              maxR = q.maxRating;
            }
          }
          let builtOptions: Array<{ optionText: string; sortOrder: number; triggerQuestionUid: string | null }> = [];
          if (qType === 'BOOLEAN') {
            builtOptions = [
              { optionText: '是', sortOrder: 1, triggerQuestionUid: null },
              { optionText: '否', sortOrder: 2, triggerQuestionUid: null },
            ];
          } else if (q.optionsConfig && q.optionsConfig.length > 0) {
            builtOptions = q.optionsConfig.map((optItem, oIdx) => ({
              optionText: optItem.text,
              sortOrder: oIdx + 1,
              triggerQuestionUid: optItem.enableUnlockQuestion ? (optItem.unlockTargetUid || null) : null,
            }));
          } else if (q.options && q.options.length > 0) {
            builtOptions = q.options.map((optText: string, oIdx: number) => ({
              optionText: optText,
              sortOrder: oIdx + 1,
              triggerQuestionUid: null,
            }));
          }

          return {
            questionCode: q.uid || `Q${idx + 1}`,
            type: qType,
            questionText: q.description || `題目 ${idx + 1}`,
            imageUrl: q.imageBase64 || '',
            isRequired: !!q.isRequired,
            sortOrder: idx + 1,
            minRating: minR,
            maxRating: maxR,
            options: builtOptions,
          };
        }),
      };

      this.apiClient.post<any>('/api/user/surveys', requestBody).subscribe({
        next: () => {
          const successData: DialogData = {
            title: payload.isPublished ? '發布成功' : '儲存成功',
            message: payload.isPublished
              ? '問卷已成功發布！受訪者已可直接在大廳參與或透過連結填答。'
              : '問卷草稿已成功保存至您的專案清單！',
            mode: 'noticeMode',
            confirmText: '返回問卷管理',
            showCloseButton: true,
          };

          this.dialogService.showDialog(successData).subscribe(() => {
            this.router.navigateByUrl('/survey-management');
            setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
          });
        },
        error: (err) => {
          const errorMsg = this.resolveErrorMessage(err, '問卷儲存失敗，請確認登入狀態或稍後再試。');
          const errorData: DialogData = {
            title: '儲存失敗',
            message: errorMsg,
            mode: 'errorMode',
            confirmText: '確定',
            showCloseButton: true,
          };
          this.dialogService.showDialog(errorData).subscribe();
        },
      });
    });
  }

  /**
   * 問卷管理頁按下刪除時呼叫：二次確認後 DELETE /api/user/surveys/{id} (後端為軟刪除)，
   * 成功時提示並執行 onSuccess (由頁面重新整理清單)，失敗顯示轉譯後之原因。
   * @param surveyId 問卷識別碼 (可含非數字前綴，送出前只取數字)
   * @param surveyTitle 問卷標題，顯示在確認與成功訊息
   * @param onSuccess 刪除成功後之回呼
   */
  public deleteSurvey(surveyId: string, surveyTitle: string, onSuccess?: () => void): void {
    const confirmData: DialogData = {
      title: '確認刪除問卷',
      message: `確定要刪除問卷「${surveyTitle}」嗎？此操作無法復原。`,
      mode: 'checkMode',
      confirmText: '確認刪除',
      cancelText: '取消',
      showCloseButton: true,
    };

    this.dialogService.showDialog(confirmData).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      const numericId = parseInt(surveyId.replace(/\D/g, ''), 10) || surveyId;
      this.apiClient.delete<any>(`/api/user/surveys/${numericId}`).subscribe({
        next: () => {
          const successData: DialogData = {
            title: '刪除成功',
            message: `問卷「${surveyTitle}」已成功刪除。`,
            mode: 'noticeMode',
            confirmText: '確定',
            showCloseButton: true,
          };
          this.dialogService.showDialog(successData).subscribe();
          onSuccess?.();
        },
        error: (err) => {
          const errorMsg = this.resolveErrorMessage(err, '問卷刪除失敗，請稍後再試。');
          const errorData: DialogData = {
            title: '刪除失敗',
            message: errorMsg,
            mode: 'errorMode',
            confirmText: '確定',
            showCloseButton: true,
          };
          this.dialogService.showDialog(errorData).subscribe();
        },
      });
    });
  }

  /**
   * 整體覆蓋問卷開放設定 (PUT /api/user/surveys/{id}/settings)：settings 內含樂觀鎖版本號，
   * 他人已先修改時後端回 409，此處顯示「已被其他人修改」；成功時把新版本號交給呼叫端保存。
   * @param surveyId 問卷識別碼
   * @param settings 起訖時間、大廳公開、開放填答與版本號
   * @param onSuccessCallback 成功後之回呼，參數為後端回傳之新版本號
   */
  public updateSurveySettings(
    surveyId: string,
    settings: SurveySettingsPayload,
    onSuccessCallback?: (newVersion: number | undefined) => void,
  ): void {
    const numericId = parseInt(surveyId.replace(/\D/g, ''), 10);
    const endpoint = isNaN(numericId)
      ? `/api/user/surveys/${surveyId}/settings`
      : `/api/user/surveys/${numericId}/settings`;

    this.apiClient.put<any>(endpoint, settings).subscribe({
      // [Why]: 成功提示由呼叫端 (問卷管理頁) 統一顯示，此處只回傳新版本號，避免同時跳出兩個對話框
      next: (res) => onSuccessCallback?.(res?.data?.version),
      error: (err) => {
        const errorMsg = this.resolveErrorMessage(err, '更新問卷設定失敗，請稍後再試。');
        const errorData: DialogData = {
          title: '更新失敗',
          message: errorMsg,
          mode: 'errorMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this.dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 填答頁按下送出時呼叫：確認後把作答內容轉成後端 SubmissionRequest
   * (年齡換成年齡層、文字量表答案換成 1~5 分、選項編號轉數字、其他答案轉文字)，
   * 再 POST /api/public/submissions；成功後提示並導向問卷大廳，失敗 (如問卷已截止 400) 顯示原因。
   * @param payload 填答頁收集之作答內容與基本資料
   */
  public submitSurvey(payload: SurveySubmissionPayload): void {
    const confirmData: DialogData = {
      title: '確認送出',
      message: '問卷填答完成，是否確認送出？送出後將無法修改。',
      mode: 'checkMode',
      confirmText: '確認送出',
      cancelText: '繼續檢查',
      showCloseButton: true,
    };

    this.dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      const rawId = (payload as any).surveyId || (payload as any).id || 1;
      const numericSurveyId = typeof rawId === 'number'
        ? rawId
        : parseInt(String(rawId).replace(/\D/g, ''), 10) || 1;

      const submissionDto = {
        surveyId: numericSurveyId,
        shareToken: payload.shareToken ?? null,
        durationSeconds: (payload as any).durationSeconds || 60,
        basicInfo: payload.basicInfo ? {
          name: payload.basicInfo.name || null,
          gender: payload.basicInfo.gender || null,
          ageGroup: this.computeStandardAgeGroup(payload.basicInfo.age, payload.basicInfo['ageGroup']),
          birthday: payload.basicInfo.birthday || null,
          nationalId: payload.basicInfo.nationalId || null,
          phone: payload.basicInfo.mobilePhone || payload.basicInfo['phone'] || null,
          homePhone: payload.basicInfo.homePhone || null,
          email: payload.basicInfo.email || null,
          industry: payload.basicInfo.jobIndustry || payload.basicInfo['industry'] || null,
          jobTitle: payload.basicInfo.jobTitle || null,
          department: payload.basicInfo.department || null,
          employeeId: payload.basicInfo.employeeId || null,
          academicMajor: payload.basicInfo.academicMajor || null,
          className: payload.basicInfo.className || null,
          studentId: payload.basicInfo.studentId || null,
          height: payload.basicInfo.height != null && !isNaN(Number(payload.basicInfo.height)) ? Number(payload.basicInfo.height) : null,
          weight: payload.basicInfo.weight != null && !isNaN(Number(payload.basicInfo.weight)) ? Number(payload.basicInfo.weight) : null,
          residence: payload.basicInfo.residentialAddress || payload.basicInfo['residence'] || null,
          registeredAddress: payload.basicInfo.registeredAddress || null,
        } : null,
        answers: (payload.answers || []).map((ans, idx) => {
          const qId = ans.questionId || (idx + 1);
          const isScaleType = ans.type === 'scale';
          let numValue: number | null = null;

          if (isScaleType && ans.answer != null) {
            const rawAnsStr = String(ans.answer).trim();
            if (!isNaN(Number(rawAnsStr))) {
              numValue = Number(rawAnsStr);
            } else {
              numValue = TEXT_SCALE_SCORE[rawAnsStr] ?? null;
            }
          }

          let optId: number | null = null;
          const rawOptId = ans.selectedOptionId ?? (ans as any).optionId;
          if (rawOptId != null) {
            optId = typeof rawOptId === 'number' ? rawOptId : parseInt(String(rawOptId).replace(/\D/g, ''), 10) || null;
          }

          let optIds: number[] = [];
          const rawOptIds = ans.selectedOptionIds ?? (ans as any).optionIds;
          if (Array.isArray(rawOptIds)) {
            optIds = rawOptIds
              .map((id) => (typeof id === 'number' ? id : parseInt(String(id).replace(/\D/g, ''), 10)))
              .filter((id) => !isNaN(id) && id != null) as number[];
          }

          let textAns: string | null = null;
          if (!isScaleType && ans.answer != null) {
            if (typeof ans.answer === 'object') {
              textAns = JSON.stringify(ans.answer);
            } else {
              textAns = String(ans.answer).trim();
            }
          }

          return {
            questionId: typeof qId === 'number' ? qId : parseInt(String(qId).replace(/\D/g, ''), 10) || 1,
            selectedOptionId: optId,
            selectedOptionIds: optIds,
            optionId: optId,
            optionIds: optIds,
            textAnswer: textAns,
            ratingValue: numValue,
          };
        }),
      };

      this.apiClient.post<any>('/api/public/submissions', submissionDto).subscribe({
        next: () => {
          const completeData: DialogData = {
            title: '填答成功',
            message: '感謝您的耐心填答！作答結果已成功記錄。',
            mode: 'noticeMode',
            confirmText: '探索問卷大廳',
            showCloseButton: true,
          };

          this.dialogService.showDialog(completeData).subscribe(() => {
            this.router.navigateByUrl('/lobby');
            setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
          });
        },
        error: (err) => {
          const errorMsg = this.resolveErrorMessage(err, '送出失敗，請檢查網路連線或稍後再試。');
          const errorData: DialogData = {
            title: '提交失敗',
            message: errorMsg,
            mode: 'errorMode',
            confirmText: '確定',
            showCloseButton: true,
          };
          this.dialogService.showDialog(errorData).subscribe();
        },
      });
    });
  }

  /**
   * 決定送給後端的年齡層：優先用填寫的年齡數字換算；沒有年齡時沿用填答頁傳來的年齡層文字
   * (已是區間格式如「25-34歲」則原樣保留，只有單一數字則換算，沒有數字則原樣保留)
   * @param age 填寫的年齡 (可為字串或數字)
   * @param rawAgeGroup 填答頁傳來的年齡層文字
   * @returns 年齡層文字，兩者皆無時為 null
   */
  private computeStandardAgeGroup(age: any, rawAgeGroup: any): string | null {
    if (age != null && age !== '') {
      const num = Number(age);
      if (!isNaN(num)) {
        return ageToGroup(num);
      }
    }
    if (rawAgeGroup) {
      const s = String(rawAgeGroup).trim();
      const match = s.match(/\d+/);
      if (match) {
        const num = parseInt(match[0], 10);
        if (s.includes('-') || s.includes('~') || s.includes('至')) {
          return s;
        }
        return ageToGroup(num);
      }
      return s;
    }
    return null;
  }
}
