/**
 * ============================================================================
 * 檔案名稱: account-model.ts
 * 所屬模組: 會員與帳號領域資料合約模組 (Account Domain Contracts)
 * 責任描述: 定義會員身分、登入、註冊、個人資料修改等請求載荷，以及後端認證回應之前端型別。
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
}

/** 修改個人資料請求資料載荷 */
export interface AccountModifyProfilePayload {
  /** 目前密碼 (僅變更密碼時帶入，供後端核對身分) */
  currentPassword?: string;
  /** 新密碼 (僅變更密碼時帶入) */
  password?: string;
  name: string;
  nationalId: string;
  phone: string;
}

/**
 * 後端認證回應 (AuthResponse) 之前端型別
 * 由登入、信箱驗證與 Token 換發端點回傳；Refresh Token 僅以 HttpOnly Cookie 下發，不出現於此
 */
export interface AuthResponseData {
  accessToken?: string;
  token?: string;
  userId: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string;
  identityCard?: string;
  role: string;
}
