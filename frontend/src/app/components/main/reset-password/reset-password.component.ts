/**
 * ============================================================================
 * 檔案名稱: reset-password.component.ts
 * 所屬模組: 密碼重置與設定模組 (Password Reset Module)
 * 責任描述: 負責提供使用者輸入信箱、6 碼驗證碼並設定新密碼，具備高強度密碼強度核驗與密碼一致性雙向驗證
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ApiClientService, ValidationService, DialogService, ActivatedRoute
 *   - 下游消費: app.routes.ts 路由導航調用, log-in.component
 * 邊界處理: 驗證碼錯誤或過期彈窗防呆；重設成功後導向登入頁
 * ============================================================================
 */

import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ApiClientService } from '../../../@services/api-client.service';
import { ValidationService } from '../../../@services/validation.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';

/**
 * 重設密碼元件 (ResetPasswordComponent)
 * 提供使用者輸入信箱、6 碼 OTP 驗證碼並設定新密碼。
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  /** 路由導航服務 */
  private readonly _router = inject(Router);
  /** 當前路由參數服務 */
  private readonly _route = inject(ActivatedRoute);
  /** 表單建構器服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 網路請求服務 */
  private readonly _apiClient = inject(ApiClientService);
  /** 欄位格式驗證服務 */
  private readonly _validationService = inject(ValidationService);
  /** 互動對話框服務 */
  private readonly _dialogService = inject(DialogService);

  /** 是否正在發送請求 */
  protected isSubmitting = false;

  /**
   * 重設密碼響應式表單群組
   */
  protected readonly formBuilderGroup = this._formBuilder.group(
    {
      email: ['', [Validators.required, Validators.email]],
      code: [
        '',
        [
          Validators.required,
          Validators.minLength(6),
          Validators.maxLength(6),
        ],
      ],
      newPassword: [
        '',
        [
          Validators.required,
          this._validationService.strictPasswordValidator(8, 16),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    {
      validators: [
        this._validationService.matchFieldsValidator(
          'newPassword',
          'confirmPassword',
        ),
      ],
    },
  );

  public ngOnInit(): void {
    // 從 URL QueryParams 嘗試預填 Email
    this._route.queryParams.subscribe((params) => {
      if (params['email']) {
        this.formBuilderGroup.patchValue({ email: params['email'] });
      }
      if (params['code']) {
        this.formBuilderGroup.patchValue({ code: params['code'] });
      }
    });
  }

  /**
   * 驗證重設密碼表單資料
   */
  protected checkResetPasswordData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

    if (this.formBuilderGroup.get('email')?.invalid) {
      errors.push('• 請輸入正確的電子信箱');
    }
    if (this.formBuilderGroup.get('code')?.invalid) {
      errors.push('• 請輸入信箱收到的 6 位數驗證碼');
    }
    if (this.formBuilderGroup.get('newPassword')?.invalid) {
      errors.push('• 新密碼須為 8 至 16 碼，且包含英文大小寫、數字與特殊符號');
    }
    if (this.formBuilderGroup.get('confirmPassword')?.invalid) {
      errors.push('• 請輸入確認密碼');
    }
    if (
      this.formBuilderGroup.hasError('passwordMismatch') &&
      !this.formBuilderGroup.get('confirmPassword')?.invalid
    ) {
      errors.push('• 確認密碼與新密碼不一致');
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

    this.sendResetPasswordData();
  }

  /**
   * 送出新密碼資料至後端
   */
  protected sendResetPasswordData(): void {
    const rawVal = this.formBuilderGroup.getRawValue();
    const payload = {
      email: rawVal.email?.trim(),
      code: rawVal.code?.trim(),
      newPassword: rawVal.newPassword,
    };

    this.isSubmitting = true;

    this._apiClient.post<any>('/api/public/auth/reset-password', payload).subscribe({
      next: () => {
        this.isSubmitting = false;
        const noticeData: DialogData = {
          title: '密碼重設成功',
          message: '您的登入密碼已成功更新！請使用全新密碼重新登入系統。',
          mode: 'noticeMode',
          confirmText: '前往登入',
          showCloseButton: false,
        };

        this._dialogService.showDialog(noticeData).subscribe(() => {
          this._router.navigateByUrl('/log-in');
        });
      },
      error: (err) => {
        this.isSubmitting = false;
        const errMsg = err?.error?.message || '密碼重設失敗，請確認驗證碼是否正確或已逾期。';
        this._dialogService.showDialog({
          title: '重設失敗',
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
