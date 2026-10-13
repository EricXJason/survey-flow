/**
 * ============================================================================
 * 檔案名稱: survey-analytics.service.ts
 * 所屬模組: 問卷統計與數據分析服務模組 (Survey Analytics Service Module)
 * 責任描述: 提供問卷填答統計總覽、題型深度統計、受訪者輪廓分佈、
 *           權限核對，以及 CSV / Excel / SPSS / JSON 四種格式匯出功能；
 *           預留接軌 Java Spring Boot + MySQL 之 REST API 介面。
 * 架構分層: Service Layer / Analytics & Aggregation Layer
 * 聯動關係:
 *   - 上游依賴: rxjs, ../@interfaces/survey-analytics-model, ../@interfaces/survey-model, ../datas/surveys.json
 *   - 下游消費: SurveyManagementComponent
 * 邊界處理: 管理員可檢視全站；會員僅能檢視所屬問卷；查無問卷時安全回退至第一筆種子資料
 * ============================================================================
 */

import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import {
  SurveyAnalyticsReport,
  QuestionAnalyticsItem,
  DemographicStats,
  TimelineSubmissionItem,
  ScaleStats,
  ExportFormat,
} from '../@interfaces/survey-analytics-model';
import { SurveyPayload, SurveySummaryItem } from '../@interfaces/survey-model';
import surveys from '../datas/surveys.json';

@Injectable({
  providedIn: 'root',
})
export class SurveyAnalyticsService {

