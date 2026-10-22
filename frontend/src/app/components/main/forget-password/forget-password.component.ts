/**
 * ============================================================================
 * 檔案名稱: forget-password.component.ts
 * 所屬模組: 密碼重設與找回模組 (Password Recovery Module)
 * 責任描述: 負責忘記密碼申請，接收使用者電子郵件並發送 6 碼 OTP 密碼重設信件
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ApiClientService, DialogService, FormBuilder
 *   - 下游消費: app.routes.ts 路由導航調用, reset-password.component
 * 邊界處理: 驗證失敗彈窗防呆；成功發送後引導跳轉至重設新密碼頁面
 * ============================================================================
 */

import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiClientService } from '../../../@services/api-client.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';

/**
 * 忘記密碼元件 (ForgetPasswordComponent)
 * 提供使用者僅需輸入電子郵件即可發送 6 碼重設驗證信。
 */
@Component({
  selector: 'app-forget-password',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './forget-password.component.html',
  styleUrl: './forget-password.component.scss',
})
export class ForgetPasswordComponent {
  /** 路由導航服務 */
  private readonly _router = inject(Router);
  /** 表單建構器服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 網路請求客戶端服務 */
  private readonly _apiClient = inject(ApiClientService);
  /** 互動對話框服務 */
  private readonly _dialogService = inject(DialogService);

  /** 是否正在發送請求狀態 */
  protected isSubmitting = false;

  /**
   * 忘記密碼響應式表單群組 (僅需提供 email)
   */
  protected readonly formBuilderGroup = this._formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
  });

  /**
   * 驗證忘記密碼表單資料
   */
  protected checkForgotPasswordData(): void {
    this.formBuilderGroup.markAllAsTouched();

    if (this.formBuilderGroup.get('email')?.invalid) {
      const errorData: DialogData = {
        title: '資料驗證錯誤',
        message: '請輸入正確且格式合規的電子信箱後再送出。',
        mode: 'errorMode',
        confirmText: '了解',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

    this.sendForgotPasswordData();
  }

  /**
   * 發送忘記密碼請求資料至後端真實端點
   */
  protected sendForgotPasswordData(): void {
    const email = this.formBuilderGroup.get('email')?.value?.trim();
    if (!email) {
      return;
    }

    this.isSubmitting = true;

    this._apiClient.post<any>('/api/public/auth/forget-password', { email }).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        const noticeData: DialogData = {
          title: '驗證碼已寄出',
          message: `6 位數安全重設驗證碼已發送至您的信箱：\n${email}\n\n請查收郵件後前往重設密碼頁面輸入驗證碼。`,
          mode: 'noticeMode',
          confirmText: '立即前往重設密碼',
          showCloseButton: true,
        };

        this._dialogService.showDialog(noticeData).subscribe(() => {
          this._router.navigate(['/reset-password'], { queryParams: { email } });
        });
      },
      error: (err) => {
        this.isSubmitting = false;
        const errMsg = err?.error?.message || '發送失敗，請確認該電子信箱是否已註冊，或稍後再試。';
        this._dialogService.showDialog({
          title: '發送失敗',
          message: errMsg,
          mode: 'errorMode',
          confirmText: '好',
        }).subscribe();
      },
    });
  }

  /**
   * 導航回登入頁面
   */
  protected goToLogInPage(): void {
    this._router.navigateByUrl('/log-in');
  }
}
