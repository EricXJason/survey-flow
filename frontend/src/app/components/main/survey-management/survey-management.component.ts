/**
 * ============================================================================
 * 檔案名稱: survey-management.component.ts
 * 所屬模組: 問卷管理後台模組 (Survey Management Module)
 * 責任描述: 提供會員與管理員管理問卷專案（個人或全站）、Material Table 分頁排序、
 *           即時搜尋、公開/私有過濾、問卷預覽、數據統計儀表板檢視，
 *           以及 JSON / CSV / Excel / SPSS 四種格式報表匯出操作。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: SurveyGetterService, SurveyStateService, UserStateService,
 *               UserEmitterService, DialogService, SurveyAnalyticsService
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 依身分權限切換全站與個人問卷查詢 API；刪除時經雙重確認對話框防呆；
 *           越權存取數據分析時即時阻斷並彈出錯誤提示
 * ============================================================================
 */

import {
  AfterViewInit,
  Component,
  computed,
  effect,
  HostListener,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { SurveyGetterService } from '../../../@services/survey-getter.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { UserStateService } from '../../../@services/user-state.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { SurveyAnalyticsService } from '../../../@services/survey-analytics.service';
import { SurveySummaryItem } from '../../../@interfaces/survey-model';
import { SurveyAnalyticsReport, ExportFormat } from '../../../@interfaces/survey-analytics-model';
import { QuestionTypeCountPipe } from '../../../pipes/question-type-count.pipe';

/**
 * 問卷管理與調研專案後台組件 (SurveyManagementComponent)
 * 供登入會員與管理員管理自己發起或全站的問卷，具備 Material Table 分頁清單、
 * 關鍵字即時搜尋、公開/私有狀態過濾、問卷預覽、數據統計儀表板與多格式報表匯出。
 */
@Component({
  selector: 'app-survey-management',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    QuestionTypeCountPipe,
  ],
  templateUrl: './survey-management.component.html',
  styleUrl: './survey-management.component.scss',
})
export class SurveyManagementComponent implements OnInit, AfterViewInit {

  // ─────────────────────────────────────────────────────────────────────────
  // 注入依賴
  // ─────────────────────────────────────────────────────────────────────────

  /** 問卷查詢服務 */
  private readonly _surveyGetter = inject(SurveyGetterService);
  /** 問卷狀態管理服務（提供進入填答/預覽模式） */
  private readonly _surveyState = inject(SurveyStateService);
  /** 使用者身分與狀態管理服務 */
  private readonly _userState = inject(UserStateService);
  /** 使用者事件廣播服務（如刪除確認回呼） */
  private readonly _userEmitter = inject(UserEmitterService);
  /** 系統對話框管理服務 */
  private readonly _dialogService = inject(DialogService);
  /** 問卷數據分析統計服務 */
  private readonly _analyticsService = inject(SurveyAnalyticsService);
  /** Angular 路由跳轉器 */
  private readonly _router = inject(Router);

  // ─────────────────────────────────────────────────────────────────────────
  // 視圖狀態信號
  // ─────────────────────────────────────────────────────────────────────────

  /** 當前視圖模式信號 ('list' 問卷列表 | 'analytics' 數據分析儀表板) */
  public readonly activeView = signal<'list' | 'analytics'>('list');
  /** 當前載入之問卷數據分析報表信號 */
  public readonly currentAnalyticsReport = signal<SurveyAnalyticsReport | null>(null);
  /** 數據分析載入中狀態信號 */
  public readonly isAnalyticsLoading = signal<boolean>(false);

  /** 匯出下拉選單展開狀態信號 */
  public readonly isExportMenuOpen = signal<boolean>(false);
  /** 當前處於數據分析中的問卷 ID 信號（用於高亮列表行） */
  public readonly currentAnalyticsSurveyId = signal<string | null>(null);

  // ─────────────────────────────────────────────────────────────────────────
  // 問卷列表狀態信號
  // ─────────────────────────────────────────────────────────────────────────

