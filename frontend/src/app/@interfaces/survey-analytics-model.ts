/**
 * ============================================================================
 * 檔案名稱: survey-analytics-model.ts
 * 所屬模組: 問卷統計與數據分析合約模組 (Survey Analytics Domain Model)
 * 責任描述: 定義問卷填答統計總覽、題型統計分析、受訪者輪廓分佈與匯出格式規格；
 *           完整對應 Java Spring Boot / MySQL 之 REST DTO，支援 CSV、Excel、SPSS 格式匯出。
 * 架構分層: Types / Domain Model Layer
 * 聯動關係:
 *   - 上游依賴: ./survey-model.ts
 *   - 下游消費: SurveyAnalyticsService, SurveyManagementComponent
 * 邊界處理: 支援可選題型統計與空資料防呆回退；數值欄位均包含 null 安全型別
 * ============================================================================
 */

// ─────────────────────────────────────────────────────────────────────────────
// 題目層級統計相關型別
// ─────────────────────────────────────────────────────────────────────────────

/** 單選/多選選項統計項目 */
export interface OptionStatItem {
  /** 選項文字標籤 */
  text: string;
  /** 選取票數 */
  count: number;
  /** 佔總回覆之百分比（0-100，保留一位小數） */
  percentage: number;
  /** 較前一期增減趨勢百分點（可選，供對比分析） */
  trendDelta?: number;
}

/** 量表評分分佈統計 (1~10 分區間) */
export interface ScaleDistributionItem {
  /** 分數級距（1 至 10 整數） */
  score: number;
  /** 選取該分數之回答人次 */
  count: number;
  /** 佔該題總回答之百分比（0-100） */
  percentage: number;
}

/** 量表題完整統計彙總 */
export interface ScaleStats {
  /** 平均得分（保留 1 位小數） */
  averageScore: number;
  /** 中位數 */
  medianScore: number;
  /** 眾數（出現最多次之分數級距） */
  modeScore: number;
  /** 最高分 */
  highestScore: number;
  /** 最低分 */
  lowestScore: number;
  /** 標準差 */
  standardDeviation: number;
  /** NPS 凈推薦值 (分數 9-10 推薦者比率 - 1-6 貶損者比率) */
  npsScore?: number;
  /** 每個分級的分佈清單 */
  distribution: ScaleDistributionItem[];
}

/** 文字問答題單筆填答摘錄 */
export interface TextResponseItem {
  /** 唯一識別碼 */
  id: string;
  /** 受訪者名稱（無需填寫身份時為匿名） */
  respondentName?: string;
  /** 填答內容 */
  content: string;
  /** 提交時間字串 */
  submittedAt: string;
  /** 情緒分析標籤（正面/中性/負面，未來 AI 分析用） */
  sentiment?: 'positive' | 'neutral' | 'negative';
}

