/**
 * ============================================================================
 * 檔案名稱: account-model.ts
 * 所屬模組: 會員與帳號領域資料合約模組 (Account Domain Contracts)
 * 責任描述: 定義會員身分、登入登出、註冊與個人資料修改等請求載荷及資料結構介面。
 * 架構分層: Types / Domain Model Layer
 * 聯動關係:
 *   - 上游依賴: 無 (純型別合約)
 *   - 下游消費: UserStateService, UserEmitterService, LogInComponent, RegisterComponent, UserProfileComponent, UserManagementComponent
 * 邊界處理: 提供不可為空之嚴格型別約束與可選屬性定義
 * ============================================================================
 */

/** 會員登入請求資料載荷 */
export interface AccountLogInPayload {
  username: string;
  password: string;
}

/** 會員註冊請求資料載荷 */
export interface AccountRegisterPayload {
  username: string;
  password: string;
  name: string;
  nationalId: string;
  email: string;
  phone: string;
  gender: string;
  birthday: string;
  role: string;
}

/** 忘記密碼請求資料載荷 */
export interface AccountForgotPasswordPayload {
  username: string;
  email: string;
}

/** 使用者個人資料模型 */
export interface UserProfileData {
  id: string;
  access: string;
  userName: string;
  name: string;
  nationalId: string;
  email: string;
  phone: string;
  gender: string;
  birthday: string;
  role: string;
}

/** 修改個人資料請求資料載荷 */
export interface AccountModifyProfilePayload {
  password?: string;
  name: string;
  nationalId: string;
  phone: string;
  gender: string;
  birthday: string;
  role: string;
}