  /** 問卷資料清單信號 */
  public readonly surveyList = signal<SurveySummaryItem[]>([]);
  /** 載入中狀態旗標信號 */
  public readonly isLoading = signal<boolean>(true);
  /** 當前使用者是否為管理員身分 */
  public readonly isAdmin = signal<boolean>(false);

  /** 搜尋關鍵字信號 */
  public readonly searchQuery = signal<string>('');
  /** 問卷發布狀態篩選條件信號（'all' | 'published' | 'private'） */
  public readonly activeFilter = signal<'all' | 'published' | 'private'>('all');

  /** 時限與雙狀態設定彈窗狀態信號 */
  public readonly isSettingsModalOpen = signal<boolean>(false);
  public readonly currentEditingSurvey = signal<SurveySummaryItem | null>(null);
  public readonly settingsStartDate = signal<string>('');
  public readonly settingsEndDate = signal<string>('');
  public readonly settingsIsPublicToLobby = signal<boolean>(true);
  public readonly settingsIsOpen = signal<boolean>(true);

  // ─────────────────────────────────────────────────────────────────────────
  // Material Table 配置
  // ─────────────────────────────────────────────────────────────────────────

  /** Angular Material 表格欄位清單 */
  public readonly displayedColumns: string[] = [
    'id',
    'title',
    'category',
    'timeRange',
    'status',
    'actions',
  ];

  /** Material Table 專用資料來源執行個體 */
  public readonly dataSource = new MatTableDataSource<SurveySummaryItem>([]);

  /** Material 分頁器引用 */
  @ViewChild(MatPaginator) public paginator!: MatPaginator;
  /** Material 欄位排序器引用 */
  @ViewChild(MatSort) public sort!: MatSort;

  // ─────────────────────────────────────────────────────────────────────────
  // 計算屬性
  // ─────────────────────────────────────────────────────────────────────────

  /** 計算屬性：總問卷數量 */
  public readonly totalCount = computed(() => this.surveyList().length);
  /** 計算屬性：已公開發布之問卷數量 */
  public readonly publishedCount = computed(
    () => this.surveyList().filter((s) => s.isPublished).length,
  );
  /** 計算屬性：未公開（草稿/私密）之問卷數量 */
  public readonly privateCount = computed(
    () => this.surveyList().filter((s) => !s.isPublished).length,
  );

