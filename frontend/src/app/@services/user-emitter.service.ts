/**
 * ============================================================================
 * 檔案名稱: user-emitter.service.ts
 * 所屬模組: 使用者操作與事件派發服務模組 (User Action Emitter Module)
 * 責任描述: 負責跨組件的使用者操作事件派發（登入、登出、註冊、問卷發布、作答送出等），結合 DialogService 彈出確認或回饋彈窗。
 * 架構分層: Service Layer / Action Coordination Layer
 * 聯動關係:
 *   - 上游依賴: ./dialog.service, ./user-state.service, ../@interfaces/account-model, ../@interfaces/survey-model
 *   - 下游消費: 各驗證與操作頁面 (LogIn, Register, SurveyDesign, SurveyRunner, Header, etc.)
 * 邊界處理: 模擬身分本機登入核對；localStorage 寫入防呆降級；對話框取消即刻中止後續操作
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

/**
 * 使用者事件廣播服務 (UserEmitterService)
 * 負責跨組件的使用者操作事件派發（登入、登出、註冊、問卷發布、作答送出等）與確認對話框調用。
 */
@Injectable({
  providedIn: 'root',
})
export class UserEmitterService {
  private readonly _router = inject(Router);
  private readonly _dialogService = inject(DialogService);
  private readonly _userState = inject(UserStateService);

  /**
   * 執行使用者登入
   */
  public emitLogin(payload: AccountLogInPayload): void {
    // 待後端就緒後替換為 POST /api/auth/login
    let targetProfile: UserProfileData | null = null;

    if (
      (payload.username === 'User46972A' &&
        payload.password === '@User46972') ||
      (payload.username === 'admin' && payload.password === 'Password123!')
    ) {
      targetProfile = {
        id: '11111',
        access: 'admin',
        userName: payload.username,
        name: '系統管理員',
        nationalId: 'B234567890',
        email: payload.username === 'admin' ? 'admin@example.com' : 'admin@survey.com',
        phone: '0987654321',
        gender: 'male',
        birthday: '1990-01-01',
        role: 'researcher',
      };
    } else if (
      (payload.username === 'User46972M' &&
        payload.password === '@User46972M') ||
      (payload.username === 'member01' && payload.password === 'Password123!')
    ) {
      targetProfile = {
        id: '00001',
        access: 'member',
        userName: payload.username,
        name: '測試會員',
        nationalId: 'A123456789',
        email: payload.username === 'member01' ? 'member01@example.com' : 'member@survey.com',
        phone: '0912345678',
        gender: 'male',
        birthday: '1995-06-15',
        role: 'employee',
      };
    } else {
      // 檢核本機已註冊使用者清單
      try {
        const storedUsers = localStorage.getItem('SURVEY_REGISTERED_USERS');
        if (storedUsers) {
          const userList: AccountRegisterPayload[] = JSON.parse(storedUsers);
          const found = userList.find(
            (u) => u.username === payload.username && u.password === payload.password,
          );
          if (found) {
            targetProfile = {
              id: 'REG_' + Date.now().toString().slice(-5),
              access: 'member',
              userName: found.username,
              name: found.name,
              nationalId: found.nationalId,
              email: found.email,
              phone: found.phone,
              gender: found.gender || 'male',
              birthday: found.birthday,
              role: found.role || 'employee',
            };
          }
        }
      } catch {
        // 忽略 JSON 解析異常
      }
    }

    if (!targetProfile) {
      const errorData: DialogData = {
        title: '登入失敗',
        message: '帳號或密碼錯誤，請重新確認。',
        mode: 'errorMode',
        confirmText: '確定',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

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

  /**
   * 執行使用者登出
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

    this._dialogService
      .showDialog(confirmData)
      .subscribe((confirmed: boolean) => {
        if (!confirmed) {
          return;
        }

        // 待後端就緒後替換為 POST /api/auth/logout
        this._userState.resetToGuest();
        this._router.navigateByUrl('/lobby');
      });
  }

  /**
   * 執行問卷刪除
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

      // 待後端就緒後替換為 DELETE /api/surveys/{surveyId}

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
    });
  }

  /**
   * 執行新會員註冊
   */
  public emitSignUp(payload: AccountRegisterPayload): void {
    // 待後端就緒後替換為 POST /api/auth/register
    try {
      const storedUsers = localStorage.getItem('SURVEY_REGISTERED_USERS');
      const userList: AccountRegisterPayload[] = storedUsers ? JSON.parse(storedUsers) : [];
      // 覆寫既有使用者或追加新註冊者
      const filtered = userList.filter((user) => user.username !== payload.username);
      filtered.push(payload);
      localStorage.setItem('SURVEY_REGISTERED_USERS', JSON.stringify(filtered));
    } catch {
      // 忽略本機儲存異常
    }

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
  }

  /**
   * 執行忘記密碼處理
   */
  public emitForgotPassword(payload: AccountForgotPasswordPayload): void {
    // 待後端就緒後替換為 POST /api/auth/forgot-password
    const noticeData: DialogData = {
      title: '重設連結已發送',
      message: '已將重設密碼資訊寄送至指定電子信箱。',
      mode: 'noticeMode',
      confirmText: '前往登入',
      showCloseButton: true,
    };

    this._dialogService.showDialog(noticeData).subscribe(() => {
      this._router.navigateByUrl('/log-in');
    });
  }

  /**
   * 執行個人檔案更新
   */
  public emitUpdateProfile(payload: AccountModifyProfilePayload): void {
    // 待後端就緒後替換為 PUT /api/account/profile
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

    const noticeData: DialogData = {
      title: '更新成功',
      message: '個人基本資料已成功更新。',
      mode: 'noticeMode',
      confirmText: '確定',
      showCloseButton: true,
    };

    this._dialogService.showDialog(noticeData).subscribe();
  }

  /**
   * 執行問卷設計完成發布
   */
  public emitDesignSurvey(payload: SurveyPayload): void {
    const confirmData: DialogData = {
      title: '確認發布問卷',
      message: `是否確認發布問卷「${payload.title}」？發布後填答者即可開始填寫。`,
      mode: 'checkMode',
      confirmText: '確認發布',
      cancelText: '繼續編輯',
      showCloseButton: true,
    };

    this._dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      // 待後端就緒後替換為 POST /api/surveys

      const successData: DialogData = {
        title: '發布成功',
        message: '問卷已成功發布！',
        mode: 'noticeMode',
        confirmText: '前往問卷大廳',
        showCloseButton: true,
      };

      this._dialogService.showDialog(successData).subscribe(() => {
        this._router.navigateByUrl('/lobby');
      });
    });
  }

  /**
   * 執行受訪者問卷作答送出
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

      // 待後端就緒後替換為 POST /api/submissions

      const completeData: DialogData = {
        title: '填答成功',
        message: '感謝您的耐心填答！',
        mode: 'noticeMode',
        confirmText: '返回首頁',
        showCloseButton: true,
      };

      this._dialogService.showDialog(completeData).subscribe(() => {
        this._router.navigateByUrl('/home');
      });
    });
  }
}
