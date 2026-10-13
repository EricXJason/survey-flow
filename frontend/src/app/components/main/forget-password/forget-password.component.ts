/**
 * ============================================================================
 * 檔案名稱: forget-password.component.ts
 * 所屬模組: 密碼重設與找回模組 (Password Recovery Module)
 * 責任描述: 負責忘記密碼身分核驗，接收使用者帳號與電子郵件並發送重設密碼連結信件通知。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/validation.service, ../../../@services/dialog.service, ../../../@interfaces/account-model
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 驗證失敗彈窗防呆；成功發送後 3 秒自動倒數或手動點擊返回登入頁
 * ============================================================================
 */

import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ValidationService } from '../../../@services/validation.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { AccountForgotPasswordPayload } from '../../../@interfaces/account-model';

/**
 * 忘記密碼元件 (ForgetPasswordComponent)
 * 提供使用者輸入帳號與電子郵件以發送密碼重設信件。
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
  /** 欄位格式驗證服務 */
  private readonly _validationService = inject(ValidationService);
  /** 互動對話框服務 */
  private readonly _dialogService = inject(DialogService);

  /**
   * 忘記密碼響應式表單群組
   * 包含 username (帳號格式驗證 6-12 碼) 與 email (電子郵件格式驗證)
   */
  protected readonly formBuilderGroup = this._formBuilder.group({
    username: [
      '',
      [Validators.required, this._validationService.usernameValidator(6, 12)],
    ],
    email: ['', [Validators.required, Validators.email]],
  });

  /**
   * 驗證忘記密碼表單資料
   * 觸發所有欄位的 touched 狀態並檢核格式，若有不合規之處則開啟錯誤提示彈窗；全數合規時調用 sendForgotPasswordData()
   * @returns void
   */
  protected checkForgotPasswordData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

    if (this.formBuilderGroup.get('username')?.invalid) {
      errors.push('• 使用者帳號未填寫或長度格式不符（須為 6 至 12 碼）');
    }
    if (this.formBuilderGroup.get('email')?.invalid) {
      errors.push('• 電子信箱未填寫或格式不正確');
    }

    if (errors.length > 0) {
      const errorData: DialogData = {
        title: '資料驗證錯誤',
        message: `請修正以下項目後再送出：\n\n${errors.join('\n')}`,
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
   * 發送忘記密碼請求資料
   * 取得表單填寫資訊，顯示重設信寄送成功通知彈窗，並於確認或倒數 3 秒後自動導向登入頁面
   * @returns void
   */
  protected sendForgotPasswordData(): void {
    const payload =
      this.formBuilderGroup.getRawValue() as AccountForgotPasswordPayload;

    // 待後端就緒後發送忘記密碼請求並顯示提示對話框
    const noticeData: DialogData = {
      title: '已寄送重設信',
      message:
        '重設密碼信件已發送至您的電子信箱，系統將於 3 秒後自動返回登入頁。',
      mode: 'noticeMode',
      confirmText: '立即前往',
      showCloseButton: true,
      autoCloseDuration: 3000,
    };

    this._dialogService.showDialog(noticeData).subscribe(() => {
      this._router.navigateByUrl('/log-in');
    });
  }

  /**
   * 導航回登入頁面
   * 當使用者想取消重設或直接返回時調用
   * @returns void
   */
  protected goToLogInPage(): void {
    this._router.navigateByUrl('/log-in');
  }
}

