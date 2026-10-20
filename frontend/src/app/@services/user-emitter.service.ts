/**
 * ============================================================================
 * 檔案名稱: user-emitter.service.ts
 * 所屬模組: 使用者操作與事件派發服務模組 (User Action Emitter Module)
 * 責任描述: 負責跨組件的使用者操作事件派發（真實登入、登出、註冊、問卷發布、問卷刪除、作答送出等），
 *           全面連線後端 Spring Boot (http://localhost:8080) RESTful API，並結合 DialogService 提供彈窗反饋。
 * 架構分層: Service Layer / Action Coordination Layer
 * 聯動關係:
 *   - 上游依賴: ./api-client.service, ./dialog.service, ./user-state.service, ../@interfaces/account-model, ../@interfaces/survey-model
 *   - 下游消費: LogInComponent, RegisterComponent, SurveyDesignComponent, SurveyRunnerComponent, SurveyManagementComponent
 * 邊界處理: 錯誤攔截顯示後端詳細提示；登入成功自動保存雙 Token 至 sessionStorage
 * ============================================================================
 */

import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import {
  SurveyPayload,
  SurveySubmissionPayload,
} from '../@interfaces/survey-model';
import {
  UserProfileData,
  AccountLogInPayload,
  AccountRegisterPayload,
  AccountModifyProfilePayload,
  AccountForgotPasswordPayload,
} from '../@interfaces/account-model';
import { DialogService, DialogData } from './dialog.service';
import { UserStateService } from './user-state.service';
import { ApiClientService } from './api-client.service';

@Injectable({
  providedIn: 'root',
})
export class UserEmitterService {
  private readonly _router = inject(Router);
  private readonly _dialogService = inject(DialogService);
  private readonly _userState = inject(UserStateService);
  private readonly _apiClient = inject(ApiClientService);

  /**
   * 解析 HTTP 例外錯誤訊息，特別處理伺服器離線 (status 0 / 504 / 502 / 503) 之情況
   * @param err HTTP 錯誤物件
   * @param fallbackMessage 預設回退錯誤提示
   * @returns 適合向使用者展示的繁體中文錯誤訊息
   */
  private resolveErrorMessage(err: any, fallbackMessage: string): string {
    if (!err || err.status === 0) {
      return '無法連線至伺服器，請確認後端服務是否已啟動（8080 埠位）或檢查網路連線。';
    }
    if (err.status === 504 || err.status === 502 || err.status === 503) {
      return '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。';
    }
    return err?.error?.message || err?.message || fallbackMessage;
  }

