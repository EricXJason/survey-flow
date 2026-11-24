/**
 * ============================================================================
 * 檔案名稱: auth-action.service.ts
 * 所屬模組: 會員帳號與認證行為服務 (資安 S 級：認證服務)
 * 責任描述: 登入、登出、個人資料更新與修改密碼：
 *   - 登入：POST /api/public/auth/login → UserStateService.applyAuthResponse 保存 Access Token 與身分
 *     (Refresh Token 由後端以 HttpOnly Cookie 下發，前端程式碼接觸不到)
 *   - 登出：POST /api/user/auth/logout 請後端吊銷 Token 並清除 Cookie，前端同時清除 sessionStorage 並回到訪客
 *   - 修改密碼：須帶入使用者輸入之目前密碼 (PUT /api/user/password)，失敗時顯示後端原因
 * 架構分層: Service Layer
 * 聯動關係:
 *   - 上游依賴: ApiClientService、DialogService、UserStateService、Router
 *   - 下游消費: UserEmitterService (LogInComponent、HeaderComponent、UserProfileComponent 經由它呼叫)
 * 邊界處理: 錯誤一律經 resolveErrorMessage 轉為繁中訊息；登出請求失敗仍會清除本機登入狀態
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
  AccountModifyProfilePayload,
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
   * 委託 ApiClientService 將後端錯誤與 HTTP 狀態碼轉譯為臺灣繁體中文訊息 (供元件顯示錯誤時共用)
   * @param err HTTP 錯誤物件
   * @param fallbackMessage 無法對應時顯示之預設訊息
   * @returns 繁中錯誤訊息
   */
  public resolveErrorMessage(err: any, fallbackMessage: string): string {
    return this.apiClient.resolveErrorMessage(err, fallbackMessage);
  }

  /**
   * 送出帳號密碼登入 (POST /api/public/auth/login)：
   * 成功時保存 Access Token 與登入身分，顯示歡迎對話框，關閉後導向問卷管理頁；
   * 失敗時 (帳密錯誤 401、鎖定 429、停權 400 等) 顯示後端原因；帳號未開通 (403 ACCOUNT_NOT_VERIFIED) 時關閉提示後導向開通頁並預填信箱。
   * @param payload 帳號與密碼
   */
  public login(payload: AccountLogInPayload): void {
    this.apiClient.post<any>('/api/public/auth/login', {
      username: payload.username,
      password: payload.password,
    }).subscribe({
      next: (res) => {
        if (res && res.success && res.data) {
          const identity = this.userState.applyAuthResponse(res.data);

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
        // [Why]: 帳號未開通時後端回 403 Problem Details (code=ACCOUNT_NOT_VERIFIED、email)，直接帶使用者去開通頁補發驗證信
        const unverifiedEmail = err?.error?.code === 'ACCOUNT_NOT_VERIFIED' ? (err.error.email ?? '') : null;
        const errorData: DialogData = {
          title: unverifiedEmail !== null ? '帳號尚未開通' : '登入失敗',
          message: errorMsg,
          mode: 'errorMode',
          confirmText: unverifiedEmail !== null ? '前往開通頁' : '確定',
          showCloseButton: true,
        };
        this.dialogService.showDialog(errorData).subscribe(() => {
          if (unverifiedEmail !== null) {
            this.router.navigate(['/verify-email'], { queryParams: { email: unverifiedEmail } });
          }
        });
      },
    });
  }

  /**
   * 詢問確認後登出：通知後端吊銷 Token 並清除 Refresh Cookie (POST /api/user/auth/logout)，
   * 不等待後端回應即清除本機登入狀態並回到首頁 (後端失敗時本機仍然登出)。
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

      // [Why]: Refresh Token 只應存在 HttpOnly Cookie；登出時一併清除 sessionStorage 中可能殘留的同名項目，避免外洩
      sessionStorage.removeItem('SURVEY_REFRESH_TOKEN');
      this.userState.resetToGuest();
      this.router.navigateByUrl('/home');
      setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
    });
  }

  /**
   * 更新個人資料 (PUT /api/user/profile)：成功後同步更新本機登入身分；
   * 表單有填新密碼時，接著呼叫 changePassword 變更密碼，由它顯示最終結果。
   * @param payload 個人資料表單內容 (含選填之目前密碼與新密碼)
   */
  public updateProfile(payload: AccountModifyProfilePayload): void {
    const profileDto = {
      fullName: payload.name,
      phone: payload.phone,
      identityCard: payload.nationalId,
    };

    this.apiClient.put<any>('/api/user/profile', profileDto).subscribe({
      next: () => {
        const current = this.userState.userProfile();
        const updatedProfile: UserProfileData = {
          ...current,
          name: payload.name,
          nationalId: payload.nationalId,
          phone: payload.phone,
        };
        this.userState.setIdentity(updatedProfile);

        if (payload.password) {
          this.changePassword(payload.currentPassword ?? '', payload.password);
          return;
        }
        this.showNotice('個人資料更新成功', '個人基本資料已成功更新！');
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

  /**
   * 變更登入密碼 (PUT /api/user/password)；目前密碼錯誤時顯示後端回傳之原因
   * @param currentPassword 使用者輸入之目前密碼
   * @param newPassword 新密碼
   */
  private changePassword(currentPassword: string, newPassword: string): void {
    this.apiClient.put<any>('/api/user/password', {
      oldPassword: currentPassword,
      newPassword,
    }).subscribe({
      next: () => this.showNotice('個人資料與密碼更新成功', '個人基本資料與登入密碼皆已成功更新！'),
      error: (err) => {
        const errorData: DialogData = {
          title: '密碼變更失敗',
          message: this.resolveErrorMessage(err, '個人資料已更新，但密碼變更失敗，請確認目前密碼是否正確。'),
          mode: 'errorMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this.dialogService.showDialog(errorData).subscribe();
      },
    });
  }

  /**
   * 顯示操作成功提示對話框
   * @param title 對話框標題
   * @param message 提示內容
   */
  private showNotice(title: string, message: string): void {
    const noticeData: DialogData = {
      title,
      message,
      mode: 'noticeMode',
      confirmText: '確定',
      showCloseButton: true,
    };
    this.dialogService.showDialog(noticeData).subscribe();
  }
}