  /**
   * 計算屬性：根據發布狀態篩選與關鍵字過濾後所產生的顯示清單
   */
  public readonly displaySurveys = computed(() => {
    let list = this.surveyList();
    const filter = this.activeFilter();
    if (filter === 'published') {
      list = list.filter((s) => s.isPublished);
    } else if (filter === 'private') {
      list = list.filter((s) => !s.isPublished);
    }
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      list = list.filter(
        (s) =>
          s.title.toLowerCase().includes(q) ||
          s.id.toLowerCase().includes(q) ||
          s.category.toLowerCase().includes(q),
      );
    }
    return list;
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 建構與生命週期
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 建構函式：監聽 displaySurveys 變化 (effect)，自動更新 Material Table 並回到第一頁
   */
  constructor() {
    effect(() => {
      const data = this.displaySurveys();
      this.dataSource.data = data;
      if (this.paginator) {
        this.paginator.firstPage();
      }
    });
  }

  /**
   * Angular 生命週期鉤子 (ngOnInit)：檢驗當前使用者身分權限並載入問卷清單
   */
  public ngOnInit(): void {
    const currentAccess = this._userState.access();
    this.isAdmin.set(currentAccess === 'admin');
    this.fetchSurveys();
  }

  /**
   * Angular 生命週期鉤子 (ngAfterViewInit)：完成視圖渲染後綁定 Material 分頁器與排序器
   */
  public ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 全域事件監聽
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 監聽全域點擊事件，當點擊匯出下拉面板外部區域時自動收起面板
   * @param event 原生滑鼠點擊事件
   */
  @HostListener('document:click', ['$event'])
  public onDocumentClick(event: Event): void {
    const target = event.target as HTMLElement;
    // [Why]: 若點擊目標不在 .export-dropdown-wrap 內，自動收起匯出選單
    if (!target.closest('.export-dropdown-wrap')) {
      this.isExportMenuOpen.set(false);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 問卷列表操作方法
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 設定問卷發布狀態過濾條件
   * @param filter 目標過濾標籤 ('all' | 'published' | 'private')
   */
  public setFilter(filter: 'all' | 'published' | 'private'): void {
    this.activeFilter.set(filter);
  }

  /**
   * 處理搜尋輸入框內容變更事件
   * @param event 原生 Input 事件物件
   */
  public onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.searchQuery.set(input.value);
  }

  /**
   * 清除搜尋輸入文字，重設問卷列表展示
   */
  public clearSearch(): void {
    this.searchQuery.set('');
  }

  /**
   * 依使用者權限向服務層請求問卷清單
   * 若為管理員則取得全站所有問卷；一般會員則僅取得自己發起的問卷。
   */
  public fetchSurveys(): void {
    this.isLoading.set(true);

    if (this.isAdmin()) {
      this._surveyGetter.getAllSurveys().subscribe({
        next: (surveys) => {
          this.surveyList.set(surveys);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        },
      });
      return;
    }

    const userId = this._userState.id();
    this._surveyGetter.getMemberSurveys(userId).subscribe({
      next: (surveys) => {
        this.surveyList.set(surveys);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      },
    });
  }

  /**
   * 預覽指定問卷
   * 載入完整問卷資料後，將模式設定為受訪者預覽模式並導向填答器展示。
   * @param surveyId 目標問卷 ID
   */
  public onPreviewSurvey(surveyId: string): void {
    this._surveyGetter.getSurveyById(surveyId).subscribe((fullSurvey) => {
      if (!fullSurvey) {
        return;
      }
      this._surveyState.setForPreviewMode(fullSurvey);
      this._router.navigateByUrl('/survey-runner');
    });
  }

  /**
   * 開啟問卷數據統計儀表板
   * 實作細粒度權限安全守衛：
   *   1. 系統管理員可檢視全站所有問卷數據。
   *   2. 一般會員僅能檢視所屬自己建立之問卷，若越權存取則安全攔截阻斷。
   * @param survey 目標問卷資料摘要物件
   */
  public onViewAnalytics(survey: SurveySummaryItem): void {
    const currentUserId = this._userState.id();
    const isAdmin = this.isAdmin();

    const hasAccess = this._analyticsService.canAccessAnalytics(
      survey,
      currentUserId,
      isAdmin,
    );

    if (!hasAccess) {
      const errorData: DialogData = {
        title: '權限不足',
        message: `您目前的身分無權查閱問卷「${survey.title}」的詳細填答數據。僅該問卷發起人與系統管理員具備分析權限。`,
        mode: 'errorMode',
        confirmText: '我知道了',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

    this.isAnalyticsLoading.set(true);
    this.currentAnalyticsSurveyId.set(survey.id);
    this._analyticsService.getSurveyAnalytics(survey.id).subscribe({
      next: (report) => {
        this.currentAnalyticsReport.set(report);
        this.activeView.set('analytics');
        this.isAnalyticsLoading.set(false);
        // 切換至分析視圖後置頂
        setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
      },
      error: () => {
        this.isAnalyticsLoading.set(false);
        this.currentAnalyticsSurveyId.set(null);
      },
    });
  }

  /**
   * 自數據分析儀表板返回問卷管理清單視圖
   */
  public onBackToList(): void {
    this.activeView.set('list');
    this.currentAnalyticsReport.set(null);
    this.currentAnalyticsSurveyId.set(null);
    this.isExportMenuOpen.set(false);
  }

  /**
   * 刪除指定問卷操作
   * 透過 UserEmitterService 觸發全域二次確認對話框，確認後自前端列表中移除該筆紀錄。
   * @param survey 目標刪除的問卷物件
   */
  public onDeleteSurvey(survey: SurveySummaryItem): void {
    this._userEmitter.emitDeleteSurvey(survey.id, survey.title, () => {
      this.surveyList.update((list) =>
        list.filter((item) => item.id !== survey.id),
      );
    });
  }

  /**
   * 複製專屬填答連結至剪貼簿
   * 不論公開或私人未發布問卷，均可取得填答專屬網址分享給他人作答
   * @param survey 目標問卷資料
   */
  public onCopyShareLink(survey: SurveySummaryItem): void {
    const shareUrl = `${window.location.origin}/survey-runner?surveyId=${survey.id}`;
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(shareUrl).then(() => {
        const data: DialogData = {
          title: '作答連結已複製',
          message: `問卷「${survey.title}」之專屬填答連結已成功複製至剪貼簿！\n\n🔗 連結網址：\n${shareUrl}\n\n此專屬連結支援公開與私人問卷，轉發給受訪者即可直接進入填答。`,
          mode: 'noticeMode',
          confirmText: '確定',
          showCloseButton: true,
        };
        this._dialogService.showDialog(data).subscribe();
      }).catch(() => {
        this._fallbackCopyShareLink(survey, shareUrl);
      });
    } else {
      this._fallbackCopyShareLink(survey, shareUrl);
    }
  }

  /**
   * 剪貼簿 API 不支援時之回退對話框
   */
  private _fallbackCopyShareLink(survey: SurveySummaryItem, shareUrl: string): void {
    const data: DialogData = {
      title: '專屬填答連結',
      message: `問卷「${survey.title}」之專屬填答連結如下，請選取複製：\n\n${shareUrl}`,
      mode: 'noticeMode',
      confirmText: '確定',
      showCloseButton: true,
    };
    this._dialogService.showDialog(data).subscribe();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 匯出功能方法群
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 切換匯出格式下拉選單的展開/收起狀態
   */
  public toggleExportMenu(): void {
    this.isExportMenuOpen.update((v) => !v);
  }

  /**
   * 執行指定格式的報表匯出並關閉下拉選單
   * 支援 JSON / CSV / Excel / SPSS 四種格式
   * @param format 匯出格式種類
   */
  public onExportReport(format: ExportFormat): void {
    const report = this.currentAnalyticsReport();
    if (!report) {
      return;
    }

    this.isExportMenuOpen.set(false);
    this._analyticsService.exportReport(report, format);

    const formatLabels: Record<ExportFormat, string> = {
      json: 'JSON 原始資料',
      csv: 'CSV 逗號分隔值',
      excel: 'Excel XLSX 格式',
      spss: 'SPSS 相容格式',
    };

    this._dialogService.showDialog({
      title: '報表匯出成功',
      message: `問卷「${report.surveyTitle}」的統計分析報表已以 ${formatLabels[format]} 格式成功下載。`,
      mode: 'noticeMode',
      autoCloseDuration: 2800,
    }).subscribe();
  }

  /**
   * 列印或列印預覽當前數據分析儀表板
   */
  public onPrintReport(): void {
    window.print();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 模板輔助計算方法
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 計算近 7 日趨勢圖中各天柱高度百分比
   * 以最大日量為 100% 基準，動態計算各天的相對高度比例
   * @param count 當天填答筆數
   * @param timelineData 完整時間軸資料（用於取得最大值）
   * @returns 百分比數值（0-100）
   */
  public getBarHeightPct(count: number, timelineData: { submissionsCount: number }[]): number {
    const max = Math.max(...timelineData.map((t) => t.submissionsCount), 1);
    return Math.round((count / max) * 100);
  }

  /**
   * 計算百分比環形進度值，用於 SVG 環形圖
   * @param value 當前值
   * @param total 總值
   * @returns 0-100 百分比數值（含一位小數）
   */
  public getPct(value: number, total: number): number {
    if (!total) return 0;
    return Math.round((value / total) * 1000) / 10;
  }

  /**
   * 根據 NPS 分數取得對應的樣式 CSS class
   * NPS ≥ 50: 優秀（綠）；20-49: 良好（藍）；0-19: 普通（黃）；< 0: 待改善（紅）
   * @param nps NPS 凈推薦值
   * @returns CSS class 字串
   */
  public getNpsClass(nps: number): string {
    if (nps >= 50) return 'nps-excellent';
    if (nps >= 20) return 'nps-good';
    if (nps >= 0) return 'nps-fair';
    return 'nps-poor';
  }

  /**
   * 取得量表分數對應的顏色 class（用於分佈直方圖柱體著色）
   * 1-4: 紅；5-6: 黃；7-8: 藍；9-10: 綠
   * @param score 分數（1-10）
   * @returns CSS class 字串
   */
  public getScoreColorClass(score: number): string {
    if (score >= 9) return 'score-promoter';
    if (score >= 7) return 'score-passive';
    if (score >= 5) return 'score-neutral';
    return 'score-detractor';
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 時限與發布雙狀態管理
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 開啟問卷時限與發布雙狀態設定彈窗
   */
  public onOpenSettings(survey: SurveySummaryItem): void {
    this.currentEditingSurvey.set(survey);
    this.settingsStartDate.set(survey.startDate || '');
    this.settingsEndDate.set(survey.endDate || '');
    this.settingsIsPublicToLobby.set(survey.isPublicToLobby ?? true);
    this.settingsIsOpen.set(survey.isPublished ?? true);
    this.isSettingsModalOpen.set(true);
  }

  /**
   * 關閉問卷時限與狀態設定彈窗
   */
  public onCloseSettings(): void {
    this.isSettingsModalOpen.set(false);
    this.currentEditingSurvey.set(null);
  }

  /**
   * 變更開始日期時間
   */
  public onStartDateChange(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.settingsStartDate.set(val);
  }

  /**
   * 變更結束日期時間
   */
  public onEndDateChange(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.settingsEndDate.set(val);
  }

  /**
   * 切換大廳公開狀態
   */
  public onTogglePublicToLobby(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.settingsIsPublicToLobby.set(checked);
  }

  /**
   * 切換開放填寫狀態
   */
  public onToggleIsOpen(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.settingsIsOpen.set(checked);
  }

  /**
   * 清除填答時限（設為常態開放）
   */
  public onClearTimeRange(): void {
    this.settingsStartDate.set('');
    this.settingsEndDate.set('');
  }

  /**
   * 儲存時限與雙狀態設定至 MySQL 資料庫
   */
  public onSaveSettings(): void {
    const survey = this.currentEditingSurvey();
    if (!survey) return;

    const start = this.settingsStartDate().trim();
    const end = this.settingsEndDate().trim();

    if (start && end && start > end) {
      this._dialogService.showDialog({
        title: '時程設定無效',
        message: '開始時間不可晚於截止時間，請重新檢查設定。',
        mode: 'errorMode',
        errorText: '確定',
      }).subscribe();
      return;
    }

    const payload = {
      startDate: start || undefined,
      endDate: end || undefined,
      isPublicToLobby: this.settingsIsPublicToLobby(),
      isOpen: this.settingsIsOpen(),
    };

    this._userEmitter.emitUpdateSurveySettings(survey.id, payload, () => {
      this.surveyList.update((list) =>
        list.map((item) =>
          item.id === survey.id
            ? {
                ...item,
                startDate: start,
                endDate: end,
                isPublicToLobby: payload.isPublicToLobby,
                isPublished: payload.isOpen,
              }
            : item
        )
      );
      this.onCloseSettings();
      this._dialogService.showDialog({
        title: '時限與發布狀態已更新',
        message: `問卷「${survey.title}」之可填寫時段與大廳公開狀態已成功儲存至資料庫！`,
        mode: 'noticeMode',
        confirmText: '確定',
        autoCloseDuration: 2200,
      }).subscribe();
    });
  }

  /**
   * 格式化 ISO 日期時間字串為簡潔精確的顯示格式 (YYYY/MM/DD HH:mm)
   * 去除不必要的 T 與秒數，方便垂直緊湊呈現，徹底防止表格橫向溢出
   * @param dtStr ISO 格式日期時間字串
   * @returns 簡潔格式化之日期時間
   */
  protected formatDateTimeShort(dtStr?: string | null): string {
    if (!dtStr) return '-';
    const clean = dtStr.replace('T', ' ');
    if (clean.length >= 16) {
      return clean.substring(0, 16).replace(/-/g, '/');
    }
    return clean.replace(/-/g, '/');
  }
}
