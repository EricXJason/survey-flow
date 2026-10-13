/**
 * ============================================================================
 * 檔案名稱: survey-model.ts
 * 所屬模組: 問卷調研領域資料合約模組 (Survey Domain Contracts)
 * 責任描述: 定義問卷設計、題目結構、作答填答酬載、大廳摘要與官方標準 12 大分類清單。
 * 架構分層: Types / Domain Model Layer
 * 聯動關係:
 *   - 上游依賴: 無 (純型別與常數合約)
 *   - 下游消費: SurveyGetterService, SurveyStateService, SurveyDesignComponent, SurveyRunnerComponent, LobbyComponent, SurveyManagementComponent
 * 邊界處理: 提供題目 UID 關聯解鎖結構與受訪者背景欄位彈性設定
 * ============================================================================
 */

/**
 * 受訪者背景資料收集與必填欄位配置
 */
export interface SurveyBasicInfoRequirements {
  collectBasicInfo: boolean;
  requireName: boolean;
  requireGender: boolean;
  requireAge: boolean;
  requireBirthday: boolean;
  requireHeight: boolean;
  requireWeight: boolean;
  requireMobilePhone: boolean;
  requireHomePhone: boolean;
  requireEmail: boolean;
  requireRegisteredAddress: boolean;
  requireResidentialAddress: boolean;
  requireDepartment: boolean;
  requireAcademicMajor: boolean;
  requireClassName: boolean;
  requireStudentId: boolean;
  requireEmployeeId: boolean;
  requireNationalId: boolean;
  requireJobIndustry: boolean;
  requireJobTitle: boolean;
}

/**
 * 問卷作答起訖時效限制設定
 */
export interface SurveyTimeLimit {
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
}

/**
 * 問卷選項配置與解鎖關聯邏輯
 */
export interface SurveyOptionItem {
  text: string;
  enableUnlockQuestion: boolean;
  unlockTargetUid: string;
}

/**
 * 單一題目完整設定資料模型
 */
export interface SurveyQuestion {
  uid: string;
  id: number;
  type: string;
  isRequired: boolean;
  hasImage: boolean;
  imageBase64?: string;
  description: string;

  scaleType?: string;

  timeValue?: string;
  hasTimeLimit?: boolean;
  minTime?: string;
  maxTime?: string;

  dateValue?: string;
  hasDateLimit?: boolean;
  minDate?: string;
  maxDate?: string;

  options?: string[];
  optionsConfig?: SurveyOptionItem[];
  isLockedByOther?: boolean;
  lockedBySummary?: string;
}

/**
 * 問卷發布與設計完整酬載模型
 */
export interface SurveyPayload {
  id?: string;
  authorId?: string;
  title: string;
  category: string;
  isPublished: boolean;
  hasTimeLimit: boolean;
  timeLimit?: SurveyTimeLimit;
  hasDescription: boolean;
  description?: string;
  hasDescriptionImage: boolean;
  descriptionImageBase64?: string;
  basicInfoRequirements: SurveyBasicInfoRequirements;
  questions: SurveyQuestion[];
}

/**
 * 單一題目之填答內容紀錄
 */
export interface SurveyAnswerItem {
  questionUid: string;
  questionId: number;
  type: string;
  answer: string | string[] | boolean | number | null;
}

/**
 * 受訪者背景填答資料集
 */
export interface SurveyBasicInfoAnswers {
  name?: string;
  gender?: string;
  age?: number;
  birthday?: string;
  height?: number;
  weight?: number;
  mobilePhone?: string;
  homePhone?: string;
  email?: string;
  registeredAddress?: string;
  residentialAddress?: string;
  department?: string;
  academicMajor?: string;
  className?: string;
  studentId?: string;
  employeeId?: string;
  nationalId?: string;
  jobIndustry?: string;
  jobTitle?: string;
  [key: string]: any;
}

/**
 * 受訪者送出問卷完整作答酬載
 */
export interface SurveySubmissionPayload {
  surveyId?: string | number;
  surveyTitle: string;
  surveyCategory: string;
  submittedAt: string;
  basicInfo: SurveyBasicInfoAnswers;
  answers: SurveyAnswerItem[];
}

/**
 * 問卷大廳與列表顯示之摘要項目
 */
export interface SurveySummaryItem {
  id: string;
  title: string;
  category: string;
  authorId?: string;
  startDate: string;
  endDate: string;
  isPublished?: boolean;
}

/**
 * 官方標準定義之 12 大問卷主題分類清單
 * 涵蓋顧客滿意、產品研究、市場調研、品牌行銷、售後支援、組織人資、活動會後、教育培訓、學術研究、健康生活、公共民意與社群綜合等全方位專業場景。
 */
export const SURVEY_CATEGORIES = [
  '顧客滿意度',
  '產品體驗研究',
  '市場商業調研',
  '品牌行銷與推廣',
  '售後服務與支援',
  '員工與職場文化',
  '活動與會後回饋',
  '教育與培訓評鑑',
  '學術專題研究',
  '健康與生活型態',
  '公共政策與民意',
  '社群互動與綜合',
] as const;

/** 問卷標準主題分類型別 */
export type SurveyCategory = (typeof SURVEY_CATEGORIES)[number];
