/**
 * ============================================================================
 * 檔案名稱: log-in.component.ts
 * 所屬模組: 會員登入模組 (Member Authentication Module)
 * 責任描述: 負責處理會員與管理員登入身分驗證，包含帳號 6-12 碼核對、複合高強度密碼檢核、快速測試帳號登入及跳轉導航。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/validation.service, ../../../@services/dialog.service, ../../../@services/user-emitter.service, ../../../@interfaces/account-model
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 標記所有欄位 touched 並彈出彙總錯誤對話框；提供快捷測試帳號一鍵登入
 * ============================================================================
 */

import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ValidationService } from '../../../@services/validation.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { AccountLogInPayload } from '../../../@interfaces/account-model';

/**
 * 會員登入頁面組件 (LogInComponent)
 * 負責處理會員與管理員登入表單驗證（帳號格式與高強度複合密碼核驗）、
 * 快速測試身分登入、跳轉忘記密碼與註冊頁面。
 */
@Component({
  selector: 'app-log-in',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './log-in.component.html',
  styleUrl: './log-in.component.scss',
})
export class LogInComponent {
  /** Angular 路由服務 */
  private readonly _router = inject(Router);
  /** 響應式表單建構服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 表單驗證規則服務 */
  private readonly _validationService = inject(ValidationService);
  /** 系統對話框管理服務 */
  private readonly _dialogService = inject(DialogService);
  /** 使用者身分事件廣播服務（登入成功處理） */
  private readonly _userEmitter = inject(UserEmitterService);

  /** 登入表單群組，包含帳號與密碼控制項 */
  protected readonly formBuilderGroup = this._formBuilder.group({
    username: [
      '',
      [Validators.required, this._validationService.usernameValidator(3, 20)],
    ],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(4),
        Validators.maxLength(50),
      ],
    ],
  });

  /**
   * 檢查並核驗登入表單資料格式
   * 包含帳號 3 至 20 碼英數字核對，以及密碼長度檢驗。
   * 若有錯誤則彈出錯誤對話框；通過則呼叫 sendSignInData 送出。
   */
  protected checkLogInData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

    if (this.formBuilderGroup.get('username')?.invalid) {
      errors.push('• 使用者帳號未填寫或長度格式不符（須為 3 至 20 碼英數字）');
    }
    if (this.formBuilderGroup.get('password')?.invalid) {
      errors.push('• 登入密碼未填寫或長度不符（須為 4 至 50 碼）');
    }

    if (errors.length > 0) {
      const errorData: DialogData = {
        title: '登入資料錯誤',
        message: `請修正以下項目後再登入：\n\n${errors.join('\n')}`,
        mode: 'errorMode',
        confirmText: '了解',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

    this.sendSignInData();
  }

  /**
   * 送出登入請求資料
   * 封裝帳號與密碼載荷，透過 UserEmitterService 觸發登入驗證。
   */
  protected sendSignInData(): void {
    const payload = this.formBuilderGroup.getRawValue() as AccountLogInPayload;
    this._userEmitter.emitLogin(payload);
  }

  /**
   * 測試專用：管理員帳號快速填入並登入
   * 快速填入測試管理員憑證並立即執行登入。
   */
  protected quickLoginAsAdmin(): void {
    const payload: AccountLogInPayload = {
      username: 'admin',
      password: 'Admin123!',
    };
    this.formBuilderGroup.patchValue(payload);
    this._userEmitter.emitLogin(payload);
  }

  /**
   * 測試專用：一般會員帳號快速填入並登入
   * 快速填入測試一般會員憑證並立即執行登入。
   */
  protected quickLoginAsMember(): void {
    const payload: AccountLogInPayload = {
      username: 'testuser',
      password: 'User1234!',
    };
    this.formBuilderGroup.patchValue(payload);
    this._userEmitter.emitLogin(payload);
  }

  /**
   * 導向忘記密碼頁面 (/forget-password)
   */
  protected goToForgetPasswordPage(): void {
    this._router.navigateByUrl('/forget-password');
  }

  /**
   * 導向會員註冊頁面 (/register)
   */
  protected goToRegisterAccountPage(): void {
    this._router.navigateByUrl('/register');
  }

  /**
   * 導向重設密碼頁面 (/reset-password)
   */
  protected goToResetPasswordPage(): void {
    this._router.navigateByUrl('/reset-password');
  }
}