  // ─────────────────────────────────────────────────────────────────────────
  // 公開 API：權限核驗
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 驗證當前使用者是否具備檢視目標問卷數據分析之權限
   * 規範：
   *   1. 系統管理員 (isAdmin = true) 具備全域權限，可檢視全站所有問卷數據。
   *   2. 一般會員僅能檢視自身發起擁有之問卷 (survey.authorId === currentUserId)。
   *
   * @param survey 目標問卷摘要或實體物件
   * @param currentUserId 當前登入者 ID
   * @param isAdmin 當前登入者是否為管理員
   * @returns boolean 是否具備存取權限
   */
  public canAccessAnalytics(
    survey: SurveySummaryItem | SurveyPayload,
    currentUserId: string,
    isAdmin: boolean,
  ): boolean {
    if (isAdmin) {
      return true;
    }
    if (!survey || !currentUserId) {
      return false;
    }
    return survey.authorId === currentUserId;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 公開 API：統計報表查詢
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 取得指定問卷之數據分析統計報表
   * 待 Java 後端 (Spring Boot) 上線後直接對接: GET /api/surveys/{surveyId}/analytics
   *
   * @param surveyId 目標問卷編號
   * @returns Observable<SurveyAnalyticsReport> 數據分析報表酬載
   */
  public getSurveyAnalytics(surveyId: string): Observable<SurveyAnalyticsReport> {
    const rawList = surveys as unknown as (SurveyPayload & {
      id?: string;
      authorId?: string;
    })[];
    const targetSurvey =
      rawList.find((s) => s.id === surveyId) ?? rawList[0];

    const report = this._generateReport(targetSurvey, surveyId);
    return of(report);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 公開 API：多格式匯出
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 匯出問卷數據分析報表為指定格式並觸發瀏覽器下載
   * 支援 JSON / CSV / Excel(UTF-8 BOM) / SPSS(UTF-8 BOM CSV) 四種格式
   *
   * @param report 完整問卷分析報表物件
   * @param format 匯出格式種類
   */
  public exportReport(report: SurveyAnalyticsReport, format: ExportFormat): void {
    switch (format) {
      case 'json':
        this._exportJson(report);
        break;
      case 'csv':
        this._exportCsv(report, false);
        break;
      case 'excel':
        this._exportCsv(report, true, 'excel');
        break;
      case 'spss':
        this._exportCsv(report, true, 'spss');
        break;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 私有：各格式匯出實作
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 匯出 JSON 格式報表
   * @param report 完整報表物件
   */
  private _exportJson(report: SurveyAnalyticsReport): void {
    const dataStr =
      'data:text/json;charset=utf-8,' +
      encodeURIComponent(JSON.stringify(report, null, 2));
    this._triggerDownload(
      dataStr,
      `SurveyFlow_Analytics_${report.surveyId}_${this._getTimestampStr()}.json`,
    );
  }

  /**
   * 匯出 CSV / Excel / SPSS 格式報表（統一以 UTF-8 BOM CSV 底層格式輸出）
   * Excel：加入 UTF-8 BOM (\uFEFF) 以確保 Microsoft Excel 開啟時正確辨識繁體中文編碼。
   * SPSS ：同 Excel BOM CSV，但欄位命名採英文變數名稱以符合 SPSS Variable Name 規範。
   *
   * @param report 完整報表物件
   * @param withBom 是否加入 UTF-8 BOM（Excel / SPSS 必須；純 CSV 可選）
   * @param variant 格式變種 ('excel' | 'spss' | undefined 為一般 CSV)
   */
  private _exportCsv(
    report: SurveyAnalyticsReport,
    withBom: boolean,
    variant?: 'excel' | 'spss',
  ): void {
    const rows: string[][] = [];
    const isSpss = variant === 'spss';

    // ── 區塊一：問卷基本資訊摘要 ──
    if (isSpss) {
      rows.push(['survey_id', 'survey_title', 'category', 'author', 'is_published', 'total_responses', 'completion_rate_pct', 'avg_duration_min', 'nps_score', 'last_response_at', 'generated_at']);
      rows.push([
        report.surveyId,
        report.surveyTitle,
        report.category,
        report.authorName ?? report.authorId,
        report.isPublished ? '1' : '0',
        String(report.totalResponses),
        String(report.completionRate),
        String(report.averageDurationMinutes),
        String(report.npsScore ?? ''),
        report.lastResponseAt,
        report.generatedAt ?? '',
      ]);
    } else {
      rows.push(['問卷基本資訊', '']);
      rows.push(['問卷編號', report.surveyId]);
      rows.push(['問卷名稱', report.surveyTitle]);
      rows.push(['分類', report.category]);
      rows.push(['發起人', report.authorName ?? report.authorId]);
      rows.push(['公開狀態', report.isPublished ? '已公開' : '私人']);
      rows.push(['總回收樣本', String(report.totalResponses)]);
      rows.push(['有效完成率(%)', String(report.completionRate)]);
      rows.push(['平均填答耗時(分)', String(report.averageDurationMinutes)]);
      rows.push(['NPS 凈推薦值', String(report.npsScore ?? '—')]);
      rows.push(['最後回覆時間', report.lastResponseAt]);
      rows.push(['報表產生時間', report.generatedAt ?? new Date().toLocaleString('zh-TW')]);
      rows.push([]);
    }

    // ── 區塊二：近 7 日每日填答趨勢 ──
    if (!isSpss) {
      rows.push(['近 7 日每日填答趨勢', '']);
      rows.push(['日期', '填答筆數', '累計筆數']);
    } else {
      rows.push([]);
      rows.push(['timeline_date', 'daily_submissions', 'cumulative_count']);
    }
    report.timeline.forEach((t) => {
      rows.push([t.date, String(t.submissionsCount), String(t.cumulativeCount ?? '')]);
    });
    rows.push([]);

    // ── 區塊三：受訪者輪廓 ──
    if (report.demographics) {
      const demo = report.demographics;
      if (!isSpss) {
        rows.push(['受訪者背景輪廓', '']);
        rows.push(['性別', '人數', '百分比(%)']);
        rows.push(['男性', String(demo.genderDistribution.male), ((demo.genderDistribution.male / report.totalResponses) * 100).toFixed(1)]);
        rows.push(['女性', String(demo.genderDistribution.female), ((demo.genderDistribution.female / report.totalResponses) * 100).toFixed(1)]);
        rows.push(['其他/不透露', String(demo.genderDistribution.other), ((demo.genderDistribution.other / report.totalResponses) * 100).toFixed(1)]);
        rows.push([]);
        rows.push(['年齡層', '人數']);
        rows.push(['18 歲以下', String(demo.ageDistribution.under18)]);
        rows.push(['18-24 歲', String(demo.ageDistribution.age18to24)]);
        rows.push(['25-34 歲', String(demo.ageDistribution.age25to34)]);
        rows.push(['35-44 歲', String(demo.ageDistribution.age35to44)]);
        rows.push(['45 歲以上', String(demo.ageDistribution.age45plus)]);
        rows.push([]);
        if (demo.topJobIndustries?.length) {
          rows.push(['主要職業/產業', '人數', '百分比(%)']);
          demo.topJobIndustries.forEach((j) => {
            rows.push([j.industry, String(j.count), String(j.percentage)]);
          });
          rows.push([]);
        }
        if (demo.deviceDistribution) {
          rows.push(['填答裝置', '人數']);
          rows.push(['電腦', String(demo.deviceDistribution.desktop)]);
          rows.push(['手機', String(demo.deviceDistribution.mobile)]);
          rows.push(['平板', String(demo.deviceDistribution.tablet)]);
          rows.push([]);
        }
      } else {
        // SPSS 輪廓欄位攤平成單列
        rows.push(['gender_male', 'gender_female', 'gender_other',
          'age_under18', 'age_18to24', 'age_25to34', 'age_35to44', 'age_45plus',
          'device_desktop', 'device_mobile', 'device_tablet']);
        rows.push([
          String(demo.genderDistribution.male), String(demo.genderDistribution.female), String(demo.genderDistribution.other),
          String(demo.ageDistribution.under18), String(demo.ageDistribution.age18to24), String(demo.ageDistribution.age25to34),
          String(demo.ageDistribution.age35to44), String(demo.ageDistribution.age45plus),
          String(demo.deviceDistribution?.desktop ?? ''), String(demo.deviceDistribution?.mobile ?? ''), String(demo.deviceDistribution?.tablet ?? ''),
        ]);
        rows.push([]);
      }
    }

    // ── 區塊四：各題統計明細 ──
    report.questionsAnalytics.forEach((q, idx) => {
      if (!isSpss) {
        rows.push([`Q${idx + 1}. ${q.description}`, '', '', '']);
        rows.push([`題型：${this._typeLabel(q.type)}`, `必填：${q.isRequired ? '是' : '否'}`, `有效作答：${q.totalAnswerCount}`, `跳過：${q.skippedCount ?? 0}`]);

        if (q.optionsStats?.length) {
          rows.push(['選項', '票數', '百分比(%)']);
          q.optionsStats.forEach((o) => rows.push([o.text, String(o.count), String(o.percentage)]));
        }

        if (q.scaleStats) {
          const s = q.scaleStats;
          rows.push(['量表指標', '數值']);
          rows.push(['平均分', String(s.averageScore)]);
          rows.push(['中位數', String(s.medianScore)]);
          rows.push(['眾數', String(s.modeScore)]);
          rows.push(['標準差', String(s.standardDeviation)]);
          rows.push(['NPS 凈推薦值', String(s.npsScore ?? '—')]);
          rows.push([]);
          rows.push(['分數', '人次', '百分比(%)']);
          s.distribution.forEach((d) => rows.push([String(d.score), String(d.count), String(d.percentage)]));
        }

        if (q.textResponses?.length) {
          rows.push(['受訪者', '填答時間', '內容']);
          q.textResponses.forEach((t) =>
            rows.push([t.respondentName ?? '匿名', t.submittedAt, t.content]),
          );
        }
        rows.push([]);
      } else {
        // SPSS：每題選項攤平
        const header = [`q${idx + 1}_uid`, `q${idx + 1}_type`, `q${idx + 1}_n`, `q${idx + 1}_skip`];
        const values: string[] = [q.questionUid, q.type, String(q.totalAnswerCount), String(q.skippedCount ?? 0)];

        if (q.optionsStats?.length) {
          q.optionsStats.forEach((o, oi) => {
            header.push(`q${idx + 1}_opt${oi + 1}_text`, `q${idx + 1}_opt${oi + 1}_n`, `q${idx + 1}_opt${oi + 1}_pct`);
            values.push(o.text, String(o.count), String(o.percentage));
          });
        }

        if (q.scaleStats) {
          header.push(`q${idx + 1}_avg`, `q${idx + 1}_median`, `q${idx + 1}_mode`, `q${idx + 1}_sd`, `q${idx + 1}_nps`);
          values.push(
            String(q.scaleStats.averageScore), String(q.scaleStats.medianScore),
            String(q.scaleStats.modeScore), String(q.scaleStats.standardDeviation),
            String(q.scaleStats.npsScore ?? ''),
          );
        }

        if (idx === 0) rows.push(header);
        rows.push(values);
      }
    });

    // ── 轉換 rows → CSV 字串 ──
    const csvContent = rows
      .map((row) =>
        row
          .map((cell) => {
            const str = String(cell ?? '');
            // [Why]: 含有逗號、換行或引號之儲存格必須以雙引號包覆；內部雙引號轉義為 ""
            if (str.includes(',') || str.includes('\n') || str.includes('"')) {
              return `"${str.replace(/"/g, '""')}"`;
            }
            return str;
          })
          .join(','),
      )
      .join('\r\n');

    // [Why]: UTF-8 BOM (\uFEFF) 確保 Excel 正確辨識 UTF-8 編碼中文，避免亂碼
    const bom = withBom ? '\uFEFF' : '';
    const mimeType = 'text/csv;charset=utf-8;';
    const blob = new Blob([bom + csvContent], { type: mimeType });
    const url = URL.createObjectURL(blob);

    const ext = variant === 'spss' ? 'spss_compatible.csv' : 'csv';
    const filename = `SurveyFlow_${variant?.toUpperCase() ?? 'CSV'}_${report.surveyId}_${this._getTimestampStr()}.${ext}`;
    this._triggerDownloadBlob(url, filename);
  }

  /**
   * 觸發瀏覽器下載 (data URI 方式，適用於 JSON)
   * @param dataUri data: URI 字串
   * @param filename 下載後的檔案名稱
   */
  private _triggerDownload(dataUri: string, filename: string): void {
    const a = document.createElement('a');
    a.setAttribute('href', dataUri);
    a.setAttribute('download', filename);
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  /**
   * 觸發瀏覽器下載 (Blob URL 方式，適用於 CSV/Excel/SPSS)
   * @param blobUrl Blob.createObjectURL 產生之 URL
   * @param filename 下載後的檔案名稱
   */
  private _triggerDownloadBlob(blobUrl: string, filename: string): void {
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    // [Why]: 釋放 Blob URL 以避免記憶體洩漏
    setTimeout(() => URL.revokeObjectURL(blobUrl), 5000);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 私有：數據報表聚合生成
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 內部聚合方法：依據問卷結構產生標準化數據分析報告（前端模擬資料）
   * 未來後端 Spring Boot 透過 MySQL COUNT(*), AVG(), STDDEV(), GROUP BY 聚合輸出完全一致之資料結構
   *
   * @param survey 問卷實體資料
   * @param surveyId 問卷 ID
   * @returns SurveyAnalyticsReport 完整統計報表
   */
  private _generateReport(
    survey: SurveyPayload & { id?: string; authorId?: string },
    surveyId: string,
  ): SurveyAnalyticsReport {
    const totalResponses = 128;
    const completionRate = 96.8;
    const avgDuration = 3.6;
    const npsScore = 48;

    // ── 各題統計分析 ──
    const questionsAnalytics: QuestionAnalyticsItem[] = (
      survey.questions ?? []
    ).map((q, idx) => {
      const qType = q.type || 'radio';

      // (A) 單選題 / 是非題
      if (qType === 'radio' || qType === 'boolean') {
        const rawOptions =
          q.options && q.options.length > 0
            ? q.options
            : ['非常滿意', '滿意', '普通', '不滿意', '非常不滿意'];
        const counts = this._distributeCounts(totalResponses, rawOptions.length);
        const optionsStats = rawOptions.map((text, oIdx) => ({
          text,
          count: counts[oIdx],
          percentage: Math.round((counts[oIdx] / totalResponses) * 1000) / 10,
          trendDelta: Math.round((Math.random() - 0.5) * 8 * 10) / 10,
        }));
        const skipped = Math.round(totalResponses * 0.02);

        return {
          questionUid: q.uid || `q_${idx}`,
          questionId: q.id || idx + 1,
          type: qType,
          description: q.description,
          isRequired: q.isRequired ?? true,
          totalAnswerCount: totalResponses - skipped,
          skippedCount: skipped,
          responseRate: Math.round(((totalResponses - skipped) / totalResponses) * 1000) / 10,
          optionsStats,
        };
      }

      // (B) 多選題
      if (qType === 'checkbox') {
        const rawOptions =
          q.options && q.options.length > 0
            ? q.options
            : ['功能豐富完整', '介面設計美觀', '操作流程順暢', '跨裝置支援良好', '資料安全可靠'];
        const baseCounts = this._distributeCounts(totalResponses * 1.8, rawOptions.length);
        const optionsStats = rawOptions.map((text, oIdx) => ({
          text,
          count: baseCounts[oIdx],
          percentage: Math.min(
            100,
            Math.round((baseCounts[oIdx] / totalResponses) * 1000) / 10,
          ),
          trendDelta: Math.round((Math.random() - 0.5) * 6 * 10) / 10,
        }));

        return {
          questionUid: q.uid || `q_${idx}`,
          questionId: q.id || idx + 1,
          type: qType,
          description: q.description,
          isRequired: q.isRequired ?? true,
          totalAnswerCount: totalResponses,
          skippedCount: 0,
          responseRate: 100,
          optionsStats,
        };
      }

      // (C) 量表題：含完整描述性統計 + NPS
      if (qType === 'scale') {
        const scores = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
        // [Why]: 偏向高滿意度的逼真常態分佈權重，模擬真實問卷得分分佈
        const weights = [1, 1, 2, 4, 8, 14, 22, 36, 26, 14];
        const scaleDistribution = scores.map((score, sIdx) => ({
          score,
          count: weights[sIdx],
          percentage: Math.round((weights[sIdx] / totalResponses) * 1000) / 10,
        }));

        const scaleStats: ScaleStats = {
          averageScore: 7.8,
          medianScore: 8.0,
          modeScore: 8,
          highestScore: 10,
          lowestScore: 1,
          standardDeviation: 1.42,
          // [Why]: NPS = 推薦者(9-10)% - 貶損者(1-6)%
          npsScore: Math.round(
            ((weights[8] + weights[9]) / totalResponses - (weights.slice(0, 6).reduce((a, b) => a + b, 0)) / totalResponses) * 100
          ),
          distribution: scaleDistribution,
        };

        return {
          questionUid: q.uid || `q_${idx}`,
          questionId: q.id || idx + 1,
          type: qType,
          description: q.description,
          isRequired: q.isRequired ?? true,
          totalAnswerCount: totalResponses,
          skippedCount: 0,
          responseRate: 100,
          scaleStats,
        };
      }

      // (D) 文字題：含字數統計
      return {
        questionUid: q.uid || `q_${idx}`,
        questionId: q.id || idx + 1,
        type: qType,
        description: q.description,
        isRequired: q.isRequired ?? false,
        totalAnswerCount: 94,
        skippedCount: 34,
        responseRate: Math.round((94 / totalResponses) * 1000) / 10,
        textStats: {
          averageLength: 42,
          totalCount: 94,
        },
        textResponses: [
          {
            id: 'txt_01',
            respondentName: '林小姐',
            content: '介面設計非常直覺，深色科技風格對比極佳，填寫體驗非常流暢！問卷題型多元完整。',
            submittedAt: '2026-10-05 14:32',
            sentiment: 'positive',
          },
          {
            id: 'txt_02',
            respondentName: '張工程師',
            content: '拖曳順序與即時跳題解鎖非常驚艷，希望未來能開放匯出 Excel 及 SPSS 報表供學術分析使用。',
            submittedAt: '2026-10-05 16:15',
            sentiment: 'positive',
          },
          {
            id: 'txt_03',
            respondentName: '王經理',
            content: '行動端體驗十分順手，身分證字號雙向校驗能有效過濾無效填答，大幅提升資料品質。',
            submittedAt: '2026-10-06 09:41',
            sentiment: 'positive',
          },
          {
            id: 'txt_04',
            respondentName: '陳研究員',
            content: '數據統計分佈一目了然，量表評分分佈清楚呈現受訪者核心傾向，後續分析非常方便。',
            submittedAt: '2026-10-06 10:20',
            sentiment: 'positive',
          },
          {
            id: 'txt_05',
            respondentName: '李同學',
            content: '整體功能完整，部分頁面在舊型平板顯示時有些許排版擠壓，建議優化一下。',
            submittedAt: '2026-10-06 10:55',
            sentiment: 'neutral',
          },
        ],
      };
    });

    // ── 近 7 日填答趨勢（含累計欄） ──
    const rawTimeline = [
      { date: '09-29', submissionsCount: 8 },
      { date: '09-30', submissionsCount: 14 },
      { date: '10-01', submissionsCount: 19 },
      { date: '10-02', submissionsCount: 25 },
      { date: '10-03', submissionsCount: 22 },
      { date: '10-04', submissionsCount: 18 },
      { date: '10-05', submissionsCount: 22 },
    ];
    let cumulative = 0;
    const timeline: TimelineSubmissionItem[] = rawTimeline.map((t) => {
      cumulative += t.submissionsCount;
      return { ...t, cumulativeCount: cumulative };
    });

    // ── 受訪者背景輪廓 ──
    const demographics: DemographicStats = {
      genderDistribution: { male: 62, female: 58, other: 8 },
      ageDistribution: {
        under18: 6,
        age18to24: 38,
        age25to34: 52,
        age35to44: 24,
        age45plus: 8,
      },
      topJobIndustries: [
        { industry: '資訊科技 / 軟體工程', count: 48, percentage: 37.5 },
        { industry: '金融保險 / 商業顧問', count: 28, percentage: 21.9 },
        { industry: '教育學術 / 大專院校', count: 24, percentage: 18.8 },
        { industry: '醫療生技 / 醫事護理', count: 16, percentage: 12.5 },
        { industry: '其他各專業服務領域', count: 12, percentage: 9.4 },
      ],
      deviceDistribution: { desktop: 74, mobile: 46, tablet: 8 },
      topRegions: [
        { region: '臺北市', count: 42, percentage: 32.8 },
        { region: '新北市', count: 28, percentage: 21.9 },
        { region: '桃園市', count: 18, percentage: 14.1 },
        { region: '臺中市', count: 24, percentage: 18.8 },
        { region: '高雄市', count: 16, percentage: 12.5 },
      ],
    };

    return {
      surveyId,
      surveyTitle: survey.title,
      category: survey.category,
      authorId: survey.authorId || '00001',
      authorName: survey.authorId === '11111' ? '系統管理員' : '陳小明',
      isPublished: survey.isPublished,
      startDate: survey.timeLimit?.startDate,
      endDate: survey.timeLimit?.endDate,
      generatedAt: new Date().toLocaleString('zh-TW', { hour12: false }),
      totalResponses,
      completionRate,
      averageDurationMinutes: avgDuration,
      lastResponseAt: '2026-10-06 10:55',
      last24hCount: 14,
      growthRate: 8.3,
      npsScore,
      timeline,
      demographics,
      questionsAnalytics,
    };
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 私有：輔助工具方法
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 將總數隨機分配至 N 個項目中，維持總和守恆
   * @param total 分配總數
   * @param parts 分組數量
   * @returns 各分組整數數量陣列
   */
  private _distributeCounts(total: number, parts: number): number[] {
    if (parts <= 1) return [Math.round(total)];
    const weights = Array.from({ length: parts }, () => Math.random() + 0.5);
    const sumWeights = weights.reduce((acc, cur) => acc + cur, 0);
    return weights.map((w) => Math.round((w / sumWeights) * total));
  }

  /**
   * 取得當前時間字串，用於匯出檔名（格式：YYYYMMDD_HHmmss）
   * @returns 時間戳字串
   */
  private _getTimestampStr(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
  }

  /**
   * 題型中文標籤轉換
   * @param type 題型英文識別碼
   * @returns 對應的繁體中文標籤
   */
  private _typeLabel(type: string): string {
    const map: Record<string, string> = {
      radio: '單選題',
      checkbox: '多選題',
      scale: '量表題',
      text: '簡答題',
      boolean: '是非題',
    };
    return map[type] ?? type;
  }
}
