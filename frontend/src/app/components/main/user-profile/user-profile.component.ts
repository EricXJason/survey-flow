/**
 * ============================================================================
 * 檔案名稱: user-profile.component.ts
 * 所屬模組: 會員個人資料管理模組 (User Profile Module)
 * 責任描述: 負責登入會員檢視與修改個人基本資料、折疊式新密碼修改面板控制、離開前防呆守衛 (canDeactivate) 及更新派發。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/user-state.service, ../../../@services/user-emitter.service, ../../../@services/dialog.service, ../../../@services/validation.service
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 管理員進入自動導航至會員後台；未儲存變更離開時觸發確認對話框攔截跳轉
 * ============================================================================
 */

import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { ValidationService } from '../../../@services/validation.service';
import { UserStateService } from '../../../@services/user-state.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { AccountModifyProfilePayload } from '../../../@interfaces/account-model';

/**
 * 個人資料編輯組件 (UserProfileComponent)
 * 負責登入會員查看與維護個人基本資料（真實姓名、身分證字號、手機號碼、職業身分等），
 * 並提供彈性展開的密碼修改機制（新密碼高複雜度核對與一致性比對）。
 */
@Component({
  selector: 'app-user-profile',
  templateUrl: './user-profile.component.html',
  styleUrl: './user-profile.component.scss',
  imports: [ReactiveFormsModule],
})
export class UserProfileComponent implements OnInit {
  /** 路由導航服務 */
  private readonly _router = inject(Router);
  /** 響應式表單建構服務 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 格式防呆檢核服務 */
  private readonly _validationService = inject(ValidationService);
  /** 使用者狀態管理服務 */
  private readonly _userState = inject(UserStateService);
  /** 使用者事件廣播服務（觸發個人資料更新） */
  private readonly _userEmitter = inject(UserEmitterService);
  /** 系統全域對話框服務 */
  private readonly _dialogService = inject(DialogService);

  /** 生日欄位最大可選日期（今日） */
  protected readonly maxBirthday = this._validationService.getLocalDate();
  /** 是否展開新密碼修改面板之狀態旗標 */
  protected isPasswordChangeExpanded: boolean = false;
  /** 個人資料編輯表單群組執行個體 */
  protected readonly formBuilderGroup: FormGroup = this.formBuilderGroupInit();
  /** 個人資料是否已送出儲存之狀態旗標 */
  private _isSubmitted: boolean = false;

  /**
   * Angular 生命週期鉤子 (ngOnInit)
   * 組件初始化時自動自 UserStateService 載入已登入使用者的個人資料並填入表單。
   */
  public ngOnInit(): void {
    if (this._userState.access() === 'admin') {
      this._router.navigate(['/user-management']);
      return;
    }
    this.loadUserProfile();
    this.formBuilderGroup.valueChanges.subscribe(() => {
      this._isSubmitted = false;
    });
  }

  /**
   * 路由離開守衛 (canDeactivate)
   * 當使用者試圖離開個人資料頁面時，若表單已被變更且尚未正式儲存，
   * 攔截跳轉並彈出操作確認對話框詢問「是否確定跳出？尚未儲存的個人資料變更將會遺失。」。
   * 只有在使用者點選「是」確認離開時才放行路由跳出；
   * 若點選「返回」或關閉對話框，則由 Angular Router 取消跳轉，維持當前編輯狀態。
   */
  public canDeactivate(): Observable<boolean> | boolean {
    if (this.formBuilderGroup.dirty && !this._isSubmitted) {
      const confirmData: DialogData = {
        title: '跳出確認',
        message: '是否確定跳出？尚未儲存的個人資料變更將會遺失。',
        mode: 'checkMode',
        confirmText: '是',
        cancelText: '返回',
        showCloseButton: true,
      };

      return this._dialogService.showDialog(confirmData);
    }
    return true;
  }

  /**
   * 初始化個人資料表單群組
   * 帳號與 Email 為不可變更欄位 (disabled: true)，其他基本欄位配置相應格式驗證器。
   * @returns FormGroup 個人資料表單物件
   */
  private formBuilderGroupInit(): FormGroup {
    const currentGender = this._userState?.userProfile()?.gender || 'male';
    return this._formBuilder.group(
      {
        username: [{ value: '', disabled: true }],
        email: [{ value: '', disabled: true }],
        password: [''],
        confirmPassword: [''],
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
        phone: [
          '',
          [Validators.required, this._validationService.taiwanPhoneValidator()],
        ],
        gender: [currentGender, [Validators.required]],
        birthday: ['', [Validators.required]],
        role: ['', [Validators.required]],
      },
      {
        validators: [this.passwordMatchValidator()],
      },
    );
  }

  /**
   * 切換「修改密碼」折疊面板展開/收合狀態
   * 當展開時動態為密碼與確認密碼欄位附加必填與嚴格強度驗證器；
   * 當收合時清空驗證器與欄位輸入值，避免阻擋基本資料更新。
   */
  protected togglePasswordChange(): void {
    this.isPasswordChangeExpanded = !this.isPasswordChangeExpanded;
    const passwordCtrl = this.formBuilderGroup.get('password');
    const confirmPasswordCtrl = this.formBuilderGroup.get('confirmPassword');

    if (this.isPasswordChangeExpanded) {
      passwordCtrl?.setValidators([
        Validators.required,
        this._validationService.strictPasswordValidator(8, 16),
      ]);
      confirmPasswordCtrl?.setValidators([Validators.required]);
    } else {
      passwordCtrl?.clearValidators();
      confirmPasswordCtrl?.clearValidators();
      passwordCtrl?.reset('');
      confirmPasswordCtrl?.reset('');
    }

    passwordCtrl?.updateValueAndValidity();
    confirmPasswordCtrl?.updateValueAndValidity();
    this.formBuilderGroup.updateValueAndValidity();
  }