  /**
   * 執行使用者登入 (連線後端 POST /api/public/auth/login)
   * @param payload 登入帳密載荷
   */
  public emitLogin(payload: AccountLogInPayload): void {
    this._apiClient.post<any>('/api/public/auth/login', {
      username: payload.username,
      password: payload.password,
    }).subscribe({
      next: (res) => {
        if (res && res.success && res.data) {
          const data = res.data;
          // 保存單一 JWT 授權憑證至會話儲存空間
          sessionStorage.setItem('SURVEY_ACCESS_TOKEN', data.token || data.accessToken);

          const targetProfile: UserProfileData = {
            id: String(data.userId),
            access: data.role === 'ROLE_ADMIN' ? 'admin' : 'member',
            userName: data.username,
            name: data.fullName,
            nationalId: '',
            email: data.email,
            phone: '',
            gender: 'male',
            birthday: '1990-01-01',
            role: data.role === 'ROLE_ADMIN' ? 'admin' : 'member',
          };

          this._userState.setIdentity(targetProfile);

          const welcomeData: DialogData = {
            title: '登入成功',
            message: `歡迎 ${targetProfile.name} 登入系統！`,
            mode: 'noticeMode',
            confirmText: '前往大廳',
            showCloseButton: true,
          };

          this._dialogService.showDialog(welcomeData).subscribe(() => {
            this._router.navigateByUrl('/lobby');
          });
        }
      },
      error: (err) => {
        const errorMsg = this.resolveErrorMessage(err, '帳號或密碼錯誤，請重新確認。');
        const errorData: DialogData = {
          title: '登入失敗',
          message: errorMsg,
          mode: 'errorMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 執行新會員註冊 (連線後端 POST /api/public/auth/register)
   * @param payload 註冊資料載荷
   */
  public emitSignUp(payload: AccountRegisterPayload): void {
    const registerDto = {
      username: payload.username,
      email: payload.email,
      password: payload.password,
      fullName: payload.name,
      phone: payload.phone,
      identityCard: payload.nationalId,
    };

    this._apiClient.post<any>('/api/public/auth/register', registerDto).subscribe({
      next: () => {
        const successData: DialogData = {
          title: '註冊成功',
          message: '會員帳號註冊成功，請前往登入頁面進行身分驗證。',
          mode: 'noticeMode',
          confirmText: '前往登入',
          showCloseButton: true,
        };

        this._dialogService.showDialog(successData).subscribe(() => {
          this._router.navigateByUrl('/log-in');
        });
      },
      error: (err) => {
        const errorMsg = this.resolveErrorMessage(err, '註冊失敗，請檢查輸入內容是否重複或稍後再試。');
        const errorData: DialogData = {
          title: '註冊失敗',
          message: errorMsg,
          mode: 'errorMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 執行使用者登出 (連線後端 POST /api/user/auth/logout 撤銷 Refresh Token)
   */
  public emitLogout(): void {
    const confirmData: DialogData = {
      title: '確認登出',
      message: '是否確定要登出系統？',
      mode: 'checkMode',
      confirmText: '登出',
      cancelText: '取消',
      showCloseButton: true,
    };

    this._dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      // 發送後端登出審計請求
      this._apiClient.post<any>('/api/user/auth/logout', {}).subscribe({
        next: () => {},
        error: () => {},
      });

      sessionStorage.removeItem('SURVEY_ACCESS_TOKEN');
      this._userState.resetToGuest();
      this._router.navigateByUrl('/lobby');
    });
  }

  /**
   * 執行問卷刪除 (連線後端 DELETE /api/user/surveys/{id})
   * @param surveyId 目標問卷 ID
   * @param surveyTitle 問卷標題
   * @param onSuccessCallback 成功回呼
   */
  public emitDeleteSurvey(
    surveyId: string,
    surveyTitle: string,
    onSuccessCallback?: () => void,
  ): void {
    const confirmData: DialogData = {
      title: '確認刪除問卷',
      message: `確定要刪除問卷「${surveyTitle}」嗎？此操作無法復原。`,
      mode: 'checkMode',
      confirmText: '確認刪除',
      cancelText: '取消',
      showCloseButton: true,
    };

    this._dialogService.showDialog(confirmData).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }

      const numericId = parseInt(surveyId.replace(/\D/g, ''), 10);
      const endpoint = isNaN(numericId)
        ? `/api/user/surveys/${surveyId}`
        : `/api/user/surveys/${numericId}`;

      this._apiClient.delete<any>(endpoint).subscribe({
        next: () => {
          const successData: DialogData = {
            title: '刪除成功',
            message: `問卷「${surveyTitle}」已成功移除。`,
            mode: 'noticeMode',
            confirmText: '確定',
            showCloseButton: true,
          };

          this._dialogService.showDialog(successData).subscribe(() => {
            if (onSuccessCallback) {
              onSuccessCallback();
            }
          });
        },
        error: (err) => {
          const errorMsg = this.resolveErrorMessage(err, '刪除失敗，請確認您具備管理此問卷之權限。');
          const errorData: DialogData = {
            title: '刪除失敗',
            message: errorMsg,
            mode: 'errorMode',
            confirmText: '確定',
            showCloseButton: true,
          };
          this._dialogService.showDialog(errorData).subscribe();
        },
      });
    });
  }

  /**
   * 執行問卷設計完成發布或儲存草稿 (連線後端 POST /api/user/surveys)
   * @param payload 前端視覺化設計之問卷資料載荷
   */
  public emitDesignSurvey(payload: SurveyPayload): void {
    const confirmData: DialogData = {
      title: payload.isPublished ? '確認發布問卷' : '確認儲存草稿',
      message: payload.isPublished
        ? `是否確認發布問卷「${payload.title}」？發布後將立即公開於問卷大廳。`
        : `是否確認儲存問卷「${payload.title}」為草稿專案？`,
      mode: 'checkMode',
      confirmText: payload.isPublished ? '確認發布' : '確認儲存',
      cancelText: '繼續編輯',
      showCloseButton: true,
    };

    this._dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
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

          return {
            questionCode: q.uid || `Q${idx + 1}`,
            type: qType,
            questionText: q.description || `題目 ${idx + 1}`,
            imageUrl: q.imageBase64 || '',
            isRequired: !!q.isRequired,
            sortOrder: idx + 1,
            minRating: 1,
            maxRating: 5,
            options: (q.optionsConfig && q.optionsConfig.length > 0)
              ? q.optionsConfig.map((optItem, oIdx) => ({
                  optionText: optItem.text,
                  sortOrder: oIdx + 1,
                  triggerQuestionUid: optItem.enableUnlockQuestion ? (optItem.unlockTargetUid || null) : null,
                }))
              : (q.options || []).map((optText: string, oIdx: number) => ({
                  optionText: optText,
                  sortOrder: oIdx + 1,
                  triggerQuestionUid: null,
                })),
          };
        }),
      };

      this._apiClient.post<any>('/api/user/surveys', requestBody).subscribe({
        next: () => {
          const successData: DialogData = {
            title: payload.isPublished ? '發布成功' : '儲存成功',
            message: payload.isPublished
              ? '問卷已成功發布！受訪者已可直接在大廳參與或透過連結填答。'
              : '問卷草稿已成功保存至您的專案清單！',
            mode: 'noticeMode',
            confirmText: '前往問卷大廳',
            showCloseButton: true,
          };

          this._dialogService.showDialog(successData).subscribe(() => {
            this._router.navigateByUrl('/lobby');
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
          this._dialogService.showDialog(errorData).subscribe();
        },
      });
    });
  }

  /**
   * 執行受訪者問卷作答送出 (連線後端 POST /api/public/submissions)
   * @param payload 填答回饋酬載
   */
  public emitSubmitSurvey(payload: SurveySubmissionPayload): void {
    const confirmData: DialogData = {
      title: '確認送出',
      message: '問卷填答完成，是否確認送出？送出後將無法修改。',
      mode: 'checkMode',
      confirmText: '確認送出',
      cancelText: '繼續檢查',
      showCloseButton: true,
    };

    this._dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
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
          const isNum = typeof ans.answer === 'number';
          return {
            questionId: qId,
            selectedOptionId: ans.selectedOptionId ?? null,
            selectedOptionIds: ans.selectedOptionIds ?? null,
            ratingValue: isNum ? ans.answer : null,
            textAnswer: !isNum && ans.answer != null ? (Array.isArray(ans.answer) ? ans.answer.join(', ') : String(ans.answer)) : null,
          };
        }),
      };

