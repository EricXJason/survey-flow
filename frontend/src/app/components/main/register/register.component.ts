/**
 * ============================================================================
 * 檔案名稱: register.component.ts
 * 所屬模組: 會員註冊模組 (Member Registration Module)
 * 責任描述: 負責新使用者註冊流程與 Email 信箱 6 碼驗證碼啟用閉環，包含欄位格式嚴格檢核、送出註冊、發送驗證碼與開通登入。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ApiClientService, UserStateService, ValidationService, DialogService, Router
 *   - 下游消費: app.routes.ts 路由導航調用, lobby.component
 * 邊界處理: 驗證碼 6 碼檢核；重發 60 秒倒數防刷；開通後直接持久化雙 Token 登入
 * ============================================================================
 */

import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AccountRegisterPayload, CAREER_OPTIONS } from '../../../@interfaces/account-model';
import { ValidationService } from '../../../@services/validation.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { ApiClientService } from '../../../@services/api-client.service';
import { UserStateService } from '../../../@services/user-state.service';

/**
 * 會員註冊頁面組件 (RegisterComponent)
 * 負責新使用者註冊帳號流程與信箱啟用雙步驟流程。
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent implements OnDestroy {
  /** 全域標準職業選項清單 */
  public readonly careerOptions = CAREER_OPTIONS;

  /** 響應式表單建構服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 表單格式驗證服務 */
  private readonly _validationService = inject(ValidationService);
  /** 系統對話框管理服務 */
  private readonly _dialogService = inject(DialogService);
  /** 網路請求服務 */
  private readonly _apiClient = inject(ApiClientService);
  /** 會員狀態中心服務 */
  private readonly _userState = inject(UserStateService);
  /** 路由導航服務 */
  private readonly _router = inject(Router);

  /** 生日最大允許日期（不能晚於今日） */
  protected readonly maxBirthday = this._validationService.getLocalDate();

  /** 當前步驟狀態：是否正在進行信箱 6 碼驗證 */
  protected isVerifyingEmail = false;

  /** 已送出註冊之電子信箱 */
  protected registeredEmail = '';

  /** 使用者輸入之 6 碼啟用碼 */
  protected verificationCode = '';

  /** 是否正在送出註冊資料 */
  protected isSubmitting = false;

  /** 是否正在驗證啟用碼 */
  protected isSubmittingCode = false;

  /** 重新發送倒數秒數 */
  protected resendCountdown = 0;

  /** 倒數定時器 */
  private _countdownTimer: any = null;

  /** 註冊表單群組與跨欄位驗證設定 */
  protected readonly formBuilderGroup = this._formBuilder.group(
    {
      username: [
        '',
        [Validators.required, this._validationService.usernameValidator(6, 12)],
      ],
      password: [
        '',
        [
          Validators.required,
          this._validationService.strictPasswordValidator(8, 16),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
      name: [
        '',
        [
          Validators.required,
          this._validationService.chineseNameValidator(2, 6),
        ],
      ],
      nationalId: [
        '',
        [
          Validators.required,
          this._validationService.taiwanNationalIdValidator(),
        ],
      ],
      email: ['', [Validators.required, Validators.email]],
      phone: [
        '',
        [
          Validators.required,
          this._validationService.taiwanPhoneValidator(),
        ],
      ],
      gender: ['', [Validators.required]],
      birthday: ['', [Validators.required]],
      role: ['', [Validators.required]],
    },
    {
      validators: [
        this._validationService.matchFieldsValidator(
          'password',
          'confirmPassword',
        ),
      ],
    },
  );

  public ngOnDestroy(): void {
    if (this._countdownTimer) {
      clearInterval(this._countdownTimer);
    }
  }

  /**
   * 檢核表單所有欄位之合規性
   */
  protected checkRegisterData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

    if (this.formBuilderGroup.get('username')?.invalid) {
      errors.push('• 帳號未填寫或格式錯誤（須為 6 至 12 碼英數字）');
    }
    if (this.formBuilderGroup.get('password')?.invalid) {
      errors.push(
        '• 密碼格式錯誤（須為 8 至 16 碼，且包含英文大小寫、數字與特殊符號）',
      );
    }
    if (this.formBuilderGroup.get('confirmPassword')?.invalid) {
      errors.push('• 請輸入確認密碼');
    }
    if (
      this.formBuilderGroup.hasError('passwordMismatch') &&
      !this.formBuilderGroup.get('confirmPassword')?.invalid
    ) {
      errors.push('• 確認密碼與密碼不相符');
    }
    if (this.formBuilderGroup.get('name')?.invalid) {
      errors.push('• 真實中文姓名未填寫或長度不符（須為 2 至 6 字繁體中文）');
    }
    if (this.formBuilderGroup.get('nationalId')?.invalid) {
      errors.push('• 身分證字號未填寫或檢核碼不符');
    }
    if (this.formBuilderGroup.get('email')?.invalid) {
      errors.push('• 電子信箱未填寫或格式不正確');
    }
    if (this.formBuilderGroup.get('phone')?.invalid) {
      errors.push('• 手機號碼未填寫或格式錯誤（須為臺灣 09 開頭手機號碼）');
    }
    if (this.formBuilderGroup.get('gender')?.invalid) {
      errors.push('• 生理性別尚未選擇');
    }
    if (this.formBuilderGroup.get('birthday')?.invalid) {
      errors.push('• 出生日期尚未選擇');
    }
    if (this.formBuilderGroup.get('role')?.invalid) {
      errors.push('• 身分職業尚未選擇');
    }

    if (errors.length > 0) {
      const errorData: DialogData = {
        title: '欄位驗證錯誤',
        message: `請修正以下項目後再送出：\n\n${errors.join('\n')}`,
        mode: 'errorMode',
        confirmText: '了解',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

    this.sendRegisterData();
  }

  /**
   * 格式化表單資料為標準之註冊載荷結構
   */
  protected formatRegisterData(): AccountRegisterPayload {
    const formRaw = this.formBuilderGroup.getRawValue();

    return {
      username: formRaw.username ?? '',
      password: formRaw.password ?? '',
      name: formRaw.name ?? '',
      nationalId: formRaw.nationalId ?? '',
      email: formRaw.email ?? '',
      phone: formRaw.phone ?? '',
      gender: formRaw.gender ?? '',
      birthday: formRaw.birthday ?? '',
      role: formRaw.role ?? '',
    };
  }

  /**
   * 送出註冊資料並切換至信箱啟用面板
   */
  protected sendRegisterData(): void {
    const payload: AccountRegisterPayload = this.formatRegisterData();
    const registerDto = {
      username: payload.username.trim(),
      email: payload.email.trim(),
      password: payload.password,
      fullName: payload.name.trim(),
      phone: payload.phone?.trim(),
      identityCard: payload.nationalId?.trim(),
    };

    this.isSubmitting = true;

    this._apiClient.post<any>('/api/public/auth/register', registerDto).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.registeredEmail = payload.email.trim();
        this.isVerifyingEmail = true;
        this.startResendCountdown();

        this._dialogService.showDialog({
          title: '註冊資料已建立',
          message: `6 位數安全啟用碼已寄送至：\n${this.registeredEmail}\n\n請查收郵件並輸入驗證碼以完成開通。`,
          mode: 'noticeMode',
          confirmText: '開始驗證',
        }).subscribe();
      },
      error: (err) => {
        this.isSubmitting = false;
        const errMsg = err?.error?.message || '註冊失敗，該帳號或信箱可能已被使用，請檢查後再試。';
        this._dialogService.showDialog({
          title: '註冊失敗',
          message: errMsg,
          mode: 'errorMode',
          confirmText: '確定',
        }).subscribe();
      },
    });
  }

  /**
   * 送出信箱 6 碼驗證碼以開通帳號
   */
  protected submitEmailVerification(): void {
    const code = this.verificationCode.trim();
    if (code.length !== 6) {
      return;
    }

    this.isSubmittingCode = true;

    this._apiClient.post<any>('/api/public/auth/verify-email', {
      email: this.registeredEmail,
      code,
    }).subscribe({
      next: (res) => {
        this.isSubmittingCode = false;
        const data = res?.data;

        if (data?.token || data?.accessToken) {
          sessionStorage.setItem('SURVEY_ACCESS_TOKEN', data.accessToken || data.token);
          if (data.refreshToken) {
            sessionStorage.setItem('SURVEY_REFRESH_TOKEN', data.refreshToken);
          }
          this._userState.setIdentity({
            id: String(data.userId || data.id || '1'),
            access: data.role === 'ROLE_ADMIN' ? 'admin' : 'member',
            userName: data.username,
            name: data.fullName || data.name || data.username,
            nationalId: '',
            email: data.email,
            phone: '',
            gender: 'male',
            birthday: '1990-01-01',
            role: data.role === 'ROLE_ADMIN' ? 'admin' : 'member',
          });
        }

        this._dialogService.showDialog({
          title: '信箱驗證成功',
          message: '恭喜您！您的會員帳號已成功開通，系統已為您自動登入！',
          mode: 'noticeMode',
          confirmText: '進入問卷大廳',
          showCloseButton: false,
        }).subscribe(() => {
          this._router.navigateByUrl('/lobby');
        });
      },
      error: (err) => {
        this.isSubmittingCode = false;
        const errMsg = err?.error?.message || '驗證碼錯誤或已過期，請重新確認。';
        this._dialogService.showDialog({
          title: '驗證失敗',
          message: errMsg,
          mode: 'errorMode',
          confirmText: '重新輸入',
        }).subscribe();
      },
    });
  }

  /**
   * 重新發送註冊啟用驗證碼
   */
  protected resendVerificationCode(): void {
    if (this.resendCountdown > 0) {
      return;
    }

    this._apiClient.post<any>('/api/public/auth/resend-verification', {
      email: this.registeredEmail,
    }).subscribe({
      next: () => {
        this.startResendCountdown();
        this._dialogService.showDialog({
          title: '驗證信已重新寄發',
          message: `新的 6 位數啟用碼已寄出至：\n${this.registeredEmail}\n\n請查收郵件。`,
          mode: 'noticeMode',
          confirmText: '了解',
        }).subscribe();
      },
      error: (err) => {
        const errMsg = err?.error?.message || '重新發送失敗，請稍後再試。';
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
   * 返回修改註冊表單
   */
  protected backToRegisterForm(): void {
    this.isVerifyingEmail = false;
    this.verificationCode = '';
  }

  /**
   * 啟動 60 秒防刷倒數計時器
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
      }
    }, 1000);
  }
}
