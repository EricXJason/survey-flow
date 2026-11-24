/**
 * ============================================================================
 * 檔案名稱: validation.service.spec.ts
 * 測試對象: ValidationService (帳號、中文姓名、密碼、手機、市話、身分證、雙欄比對、日期年份等表單驗證器)
 * ============================================================================
 */

import { FormControl, FormGroup, ValidatorFn } from '@angular/forms';
import { ValidationService } from './validation.service';

describe('ValidationService', () => {
  const service = new ValidationService();

  /**
   * 以指定驗證器驗證單一值
   * @param validator 驗證器
   * @param value 欄位值
   * @returns 驗證錯誤物件，通過時為 null
   */
  const check = (validator: ValidatorFn, value: unknown) => validator(new FormControl(value));

  it('空值一律略過 (必填交由 Validators.required 處理)', () => {
    const validators = [
      service.usernameValidator(),
      service.chineseNameValidator(),
      service.strictPasswordValidator(),
      service.taiwanPhoneValidator(),
      service.taiwanTelValidator(),
      service.taiwanNationalIdValidator(),
      service.fourDigitYearDateValidator(),
    ];
    validators.forEach((validator) => {
      expect(check(validator, '')).toBeNull();
      expect(check(validator, null)).toBeNull();
    });
    expect(check(service.chineseNameValidator(), '   ')).toBeNull();
    expect(check(service.usernameValidator(), 123)).toBeNull();
  });

  it('帳號：英數 6~12 碼；可選擇允許底線', () => {
    expect(check(service.usernameValidator(), 'user01')).toBeNull();
    expect(check(service.usernameValidator(), 'user 01')).toEqual({ invalidUserName: { min: 6, max: 12, allowUnderscore: false } });
    expect(check(service.usernameValidator(), 'abc')).not.toBeNull();
    expect(check(service.usernameValidator(), 'user_01')).not.toBeNull();
    expect(check(service.usernameValidator(6, 12, true), 'user_01')).toBeNull();
  });

  it('中文姓名：2~6 個中文字，前後空白會先去除', () => {
    expect(check(service.chineseNameValidator(), ' 王小明 ')).toBeNull();
    expect(check(service.chineseNameValidator(), '王')).toEqual({ invalidChineseName: { min: 2, max: 6 } });
    expect(check(service.chineseNameValidator(), 'Tom')).not.toBeNull();
  });

  it('密碼：須含大小寫、數字與特殊符號，8~16 碼；可關閉特殊符號要求', () => {
    expect(check(service.strictPasswordValidator(), 'Password1!')).toBeNull();
    expect(check(service.strictPasswordValidator(), 'Password1')).toEqual({
      invalidPassword: { min: 8, max: 16, requireSpecialChar: true },
    });
    expect(check(service.strictPasswordValidator(), 'password1!')).not.toBeNull();
    expect(check(service.strictPasswordValidator(8, 16, false), 'Password1')).toBeNull();
    expect(check(service.strictPasswordValidator(8, 16, false), 'Password1!')).not.toBeNull();
  });

  it('手機：預設只接受 09 開頭 10 碼；允許連字號時接受 0912-345-678', () => {
    expect(check(service.taiwanPhoneValidator(), '0912345678')).toBeNull();
    expect(check(service.taiwanPhoneValidator(), '0912-345-678')).toEqual({ invalidPhone: true });
    expect(check(service.taiwanPhoneValidator(true), '0912-345-678')).toBeNull();
    expect(check(service.taiwanPhoneValidator(true), '0812345678')).not.toBeNull();
  });

  it('市話：預設允許區碼連字號與分機；關閉時只接受純數字', () => {
    expect(check(service.taiwanTelValidator(), '02-23456789#123')).toBeNull();
    expect(check(service.taiwanTelValidator(), '2345678')).toEqual({ invalidTel: true });
    expect(check(service.taiwanTelValidator(false), '0223456789')).toBeNull();
    expect(check(service.taiwanTelValidator(false), '02-23456789')).not.toBeNull();
  });

  it('身分證：格式與檢查碼都要正確；小寫與前後空白可接受', () => {
    expect(check(service.taiwanNationalIdValidator(), 'A123456789')).toBeNull();
    expect(check(service.taiwanNationalIdValidator(), ' a123456789 ')).toBeNull();
    expect(check(service.taiwanNationalIdValidator(), 'A123456788')).toEqual({ invalidNationalId: true });
    expect(check(service.taiwanNationalIdValidator(), 'A323456789')).toEqual({ invalidNationalId: true });
    expect(check(service.taiwanNationalIdValidator(), '1234567890')).toEqual({ invalidNationalId: true });
  });

  it('雙欄比對：兩欄相同才通過；任一欄空白或欄位不存在時略過；可自訂錯誤鍵', () => {
    const group = new FormGroup({ password: new FormControl('Aa1!aaaa'), confirmPassword: new FormControl('Aa1!aaab') });
    expect(service.matchFieldsValidator()(group)).toEqual({ passwordMismatch: true });

    group.controls.confirmPassword.setValue('Aa1!aaaa');
    expect(service.matchFieldsValidator()(group)).toBeNull();

    group.controls.confirmPassword.setValue('');
    expect(service.matchFieldsValidator()(group)).toBeNull();

    const emailGroup = new FormGroup({ email: new FormControl('a@x.com'), emailAgain: new FormControl('b@x.com') });
    expect(service.matchFieldsValidator('email', 'emailAgain', 'emailMismatch')(emailGroup)).toEqual({ emailMismatch: true });
    expect(service.matchFieldsValidator('missing', 'emailAgain')(emailGroup)).toBeNull();
  });

  it('getLocalDate：回傳 YYYY-MM-DD，可指定偏移天數 (跨月)', () => {
    const base = new Date(2026, 0, 31);

    expect(service.getLocalDate(0, base)).toBe('2026-01-31');
    expect(service.getLocalDate(1, base)).toBe('2026-02-01');
    expect(service.getLocalDate()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('日期年份：必須 4 位數且在範圍內；非日期格式略過', () => {
    const validator = service.fourDigitYearDateValidator();

    expect(check(validator, '2026-05-01')).toBeNull();
    expect(check(validator, '20260-05-01')).toEqual({ invalidYearLength: { required: 4, actual: 5 } });
    expect(check(validator, '1800/05/01')).toEqual({ yearOutOfRange: { min: 1900, max: 2099, actual: 1800 } });
    expect(check(validator, 'not-a-date')).toBeNull();
  });
});