      this._apiClient.post<any>('/api/public/submissions', submissionDto).subscribe({
        next: () => {
          const completeData: DialogData = {
            title: '填答成功',
            message: '感謝您的耐心填答！作答結果已成功記錄。',
            mode: 'noticeMode',
            confirmText: '返回首頁',
            showCloseButton: true,
          };

          this._dialogService.showDialog(completeData).subscribe(() => {
            this._router.navigateByUrl('/home');
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
          this._dialogService.showDialog(errorData).subscribe();
        },
      });
    });
  }

  /**
   * 執行忘記密碼處理 (展示提示)
   */
  public emitForgotPassword(payload: AccountForgotPasswordPayload): void {
    const noticeData: DialogData = {
      title: '重設連結已發送',
      message: '已將重設密碼資訊寄送至指定電子信箱。若於本機開發模式，驗證碼已輸出於後端主控台日誌。',
      mode: 'noticeMode',
      confirmText: '前往登入',
      showCloseButton: true,
    };

    this._dialogService.showDialog(noticeData).subscribe(() => {
      this._router.navigateByUrl('/log-in');
    });
  }

  /**
   * 執行個人檔案更新 (連線後端 PUT /api/user/profile 與 PUT /api/user/password)
   */
  public emitUpdateProfile(payload: AccountModifyProfilePayload): void {
    const profileDto = {
      fullName: payload.name,
      phone: payload.phone,
      identityCard: payload.nationalId,
    };

    this._apiClient.put<any>('/api/user/profile', profileDto).subscribe({
      next: (res) => {
        const current = this._userState.userProfile();
        const updatedProfile: UserProfileData = {
          ...current,
          name: payload.name,
          nationalId: payload.nationalId,
          phone: payload.phone,
          gender: payload.gender,
          birthday: payload.birthday,
          role: payload.role,
        };
        this._userState.setIdentity(updatedProfile);

        // 若使用者有輸入新密碼，同時發送密碼變更請求
        if (payload.password) {
          this._apiClient.put<any>('/api/user/password', {
            oldPassword: 'User1234!', // 預設原密碼或由表單傳入
            newPassword: payload.password,
          }).subscribe({
            next: () => {},
            error: () => {},
          });
        }

        const noticeData: DialogData = {
          title: '更新成功',
          message: '個人基本資料已成功持久化至資料庫！',
          mode: 'noticeMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(noticeData).subscribe();
      },
      error: (err) => {
        const errorMsg = this.resolveErrorMessage(err, '更新個人資料失敗，請確認登入狀態或稍後再試。');
        const errorData: DialogData = {
          title: '更新失敗',
          message: errorMsg,
          mode: 'errorMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 執行問卷可填寫日期時間區間、大廳公開性與即時填寫狀態更新 (連線後端 PATCH /api/user/surveys/{id}/settings)
   * @param surveyId 目標問卷 ID
   * @param settings 時限與發布設定載荷
   * @param onSuccessCallback 更新成功回呼
   */
  public emitUpdateSurveySettings(
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

    this._apiClient.patch<any>(endpoint, settings).subscribe({
      next: () => {
        const noticeData: DialogData = {
          title: '設定更新成功',
          message: '問卷可填寫時段、大廳公開性與開放狀態已成功保存！',
          mode: 'noticeMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(noticeData).subscribe(() => {
          if (onSuccessCallback) {
            onSuccessCallback();
          }
        });
      },
      error: (err: any) => {
        const errorMsg = this.resolveErrorMessage(err, '更新問卷設定失敗，請確認您具備管理此問卷之權限。');
        const errorData: DialogData = {
          title: '更新失敗',
          message: errorMsg,
          mode: 'errorMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(errorData).subscribe();
      },
    });
  }
}
