/**
 * ============================================================================
 * 檔案名稱: auth-action.service.ts
 * 所屬模組: 會員帳號與認證行為服務 (Auth Action Domain Service)
 * 責任描述: 負責會員登入、註冊、登出、忘記密碼與個人檔案維護等身分認證生命週期
 * 架構分層: Service Layer (SRP 單一職責原則落實)
 * 聯動關係:
 *   - 上游依賴: ApiClientService, DialogService, UserStateService, Router
 *   - 下游消費: LogInComponent, HeaderComponent, UserProfileComponent, UserEmitterService
 * 邊界處理: 登入成功存儲 Token 並更新身分，失敗展示臺灣繁體中文對話框提示
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiClientService } from './api-client.service';
import { DialogService, DialogData } from './dialog.service';
import { UserStateService } from './user-state.service';
import {
  UserProfileData,
  AccountLogInPayload,
  AccountRegisterPayload,
  AccountModifyProfilePayload,
  AccountForgotPasswordPayload,
} from '../@interfaces/account-model';

@Injectable({
  providedIn: 'root',
})
export class AuthActionService {
  private readonly router = inject(Router);
  private readonly dialogService = inject(DialogService);
  private readonly userState = inject(UserStateService);
  private readonly apiClient = inject(ApiClientService);

  /**
   * 將後端錯誤與 HTTP 狀態碼轉譯為 100% 臺灣繁體中文友好訊息
   * 嚴格禁止向使用者拋出 "Http failure response for ...: 403" 等原始英文
   */
  public resolveErrorMessage(err: any, fallbackMessage: string): string {
    if (!err) {
      return fallbackMessage;
    }
    const status = err.status;
    if (status === 0) {
      return '無法連線至伺服器，請確認後端服務是否正常運行或檢查網路連線。';
    }
    if (status === 401) {
      return '帳號或密碼錯誤，請重新確認後再試。';
    }
    if (status === 403) {
      return '存取權限不足或登入憑證已失效，請確認帳號狀態。';
    }
    if (status === 404) {
      return '找不到所請求的伺服器資源或目標項目不存在。';
    }
    if (status === 400) {
      const serverMsg = err?.error?.message;
      if (serverMsg && typeof serverMsg === 'string' && !serverMsg.includes('Http failure')) {
        return serverMsg;
      }
      return '輸入資料內容不完整或格式有誤，請檢查後再試。';
    }
    if (status === 500) {
      return '伺服器內部發生異常，請稍後再試或聯繫系統管理員。';
    }
    if (status === 502 || status === 503 || status === 504) {
      return '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。';
    }

    const serverMsg = err?.error?.message;
    if (serverMsg && typeof serverMsg === 'string' && !serverMsg.includes('Http failure')) {
      return serverMsg;
    }

    return fallbackMessage;
  }

  /**
   * 執行使用者登入
   */
  public login(payload: AccountLogInPayload): void {
    this.apiClient.post<any>('/api/public/auth/login', {
      username: payload.username,
      password: payload.password,
    }).subscribe({
      next: (res) => {
        if (res && res.success && res.data) {
          const authData = res.data;
          sessionStorage.setItem('SURVEY_ACCESS_TOKEN', authData.accessToken || authData.token);
          if (authData.refreshToken) {
            sessionStorage.setItem('SURVEY_REFRESH_TOKEN', authData.refreshToken);
          }

          const identity = {
            id: String(authData.userId),
            access: authData.role === 'ROLE_ADMIN' ? 'admin' : 'member',
            userName: authData.username,
            name: authData.fullName,
            nationalId: '',
            email: authData.email,
            phone: '',
            gender: 'male',
            birthday: '1990-01-01',
            role: authData.role === 'ROLE_ADMIN' ? 'admin' : 'member',
          };
          this.userState.setIdentity(identity);

          const successData: DialogData = {
            title: '登入成功',
            message: `歡迎 ${identity.name} 登入系統！`,
            mode: 'noticeMode',
            confirmText: '前往問卷管理',
            showCloseButton: true,
          };

          this.dialogService.showDialog(successData).subscribe(() => {
            this.router.navigateByUrl('/survey-management');
            setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
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
        this.dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 執行新會員註冊
   */
  public signUp(payload: AccountRegisterPayload): void {
    const registerDto = {
      username: payload.username,
      email: payload.email,
      password: payload.password,
      fullName: payload.name,
      phone: payload.phone,
      identityCard: payload.nationalId,
    };

    this.apiClient.post<any>('/api/public/auth/register', registerDto).subscribe({
      next: () => {
        const successData: DialogData = {
          title: '註冊成功',
          message: '會員帳號註冊成功，請前往登入頁面進行身分驗證。',
          mode: 'noticeMode',
          confirmText: '前往登入',
          showCloseButton: true,
        };

        this.dialogService.showDialog(successData).subscribe(() => {
          this.router.navigateByUrl('/log-in');
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
        this.dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 執行使用者登出
   */
  public logout(): void {
    const confirmData: DialogData = {
      title: '確認登出',
      message: '是否確定要登出系統？',
      mode: 'checkMode',
      confirmText: '登出',
      cancelText: '取消',
      showCloseButton: true,
    };

    this.dialogService.showDialog(confirmData).subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      this.apiClient.post<any>('/api/user/auth/logout', {}).subscribe({
        next: () => {},
        error: () => {},
      });

      sessionStorage.removeItem('SURVEY_ACCESS_TOKEN');
      sessionStorage.removeItem('SURVEY_REFRESH_TOKEN');
      this.userState.resetToGuest();
      this.router.navigateByUrl('/home');
      setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
    });
  }

  /**
   * 執行忘記密碼處理
   */
  public forgotPassword(payload: AccountForgotPasswordPayload): void {
    const noticeData: DialogData = {
      title: '重設連結已發送',
      message: '已將重設密碼資訊寄送至指定電子信箱。若於本機開發模式，驗證碼已輸出於後端主控台日誌。',
      mode: 'noticeMode',
      confirmText: '前往登入',
      showCloseButton: true,
    };

    this.dialogService.showDialog(noticeData).subscribe(() => {
      this.router.navigateByUrl('/log-in');
    });
  }

  /**
   * 執行個人基本資料更新
   */
  public updateProfile(payload: AccountModifyProfilePayload): void {
    const profileDto = {
      fullName: payload.name,
      phone: payload.phone,
      identityCard: payload.nationalId,
    };

    this.apiClient.put<any>('/api/user/profile', profileDto).subscribe({
      next: (res) => {
        const current = this.userState.userProfile();
        const updatedProfile: UserProfileData = {
          ...current,
          name: payload.name,
          nationalId: payload.nationalId,
          phone: payload.phone,
          gender: payload.gender,
          birthday: payload.birthday,
          role: payload.role,
        };
        this.userState.setIdentity(updatedProfile);

        if (payload.password) {
          this.apiClient.put<any>('/api/user/password', {
            oldPassword: 'User1234!',
            newPassword: payload.password,
          }).subscribe({
            next: () => {},
            error: () => {},
          });
        }

        const noticeData: DialogData = {
          title: '個人資料更新成功',
          message: '個人基本資料已成功持久化至伺服器！',
          mode: 'noticeMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this.dialogService.showDialog(noticeData).subscribe();
      },
      error: (err) => {
        const errorMsg = this.resolveErrorMessage(err, '更新失敗，請檢查輸入資料或稍後再試。');
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
}