/** 單一題目完整統計分析物件 */
export interface QuestionAnalyticsItem {
  /** 題目全域唯一識別碼 */
  questionUid: string;
  /** 題目序號（由 1 起算） */
  questionId: number;
  /** 題型分類（radio / checkbox / scale / text / boolean） */
  type: string;
  /** 題目描述文字 */
  description: string;
  /** 題目描述相容欄位 */
  questionText?: string;
  /** 是否為必填題目 */
  isRequired: boolean;
  /** 有效作答人次 */
  totalAnswerCount: number;
  /** 有效作答人次相容欄位 */
  totalResponses?: number;
  /** 未作答（跳過）人次 */
  skippedCount?: number;
  /** 答題率（有效作答 / 總樣本，0-100） */
  responseRate?: number;
  /** 選項統計（單選題、多選題） */
  optionsStats?: OptionStatItem[];
  /** 量表統計（量表題）—含中位數、眾數、NPS */
  scaleStats?: ScaleStats;
  /** 文字填答清單（簡答題） */
  textResponses?: TextResponseItem[];
  /** 文字題字數統計 */
  textStats?: {
    averageLength: number;
    totalCount: number;
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// 受訪者輪廓與時間趨勢
// ─────────────────────────────────────────────────────────────────────────────

/** 受訪者背景資料輪廓分析 */
export interface DemographicStats {
  /** 生理性別分佈 */
  genderDistribution: {
    male: number;
    female: number;
    other: number;
  };
  /** 年齡層區間分佈 */
  ageDistribution: {
    under18: number;
    age18to24: number;
    age25to34: number;
    age35to44: number;
    age45plus: number;
  };
  /** 前五大職業/產業分佈 */
  topJobIndustries: { industry: string; count: number; percentage: number }[];
  /** 填答裝置來源分佈 */
  deviceDistribution?: {
    desktop: number;
    mobile: number;
    tablet: number;
  };
  /** 填答地區分佈（前五） */
  topRegions?: { region: string; count: number; percentage: number }[];
}

/** 問卷時間軸每日填答趨勢項目 */
export interface TimelineSubmissionItem {
  /** 日期標籤（格式 MM-DD） */
  date: string;
  /** 當日完成填答之總筆數 */
  submissionsCount: number;
  /** 當日起始累積筆數（可選，用於繪製累積曲線） */
  cumulativeCount?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// 完整問卷數據分析報表主體 DTO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 完整問卷數據分析報表酬載 (SurveyAnalyticsReport)
 * 未來對接 Java Spring Boot GET /api/surveys/{surveyId}/analytics 之標準 DTO；
 * 支援 JSON / CSV / Excel (XLSX) / SPSS (.sav via CSV 格式) 四種匯出格式。
 */
export interface SurveyAnalyticsReport {
  /** 問卷唯一識別碼 */
  surveyId: string;
  /** 問卷標題 */
  surveyTitle: string;
  /** 問卷分類標籤 */
  category: string;
  /** 發起人 ID */
  authorId: string;
  /** 發起人顯示名稱 */
  authorName?: string;
  /** 是否已公開於大廳 */
  isPublished: boolean;
  /** 填答開放起始日期 */
  startDate?: string;
  /** 填答截止日期 */
  endDate?: string;
  /** 統計報表產生時間 */
  generatedAt?: string;

  // ── 核心 KPI 指標 ──
  /** 總回收樣本數 */
  totalResponses: number;
  /** 作答有效完成率（0-100，含一位小數） */
  completionRate: number;
  /** 平均填答耗時（分鐘，含一位小數） */
  averageDurationMinutes: number;
  /** 最近一筆回覆時間字串 */
  lastResponseAt: string;
  /** 最近 24 小時新增筆數 */
  last24hCount?: number;
  /** 較前一統計週期增長率百分比 */
  growthRate?: number;
  /** 問卷整體 NPS 凈推薦值（-100 至 100） */
  npsScore?: number;

  // ── 填答時間軸趨勢（近 7 日） ──
  timeline: TimelineSubmissionItem[];

  // ── 受訪者背景輪廓 ──
  demographics?: DemographicStats;

  // ── 各題深入分析清單 ──
  questionsAnalytics: QuestionAnalyticsItem[];
}

// ─────────────────────────────────────────────────────────────────────────────
// 匯出格式相關型別定義
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 匯出格式種類列舉
 * JSON  : 完整報表 JSON 資料
 * CSV   : 逗號分隔值，可直接匯入 Excel 或 SPSS
 * EXCEL : Microsoft Excel XLSX 格式（需動態生成 BOM UTF-8）
 * SPSS  : SPSS .sav 相容 CSV（UTF-8 BOM + 特殊欄位命名規範）
 */
export type ExportFormat = 'json' | 'csv' | 'excel' | 'spss';

/** 匯出選項設定 */
export interface ExportOptions {
  /** 匯出格式 */
  format: ExportFormat;
  /** 是否包含填答者原始明細（僅 CSV/EXCEL/SPSS 有效） */
  includeRawResponses?: boolean;
  /** 是否包含受訪者輪廓欄位 */
  includeDemographics?: boolean;
  /** 是否包含各題詳細統計 */
  includeQuestionStats?: boolean;
  /** 檔案語系 ('zh-TW' | 'en') */
  locale?: string;
}
