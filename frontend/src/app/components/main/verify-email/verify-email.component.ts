/**
 * ============================================================================
 * 檔案名稱: verify-email.component.ts
 * 所屬模組: 會員信箱認證與帳號開通模組 (Email Verification Module)
 * 責任描述: 負責處理會員信箱開通驗證。支援郵件專屬連結自動觸發開通，亦提供手動 6 碼驗證與重新發送開通信。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ApiClientService, UserStateService, DialogService, ActivatedRoute, Router
 *   - 下游消費: app.routes.ts 路由導航註冊調用
 * 邊界處理: 驗證碼時效逾期提示；開通成功自動儲存零信任雙 Token 並導向大廳
 * ============================================================================
 */

import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiClientService } from '../../../@services/api-client.service';
import { UserStateService } from '../../../@services/user-state.service';
import { DialogService } from '../../../@services/dialog.service';
import { UserProfileData } from '../../../@interfaces/account-model';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit, OnDestroy {
  private readonly _route = inject(ActivatedRoute);
  private readonly _router = inject(Router);
  private readonly _apiClient = inject(ApiClientService);
  private readonly _userState = inject(UserStateService);
  private readonly _dialogService = inject(DialogService);

  /** 目標驗證信箱 */
  protected targetEmail = '';

  /** 6 位數啟用碼 */
  protected verificationCode = '';

  /** 是否正在自動開通核驗中 */
  protected isVerifying = false;

  /** 是否已驗證成功開通 */
  protected isVerifiedSuccess = false;

  /** 開通失敗錯誤訊息 */
  protected errorMessage = '';

  /** 重新寄送倒數秒數 */
  protected resendCountdown = 0;

  /** 是否正在重發驗證信 */
  protected isResending = false;

  private _countdownTimer: any = null;

  public ngOnInit(): void {
    this._route.queryParams.subscribe((params) => {
      const email = params['email']?.trim();
      const code = params['code']?.trim();

      if (email) {
        this.targetEmail = email;
      }
      if (code) {
        this.verificationCode = code;
      }

      // 若網址同時具備 email 與 6 碼驗證碼 (從郵件連結點擊直達)，立即自動執行開通核驗
      if (email && code && code.length === 6) {
        this.executeVerification(email, code);
      }
    });
  }

  public ngOnDestroy(): void {
    if (this._countdownTimer) {
      clearInterval(this._countdownTimer);
    }
  }

  /**
   * 執行開通核驗 (呼叫 POST /api/public/auth/verify-email)
   * @param email 目標信箱
   * @param code 6 位數啟用碼
   */
  protected executeVerification(email: string, code: string): void {
    if (this.isVerifying) {
      return;
    }

    this.isVerifying = true;
    this.errorMessage = '';

    this._apiClient.post<any>('/api/public/auth/verify-email', { email, code }).subscribe({
      next: (res) => {
        this.isVerifying = false;
        this.isVerifiedSuccess = true;

        const data = res?.data;
        if (data?.token || data?.accessToken) {
          sessionStorage.setItem('SURVEY_ACCESS_TOKEN', data.accessToken || data.token);
          if (data.refreshToken) {
            sessionStorage.setItem('SURVEY_REFRESH_TOKEN', data.refreshToken);
          }

          const profile: UserProfileData = {
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
          this._userState.setIdentity(profile);
        }

        // 成功開通後 2 秒自動導向問卷大廳
        setTimeout(() => {
          this._router.navigateByUrl('/lobby');
        }, 2000);
      },
      error: (err) => {
        this.isVerifying = false;
        this.errorMessage = err?.error?.message || '開通驗證失敗，驗證碼可能已過期或不正確。';
      },
    });
  }

  /**
   * 手動點擊送出驗證碼
   */
  protected handleManualVerify(): void {
    const email = this.targetEmail.trim();
    const code = this.verificationCode.trim();

    if (!email) {
      this.errorMessage = '請輸入待驗證的電子信箱';
      return;
    }
    if (code.length !== 6) {
      this.errorMessage = '請輸入完整的 6 位數啟用驗證碼';
      return;
    }

    this.executeVerification(email, code);
  }

  /**
   * 重新發送開通驗證信
   */
  protected handleResendVerification(): void {
    const email = this.targetEmail.trim();
    if (!email) {
      this._dialogService.showDialog({
        title: '請輸入電子信箱',
        message: '請先在上方輸入框輸入欲補發之電子信箱。',
        mode: 'errorMode',
      }).subscribe();
      return;
    }

    if (this.resendCountdown > 0 || this.isResending) {
      return;
    }

    this.isResending = true;
    this._apiClient.post<any>('/api/public/auth/resend-verification', { email }).subscribe({
      next: () => {
        this.isResending = false;
        this.startResendCountdown();
        this._dialogService.showDialog({
          title: '開通信已寄出',
          message: `新的開通驗證信已重新發送至：\n${email}\n\n請前往信箱查收並點擊信中連結完成開通。`,
          mode: 'noticeMode',
          confirmText: '確定',
        }).subscribe();
      },
      error: (err) => {
        this.isResending = false;
        this._dialogService.showDialog({
          title: '重新發送失敗',
          message: err?.error?.message || '重新發送失敗，請確認該信箱是否已註冊或稍後再試。',
          mode: 'errorMode',
        }).subscribe();
      },
    });
  }

  /**
   * 啟動 60 秒倒數計時器
   */
  private startResendCountdown(): void {
    this.resendCountdown = 60;
    if (this._countdownTimer) {
      clearInterval(this._countdownTimer);
    }
    this._countdownTimer = setInterval(() => {
      this.resendCountdown--;
      if (this.resendCountdown <= 0) {
        clearInterval(this._countdownTimer);
        this._countdownTimer = null;
      }
    }, 1000);
  }

  /**
   * 導航回登入頁
   */
  protected goToLogIn(): void {
    this._router.navigateByUrl('/log-in');
  }

  /**
   * 立即前往問卷大廳
   */
  protected goToLobby(): void {
    this._router.navigateByUrl('/lobby');
  }
}
