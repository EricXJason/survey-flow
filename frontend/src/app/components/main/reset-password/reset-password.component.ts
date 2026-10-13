/**
 * ============================================================================
 * 檔案名稱: reset-password.component.ts
 * 所屬模組: 密碼重置與設定模組 (Password Reset Module)
 * 責任描述: 負責提供使用者輸入並確認新密碼，具備 8-16 碼高強度密碼強度核驗與密碼一致性雙向驗證。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/validation.service, ../../../@services/dialog.service
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 密碼不一致或強度不足時彈窗提示；重設成功後 3 秒自動返回登入頁
 * ============================================================================
 */

import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ValidationService } from '../../../@services/validation.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';

/**
 * 重設密碼元件 (ResetPasswordComponent)
 * 提供使用者設定並確認新密碼，具備高強度密碼檢核與密碼一致性驗證。
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent {
  /** 路由導航服務 */
  private readonly _router = inject(Router);
  /** 表單建構器服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 欄位格式驗證服務 */
  private readonly _validationService = inject(ValidationService);
  /** 互動對話框服務 */
  private readonly _dialogService = inject(DialogService);

  /**
   * 重設密碼響應式表單群組
   * 包含 newPassword (高強度密碼規範 8-16 碼且含大小寫/數字/特殊符號) 與 confirmPassword，並配有欄位一致性驗證器
   */
  protected readonly formBuilderGroup = this._formBuilder.group(
    {
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

  /**
   * 驗證重設密碼表單資料
   * 標記表單為已觸碰，驗證密碼強度及兩次輸入是否一致；若有錯誤跳出對話框提示，合規即調用 sendResetPasswordData()
   * @returns void
   */
  protected checkResetPasswordData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

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
   * 送出新密碼資料
   * 呼叫後端 API 執行密碼重設，成功後彈出通知並於倒數 3 秒後導航回登入頁面
   * @returns void
   */
  protected sendResetPasswordData(): void {
    // 待後端就緒後以權杖與新密碼發送 POST /api/auth/reset-password

    const noticeData: DialogData = {
      title: '密碼重設成功',
      message: '您的密碼已成功更新，系統將於 3 秒後自動返回登入頁。',
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
   * 使用者取消操作或直接返回時調用
   * @returns void
   */
  protected goToLogInPage(): void {
    this._router.navigateByUrl('/log-in');
  }
}

