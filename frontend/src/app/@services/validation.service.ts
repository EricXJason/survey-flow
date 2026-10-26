/**
 * ============================================================================
 * 檔案名稱: validation.service.ts
 * 所屬模組: 表單驗證與規則防呆服務模組 (Form Validation Module)
 * 責任描述: 提供全站通用 Angular 響應式表單驗證器，涵蓋帳號格式、中文姓名、複合高強度密碼、臺灣手機號碼、臺灣室內電話、中華民國身分證字號加權驗證與跨欄位比對。
 * 架構分層: Service Layer / Validation & Security Layer
 * 聯動關係:
 *   - 上游依賴: @angular/forms (ValidatorFn, AbstractControl, ValidationErrors)
 *   - 下游消費: LogInComponent, RegisterComponent, UserProfileComponent, ResetPasswordComponent, ForgetPasswordComponent, SurveyRunnerComponent
 * 邊界處理: 空值預設略過由 Validators.required 負責；身分證採正規字母權重對照表與 Modulo 10 校驗碼嚴格演算
 * ============================================================================
 */

import { Injectable } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormGroup,
  ValidationErrors,
  ValidatorFn,
} from '@angular/forms';

@Injectable({
  providedIn: 'root',
})
export class ValidationService {
  /**
   * ==========================================================================
   * 1. 帳號與使用者名稱驗證器
   * ==========================================================================
   * 【規範】僅允許英文字母與數字，嚴格排除空格、中文字元與特殊符號。
   * 【預設值】長度 6 至 12 碼。
   *
   * @param minLength 最小長度限制（預設 6）
   * @param maxLength 最大長度限制（預設 12）
   * @param allowUnderscore 是否允許包含底線字元 `_`（預設 false）
   */
  public usernameValidator(
    minLength: number = 6,
    maxLength: number = 12,
    allowUnderscore: boolean = false,
  ): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: unknown = control.value;
      if (!value || typeof value !== 'string') {
        // 空值回傳 null，由 Validators.required 負責必填檢查
        return null;
      }

      const pattern: string = allowUnderscore
        ? `^[a-zA-Z0-9_]{${minLength},${maxLength}}$`
        : `^[a-zA-Z0-9]{${minLength},${maxLength}}$`;

