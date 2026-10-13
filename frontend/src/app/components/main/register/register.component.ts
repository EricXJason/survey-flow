/**
 * ============================================================================
 * 檔案名稱: register.component.ts
 * 所屬模組: 會員註冊模組 (Member Registration Module)
 * 責任描述: 負責新使用者註冊流程，包含帳號格式、密碼一致性、中文姓名、身分證字號雙向權重演算、臺灣手機門號等欄位嚴格核驗與送出。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/validation.service, ../../../@services/dialog.service, ../../../@services/user-emitter.service, ../../../@interfaces/account-model
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 生日最大限制鎖定今日；所有錯誤項目以條列清單對話框明確反饋
 * ============================================================================
 */

import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AccountRegisterPayload } from '../../../@interfaces/account-model';
import { ValidationService } from '../../../@services/validation.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';

/**
 * 會員註冊頁面組件 (RegisterComponent)
 * 負責新使用者註冊帳號流程，包含複合密碼一致性檢核、
 * 臺灣身分證字號雙向字母權重演算法防呆、臺灣手機號碼格式核驗、中文姓名檢核與送出註冊。
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  /** 響應式表單建構服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 表單格式驗證服務 */
  private readonly _validationService = inject(ValidationService);
  /** 系統對話框管理服務 */
  private readonly _dialogService = inject(DialogService);
  /** 使用者事件廣播服務（觸發註冊事件） */
  private readonly _userEmitter = inject(UserEmitterService);

  /** 生日最大允許日期（不能晚於今日） */
  protected readonly maxBirthday = this._validationService.getLocalDate();

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
        [Validators.required, this._validationService.taiwanPhoneValidator()],
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

  /**
   * 完整核驗註冊表單各欄位之規範
   * 包含帳號、高強度密碼、確認密碼一致性、中文姓名、身分證字號雙向權重演算法、臺灣手機與 Email。
   * 若有錯誤彈出提示對話框；核驗完全通過則進入 sendRegisterData 流程。
   */
  protected checkRegisterData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

    if (this.formBuilderGroup.get('username')?.invalid) {
      errors.push('• 使用者帳號未填寫或長度格式不符（須為 6 至 12 碼）');
    }
    if (this.formBuilderGroup.get('password')?.invalid) {
      errors.push('• 設定密碼未填寫或複雜度不符（須為 8 至 16 碼英數組合）');
    }
    if (this.formBuilderGroup.get('confirmPassword')?.invalid) {
      errors.push('• 確認密碼尚未填寫');
    }
    if (this.formBuilderGroup.errors?.['passwordMismatch']) {
      errors.push('• 確認密碼與設定密碼不一致');
    }
    if (this.formBuilderGroup.get('name')?.invalid) {
      errors.push('• 真實姓名未填寫或格式不符（須為 2 至 6 字中文姓名）');
    }
    if (this.formBuilderGroup.get('nationalId')?.invalid) {
      errors.push('• 身分證字號未填寫或格式檢核未通過');
    }
    if (this.formBuilderGroup.get('email')?.invalid) {
      errors.push('• 電子信箱未填寫或格式錯誤');
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
   * 格式化表單資料為標準之註冊載荷結構 (AccountRegisterPayload)
   * @returns 結構化之註冊資料載荷物件
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
   * 送出註冊資料
   * 格式化資料並透過 UserEmitterService 觸發後端註冊存檔與登入綁定。
   */
  protected sendRegisterData(): void {
    const payload: AccountRegisterPayload = this.formatRegisterData();
    this._userEmitter.emitSignUp(payload);
  }
}
