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

/** 身分職業選項定義介面 */
export interface CareerOptionItem {
  value: string;
  label: string;
}

/**
 * 全域身分職業標準選項清單 (SSOT 單一真實來源)
 * 涵蓋學術教育、資訊科技、工商業、醫護專門職業、服務零售、自由自媒體與多元生活狀態
 */
export const CAREER_OPTIONS: readonly CareerOptionItem[] = [
  { value: 'student', label: '學生 (大專院校 / 碩博研究生 / 中學)' },
  { value: 'teacher', label: '教師 / 教授 / 各級教育工作者' },
  { value: 'researcher', label: '學術研究人員 / 智庫學者 / 科學家' },
  { value: 'it_engineer', label: '資訊科技 / 軟體工程師 / AI 架構師' },
  { value: 'semiconductor', label: '半導體 / 電子製造 / 硬體研發工程師' },
  { value: 'employee', label: '一般上班族 / 企業行政與專案企劃' },
  { value: 'finance', label: '金融保險 / 證券投資 / 財會審計' },
  { value: 'marketing', label: '市場行銷 / 公關廣告 / 品牌營運' },
  { value: 'executive', label: '高階主管 / 企業負責人 / 創業家' },
  { value: 'healthcare', label: '醫療生技 (醫師 / 護理師 / 藥師 / 醫事人員)' },
  { value: 'legal_pro', label: '專門職業 (律師 / 會計師 / 建築師 / 顧問)' },
  { value: 'civil_service', label: '公務人員 / 軍憲警消 / 國營事業' },
  { value: 'retail_service', label: '零售門市 / 批發物流 / 電商從業人員' },
  { value: 'hospitality', label: '餐飲服務 / 觀光休閒 / 航空旅宿' },
  { value: 'freelancer', label: '自由工作者 / 接案設計師 / 獨立顧問' },
  { value: 'creator', label: '文化創意 / 影音自媒體創作者 / 藝術家' },
  { value: 'homemaker', label: '家庭主理人 / 全職家管' },
  { value: 'retired', label: '已退休人員' },
  { value: 'seeking', label: '待業中 / 尋職轉換中' },
  { value: 'other', label: '其他職業領域' },
] as const;

/**
 * 職業代碼對應臺灣繁體中文精簡標籤映射表 (供導覽列或卡片徽章展示)
 */
export const CAREER_LABEL_MAP: Record<string, string> = {
  student: '學生',
  teacher: '教師',
  researcher: '研究人員',
  it_engineer: '軟體工程師',
  semiconductor: '半導體工程師',
  employee: '上班族',
  finance: '金融保險',
  marketing: '行銷公關',
  executive: '企業主管',
  healthcare: '醫療人員',
  legal_pro: '專門職業',
  civil_service: '公務人員',
  retail_service: '零售電商',
  hospitality: '餐飲觀光',
  freelancer: '自由工作者',
  creator: '自媒體創作者',
  homemaker: '家庭主理人',
  retired: '已退休',
  seeking: '待業轉換中',
  other: '其他職業',
  '學生': '學生',
  '教師': '教師',
  '研究人員': '研究人員',
  '上班族': '上班族',
  '已退休': '已退休',
  '其他': '其他職業',
  member: '一般會員',
  admin: '系統管理員',
  user: '一般會員',
};