      const regex: RegExp = new RegExp(pattern);
      return regex.test(value)
        ? null
        : {
          invalidUserName: {
            min: minLength,
            max: maxLength,
            allowUnderscore,
          },
        };
    };
  }

  /**
   * ==========================================================================
   * 2. 中文姓名驗證器
   * ==========================================================================
   * 【規範】僅允許繁簡中文字元，嚴禁空格、數字、英文與任何特殊符號。
   * 【預設值】長度 2 至 6 字。
   *
   * @param minLength 最小中文字數（預設 2）
   * @param maxLength 最大中文字數（預設 6）
   */
  public chineseNameValidator(
    minLength: number = 2,
    maxLength: number = 6,
  ): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const rawValue: unknown = control.value;
      if (rawValue === null || rawValue === undefined || rawValue === '') {
        return null;
      }

      const strValue = String(rawValue).trim();
      if (!strValue) {
        return null;
      }

      // 支援繁體中文常用漢字及 CJK 統一漢字集，去除前後空格防呆
      const regex: RegExp = new RegExp(
        `^[\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff]{${minLength},${maxLength}}$`,
      );
      return regex.test(strValue)
        ? null
        : {
          invalidChineseName: {
            min: minLength,
            max: maxLength,
          },
        };
    };
  }

  /**
   * ==========================================================================
   * 3. 複合高強度密碼驗證器
   * ==========================================================================
   * 【規範】必須同時包含：英文大寫、英文小寫、數字及指定特殊符號，禁止空格與中文。
   * 【預設值】長度 8 至 16 碼。
   *
   * @param minLength 最小密碼長度（預設 8）
   * @param maxLength 最大密碼長度（預設 16）
   * @param requireSpecialChar 是否強制要求特殊符號（預設 true）
   * @param symbolPattern 支援的特殊符號正規表示式字符集（預設 @$!%*?&._-#）
   */
  public strictPasswordValidator(
    minLength: number = 8,
    maxLength: number = 16,
    requireSpecialChar: boolean = true,
    symbolPattern: string = '@$!%*?&._\\-#',
  ): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: unknown = control.value;
      if (!value || typeof value !== 'string') {
        return null;
      }

      const pattern: string = requireSpecialChar
        ? `^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[${symbolPattern}])[a-zA-Z\\d${symbolPattern}]{${minLength},${maxLength}}$`
        : `^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{${minLength},${maxLength}}$`;

      const regex: RegExp = new RegExp(pattern);
      return regex.test(value)
        ? null
        : {
          invalidPassword: {
            min: minLength,
            max: maxLength,
            requireSpecialChar,
          },
        };
    };
  }

  /**
   * ==========================================================================
   * 4. 臺灣手機門號驗證器
   * ==========================================================================
   * 【規範】固定 10 碼純數字且以 09 開頭，支援帶連字號格式（如 0912-345-678）。
   *
   * @param allowHyphens 是否允許連字號格式（預設 false）
   */
  public taiwanPhoneValidator(allowHyphens: boolean = false): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: unknown = control.value;
      if (!value || typeof value !== 'string') {
        return null;
      }

      const regex: RegExp = allowHyphens
        ? /^09\d{2}-?\d{3}-?\d{3}$|^09\d{2}-?\d{6}$/
        : /^09\d{8}$/;

      return regex.test(value) ? null : { invalidPhone: true };
    };
  }

  /**
   * ==========================================================================
   * 5. 臺灣住家與室內電話驗證器
   * ==========================================================================
   * 【規範】支援 0 開頭之 2 至 3 碼區碼，包含 6 至 8 碼號碼，並相容連字號（例如 02-12345678、04-1234567、082-123456）。
   *
   * @param allowHyphens 是否強制或允許連字號格式（預設 true）
   */
  public taiwanTelValidator(allowHyphens: boolean = true): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: unknown = control.value;
      if (!value || typeof value !== 'string') {
        return null;
      }

      const regex: RegExp = allowHyphens
        ? /^(0\d{1,2})-?(\d{6,8})(#\d{1,6})?$/
        : /^0\d{7,10}$/;

      return regex.test(value) ? null : { invalidTel: true };
    };
  }

  /**
   * ==========================================================================
   * 6. 中華民國國民身分證字號驗證器
   * ==========================================================================
   * 【規範】首碼大寫英文字母 + 第 2 碼性別碼（1男/2女） + 8 碼流水校驗碼（Modulo 10）。
   */
  public taiwanNationalIdValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const rawValue: unknown = control.value;
      if (!rawValue) {
        return null;
      }

      const id: string = String(rawValue).trim().toUpperCase();
      if (!/^[A-Z][12]\d{8}$/.test(id)) {
        return { invalidNationalId: true };
      }

      const cityCodeMap: Record<string, number> = {
        A: 10,
        B: 11,
        C: 12,
        D: 13,
        E: 14,
        F: 15,
        G: 16,
        H: 17,
        I: 34,
        J: 18,
        K: 19,
        L: 20,
        M: 21,
        N: 22,
        O: 35,
        P: 23,
        Q: 24,
        R: 25,
        S: 26,
        T: 27,
        U: 28,
        V: 29,
        W: 32,
        X: 30,
        Y: 31,
        Z: 33,
      };

      const cityNum: number | undefined = cityCodeMap[id.charAt(0)];
      if (!cityNum) {
        return { invalidNationalId: true };
      }

      // 臺灣身分證檢查碼加權運算
      let sum: number = Math.floor(cityNum / 10) * 1 + (cityNum % 10) * 9;
      const weights: readonly number[] = [8, 7, 6, 5, 4, 3, 2, 1];

      for (let i = 0; i < weights.length; i++) {
        sum += parseInt(id.charAt(i + 1), 10) * weights[i];
      }
      sum += parseInt(id.charAt(9), 10);

      return sum % 10 === 0 ? null : { invalidNationalId: true };
    };
  }

  /**
   * ==========================================================================
   * 7. 跨欄位值比對驗證器
   * ==========================================================================
   * 【說明】掛載於 FormGroup 層級，核對密碼與確認密碼是否一致。
   *
   * @param primaryField 主欄位 Key 名稱（例如 'password'）
   * @param matchingField 次欄位 Key 名稱（例如 'confirmPassword'）
   * @param errorKey 自訂錯誤代碼（預設 'passwordMismatch'）
   */
  public matchFieldsValidator(
    primaryField: string = 'password',
    matchingField: string = 'confirmPassword',
    errorKey: string = 'passwordMismatch',
  ): ValidatorFn {
    return (group: AbstractControl): ValidationErrors | null => {
      const primaryControl: AbstractControl | null = group.get(primaryField);
      const matchingControl: AbstractControl | null = group.get(matchingField);

      if (!primaryControl || !matchingControl) {
        return null;
      }

      const primaryValue: unknown = primaryControl.value;
      const matchingValue: unknown = matchingControl.value;

      if (!primaryValue || !matchingValue) {
        return null;
      }

      return primaryValue === matchingValue ? null : { [errorKey]: true };
    };
  }

  /**
   * ==========================================================================
   * 8. 本地時區日期工具
   * ==========================================================================
   * 【說明】採用使用者設備本地時區（非 UTC），輸出 YYYY-MM-DD 格式字串。
   *
   * @param offsetDays 日期增減天數（例如 -1 為昨天，+30 為一個月後）
   * @param baseDate 基準日期（預設為執行當下系統時間）
   */
  public getLocalDate(
    offsetDays: number = 0,
    baseDate: Date = new Date(),
  ): string {
    const targetDate: Date = new Date(baseDate);
    if (offsetDays !== 0) {
      targetDate.setDate(targetDate.getDate() + offsetDays);
    }
    return new Intl.DateTimeFormat('en-CA', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(targetDate);
  }

  /**
   * ==========================================================================
   * 9. 四位數年份合法日期驗證器
   * ==========================================================================
   * 【規範】嚴格限制輸入之日期字串年份必須為剛好 4 位數（介於 minYear 與 maxYear 之間），防範 5~6 位數年份異常輸入。
   *
   * @param minYear 最早允許年份（預設 1900）
   * @param maxYear 最晚允許年份（預設 2099）
   */
  public fourDigitYearDateValidator(minYear: number = 1900, maxYear: number = 2099): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;
      const strVal = String(value).trim();
      const match = strVal.match(/^(\d{1,6})[-/](\d{1,2})[-/](\d{1,2})/);
      if (!match) return null;
      const yearStr = match[1];
      if (yearStr.length !== 4) {
        return { invalidYearLength: { required: 4, actual: yearStr.length } };
      }
      const year = parseInt(yearStr, 10);
      if (year < minYear || year > maxYear) {
        return { yearOutOfRange: { min: minYear, max: maxYear, actual: year } };
      }
      return null;
    };
  }
}