  /**
   * 密碼與確認密碼一致性自訂驗證函式
   * @returns ValidatorFn 表單群組驗證函式
   */
  private passwordMatchValidator(): ValidatorFn {
    return (group: AbstractControl): ValidationErrors | null => {
      if (!this.isPasswordChangeExpanded) {
        return null;
      }

      const password = group.get('password')?.value;
      const confirmPassword = group.get('confirmPassword')?.value;

      if (!password || !confirmPassword) {
        return null;
      }

      return password === confirmPassword ? null : { passwordMismatch: true };
    };
  }

  /**
   * 從 UserStateService 載入當前使用者個人檔案並回填至表單控制項
   * 確保性別對齊帳號現有設定，若初始值為空則預設賦予生理性別（預設男），絕不留空。
   */
  private loadUserProfile(): void {
    const profile = this._userState.userProfile();
    let accountGender = profile.gender;
    if (accountGender === '男') {
      accountGender = 'male';
    } else if (accountGender === '女') {
      accountGender = 'female';
    } else if (accountGender === '其他') {
      accountGender = 'other';
    }

    if (!accountGender) {
      accountGender = 'male';
      this._userState.setIdentity({
        ...profile,
        gender: 'male',
      });
    }

    // 身分職業值對應字典（確保相容中文稱謂與英文代碼）
    const roleMap: Record<string, string> = {
      student: 'student',
      teacher: 'teacher',
      researcher: 'researcher',
      employee: 'employee',
      retired: 'retired',
      other: 'other',
      '學生': 'student',
      '教師': 'teacher',
      '研究人員': 'researcher',
      '上班族': 'employee',
      '已退休': 'retired',
      '其他': 'other',
      '一般會員': 'employee',
      '管理員': 'researcher',
    };

    let accountRole = profile.role ? roleMap[profile.role] || 'employee' : 'employee';

    this.formBuilderGroup.patchValue({
      username: profile.userName,
      email: profile.email,
      name: profile.name,
      nationalId: profile.nationalId,
      phone: profile.phone,
      gender: accountGender,
      birthday: profile.birthday,
      role: accountRole,
    });
  }

  /**
   * 取得個人資料欄位元資料清單（用於錯誤提示文字組裝）
   * @returns 包含鍵名與可讀標籤的物件陣列
   */
  private getProfileFieldMetaList(): { key: string; label: string }[] {
    return [
      { key: 'name', label: '真實姓名（須為 2 至 6 字中文姓名）' },
      { key: 'nationalId', label: '身分證字號（格式檢核未通過）' },
      { key: 'phone', label: '手機號碼（須為臺灣 09 開頭 10 碼電話）' },
      { key: 'gender', label: '生理性別' },
      { key: 'birthday', label: '出生日期' },
      { key: 'role', label: '身分職業' },
    ];
  }

  /**
   * 核驗個人資料表單完整性與密碼規則
   * 若密碼修改展開，檢查新密碼強度與一致性；隨後核驗姓名、身分證字號、手機格式。
   * 核驗通過後呼叫 sendProfileData 進行更新存檔。
   */
  protected checkProfileData(): void {
    this.formBuilderGroup.markAllAsTouched();
    const errors: string[] = [];

    if (this.isPasswordChangeExpanded) {
      const passwordCtrl = this.formBuilderGroup.get('password');
      const confirmPasswordCtrl = this.formBuilderGroup.get('confirmPassword');

      if (passwordCtrl?.hasError('required')) {
        errors.push('• 新密碼尚未填寫');
      } else if (passwordCtrl?.invalid) {
        errors.push(
          '• 新密碼強度不符（須為 8 至 16 碼，包含英文大小寫、數字與特殊符號）',
        );
      }

      if (confirmPasswordCtrl?.hasError('required')) {
        errors.push('• 確認新密碼尚未填寫');
      } else if (this.formBuilderGroup.hasError('passwordMismatch')) {
        errors.push('• 確認新密碼與新密碼不一致');
      }
    }

    for (const meta of this.getProfileFieldMetaList()) {
      const control = this.formBuilderGroup.get(meta.key);
      if (control && control.invalid) {
        errors.push(`• ${meta.label} 尚未填寫或格式錯誤`);
      }
    }

    if (errors.length > 0) {
      const errorData: DialogData = {
        title: '個人資料欄位格式有誤',
        message: '請修正以下欄位內容後再重新送出：\n\n' + errors.join('\n'),
        mode: 'errorMode',
        confirmText: '我知道了',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData);
      return;
    }

    this.sendProfileData();
  }

  /**
   * 格式化表單資料為更新載荷 (AccountModifyProfilePayload)
   * 若未展開密碼修改，則 payload 中不包含 password 欄位。
   * @returns 結構化更新資料載荷物件
   */
  protected formatProfileData(): AccountModifyProfilePayload {
    const formRaw = this.formBuilderGroup.getRawValue();

    const payload: AccountModifyProfilePayload = {
      name: formRaw.name ?? '',
      nationalId: formRaw.nationalId ?? '',
      phone: formRaw.phone ?? '',
      gender: formRaw.gender ?? '',
      birthday: formRaw.birthday ?? '',
      role: formRaw.role ?? '',
    };

    if (this.isPasswordChangeExpanded && formRaw.password) {
      payload.password = formRaw.password;
    }

    return payload;
  }

  /**
   * 送出個人資料更新請求
   * 透過 UserEmitterService 觸發後端更新個人檔案。
   */
  protected sendProfileData(): void {
    this._isSubmitted = true;
    const payload = this.formatProfileData();
    this._userEmitter.emitUpdateProfile(payload);
  }
}
