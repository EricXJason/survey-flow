/**
 * ============================================================================
 * 檔案名稱: survey-action.service.ts
 * 所屬模組: 問卷生命週期與填答操作服務 (Survey Action Domain Service)
 * 責任描述: 負責問卷建立、發布、刪除、開放狀態開關、填答提交等問卷領域核心操作
 * 架構分層: Service Layer (SRP 單一職責原則落實)
 * 聯動關係:
 *   - 上游依賴: ApiClientService, DialogService, UserStateService, Router
 *   - 下游消費: SurveyDesignComponent, SurveyManagementComponent, SurveyRunnerComponent, UserEmitterService
 * 邊界處理: 提供二次確認防手滑對話框、錯誤平滑提示、填答送出平滑導向大廳或管理中心
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiClientService } from './api-client.service';
import { DialogService, DialogData } from './dialog.service';
import { UserStateService } from './user-state.service';
import {
  SurveyPayload,
  SurveySubmissionPayload,
} from '../@interfaces/survey-model';

@Injectable({
  providedIn: 'root',
})
export class SurveyActionService {
  private readonly router = inject(Router);
  private readonly dialogService = inject(DialogService);
  private readonly userState = inject(UserStateService);
  private readonly apiClient = inject(ApiClientService);

  /**
   * 將後端錯誤與 HTTP 狀態碼轉譯為 100% 臺灣繁體中文友好訊息
   * 嚴格禁止向使用者拋出 "Http failure response for ...: 403" 等原始英文
   */
  private resolveErrorMessage(err: any, fallbackMessage: string): string {
    if (!err) {
      return fallbackMessage;
    }
    const status = err.status;
    if (status === 0) {
      return '無法連線至伺服器，請確認後端服務是否正常運行或檢查網路連線。';
    }
    if (status === 401) {
      return '尚未登入或會員認證已過期，請重新登入後再試。';
    }
    if (status === 403) {
      return '存取權限不足或登入憑證已失效，請確認帳號權限或重新登入。';
    }
    if (status === 404) {
      return '找不到所請求的伺服器資源或目標問卷不存在。';
    }
    if (status === 400) {
      const serverMsg = err?.error?.message;
      if (serverMsg && typeof serverMsg === 'string' && !serverMsg.includes('Http failure')) {
        return serverMsg;
      }
      return '請求資料內容不完整或格式有誤，請檢查後再試。';
    }
    if (status === 500) {
      return '伺服器內部發生異常，請稍後再試或聯繫系統管理員。';
    }
    if (status === 502 || status === 503 || status === 504) {
      return '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。';
    }

    // 若後端有明確回傳業務中文訊息且非英文 Http failure 字串
    const serverMsg = err?.error?.message;
    if (serverMsg && typeof serverMsg === 'string' && !serverMsg.includes('Http failure')) {
      return serverMsg;
    }

    return fallbackMessage;
  }

  /**
   * 執行問卷設計完成/儲存/發布
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

      const now = new Date();
      const startDate = payload.timeLimit?.startDate
        ? `${payload.timeLimit.startDate}T00:00:00`
        : now.toISOString().slice(0, 19);
      const endDate = payload.timeLimit?.endDate
        ? `${payload.timeLimit.endDate}T23:59:59`
        : new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 19);

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
          let qType = 'TEXT';
          const t = (q.type || '').toLowerCase();
          if (t === 't/f' || t === 'boolean') {
            qType = 'BOOLEAN';
          } else if (t === 'radio' || t === 'single') {
            qType = 'SINGLE';
          } else if (t.includes('multi')) {
            qType = 'MULTI';
          } else if (t === 'scale' || t === 'rating') {
            qType = 'RATING';
          } else if (t === 'time') {
            qType = 'TIME';
          } else if (t === 'date') {
            qType = 'DATE';
          } else {
            qType = 'TEXT';
          }

          let minR = 1;
          let maxR = 5;
          if (qType === 'RATING') {
            const scaleCode = q.scaleType || '';
            if (scaleCode === 'scale-num-0-10' || scaleCode === 'nps') {
              minR = 0;
              maxR = 10;
            } else if (scaleCode === 'scale-num-1-10') {
              minR = 1;
              maxR = 10;
            } else if (scaleCode === 'scale-text-satisfaction-4') {
              minR = 1;
              maxR = 4;
            } else if (q.minRating !== undefined && q.maxRating !== undefined && q.minRating !== null && q.maxRating !== null) {
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
   * 執行問卷刪除
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
   * 切換問卷發布與大廳公開狀態
   */
  public togglePublish(surveyId: string, currentPublishedState: boolean, onSuccess?: () => void): void {
    const nextPublishedState = !currentPublishedState;
    const actionText = nextPublishedState ? '發布至問卷大廳' : '取消公開並轉為私有';

    const confirmData: DialogData = {
      title: `確認${actionText}`,
      message: nextPublishedState
        ? '發布後該問卷將立即公開呈現於大廳廣場，受訪者免登入即可瀏覽並作答。是否確認發布？'
        : '取消公開後該問卷將從大廳下架，僅能透過專屬連結進入作答。是否確認轉為私有？',
      mode: 'checkMode',
      confirmText: `確認${nextPublishedState ? '發布' : '轉私有'}`,
      cancelText: '取消',
      showCloseButton: true,
    };

    this.dialogService.showDialog(confirmData).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      const numericId = parseInt(surveyId.replace(/\D/g, ''), 10) || surveyId;
      this.apiClient.put<any>(`/api/user/surveys/${numericId}/settings`, {
        isPublicToLobby: nextPublishedState,
      }).subscribe({
        next: () => {
          const successData: DialogData = {
            title: `${actionText}成功`,
            message: `問卷已成功${actionText}！`,
            mode: 'noticeMode',
            confirmText: '確定',
            showCloseButton: true,
          };
          this.dialogService.showDialog(successData).subscribe();
          onSuccess?.();
        },
        error: (err) => {
          const errorMsg = this.resolveErrorMessage(err, `${actionText}失敗，請稍後再試。`);
          const errorData: DialogData = {
            title: '設定失敗',
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
   * 更新問卷開放狀態與填答時限設定
   */
  public updateSurveySettings(
    surveyId: string,
    settings: {
      startDate?: string | null;
      endDate?: string | null;
      isPublicToLobby: boolean;
      isOpen: boolean;
    },
    onSuccessCallback?: () => void,
  ): void {
    const numericId = parseInt(surveyId.replace(/\D/g, ''), 10);
    const endpoint = isNaN(numericId)
      ? `/api/user/surveys/${surveyId}/settings`
      : `/api/user/surveys/${numericId}/settings`;

    this.apiClient.put<any>(endpoint, settings).subscribe({
      next: () => {
        const successData: DialogData = {
          title: '設定更新成功',
          message: '問卷開放狀態與時限已成功同步至伺服器！',
          mode: 'noticeMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this.dialogService.showDialog(successData).subscribe();
        onSuccessCallback?.();
      },
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
   * 執行受訪者問卷作答送出
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
        durationSeconds: (payload as any).durationSeconds || 60,
        basicInfo: payload.basicInfo ? {
          name: payload.basicInfo.name || null,
          gender: payload.basicInfo.gender || null,
          ageGroup: payload.basicInfo.age != null ? `${payload.basicInfo.age}歲` : (payload.basicInfo['ageGroup'] || null),
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
          height: payload.basicInfo.height != null ? Number(payload.basicInfo.height) : null,
          weight: payload.basicInfo.weight != null ? Number(payload.basicInfo.weight) : null,
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
              const textScaleMap: Record<string, number> = {
                '非常不滿意': 1, '極不滿意': 1, '非常不同意': 1, '極不同意': 1,
                '不滿意': 2, '不同意': 2, '滿意度低': 2,
                '普通': 3, '尚可': 3, '中立': 3, '無意見': 3,
                '滿意': 4, '同意': 4, '滿意度高': 4,
                '非常滿意': 5, '極滿意': 5, '非常同意': 5, '極同意': 5,
              };
              numValue = textScaleMap[rawAnsStr] ?? null;
            }
          }

          let optId: number | null = null;
          const rawOptId = (ans as any).optionId;
          if (rawOptId != null) {
            optId = typeof rawOptId === 'number' ? rawOptId : parseInt(String(rawOptId).replace(/\D/g, ''), 10) || null;
          }

          return {
            questionId: typeof qId === 'number' ? qId : parseInt(String(qId).replace(/\D/g, ''), 10) || 1,
            optionId: optId,
            textAnswer: !isScaleType && ans.answer != null ? (typeof ans.answer === 'object' ? JSON.stringify(ans.answer) : String(ans.answer)) : null,
            ratingValue: numValue,
          };
        }),
      };

      this.apiClient.post<any>('/api/public/submissions', submissionDto).subscribe({
        next: () => {
          const isMember = this.userState.isLoggedIn();
          const targetUrl = isMember ? '/survey-management' : '/lobby';
          const completeData: DialogData = {
            title: '填答成功',
            message: '感謝您的耐心填答！作答結果已成功記錄。',
            mode: 'noticeMode',
            confirmText: isMember ? '返回問卷管理' : '探索問卷大廳',
            showCloseButton: true,
          };

          this.dialogService.showDialog(completeData).subscribe(() => {
            this.router.navigateByUrl(targetUrl);
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
}
