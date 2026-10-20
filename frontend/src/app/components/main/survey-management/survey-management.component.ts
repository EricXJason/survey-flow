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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
import {
  SurveyAnalyticsReport,
  ExportFormat,
  OptionStatItem,
  TextResponseItem,
  QuestionAnalyticsItem,
} from '../../../@interfaces/survey-analytics-model';
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
  /** 當前激活路由實例（用於讀取與同步 queryParams） */
  private readonly _route = inject(ActivatedRoute);

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
  /** 伺服器連線異常狀態信號 */
  public readonly isServerUnavailable = signal<boolean>(false);
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
  /** 彈窗中複製連結成功視覺反饋信號 */
  public readonly isCopiedFromModal = signal<boolean>(false);

  // ─────────────────────────────────────────────────────────────────────────
  // 簡答題過濾搜尋與視覺化狀態信號
  // ─────────────────────────────────────────────────────────────────────────

  /** 簡答題搜尋關鍵字暫存 (questionUid -> keyword) */
  public readonly textSearchMap = signal<Record<string, string>>({});
  /** 簡答題展開/收合長文字集合 (responseId -> boolean) */
  public readonly expandedResponseSet = signal<Set<string>>(new Set<string>());

  /** 專業高對比資料視覺化調色盤 (高明度、高純度、澄澈通透且不灰暗的現代頂級調色盤) */
  public readonly chartColors: string[] = [
    '#3b82f6', // 明亮電氣藍 (Vibrant Electric Blue, L=60%)
    '#10b981', // 鮮明翡翠純綠 (Vivid Emerald, L=60%)
    '#f59e0b', // 澄澈琥珀金 (Radiant Amber, L=62%)
    '#8b5cf6', // 清爽鳶尾亮紫 (Vivid Violet, L=65%)
    '#06b6d4', // 澄澈湖水天青 (Vivid Cyan, L=60%)
    '#ec4899', // 鮮麗櫻花桃粉 (Bright Rose Pink, L=65%)
    '#f97316', // 晨曦活力陽光橘 (Bright Sun Orange, L=62%)
    '#6366f1', // 電氣亮靛 (Electric Indigo, L=65%)
    '#14b8a6', // 澄淨薄荷綠 (Bright Mint Teal, L=60%)
    '#d946ef', // 霓虹洋紅 (Vivid Magenta, L=65%)
    '#84cc16', // 活力青檸綠 (Bright Lime Green, L=60%)
    '#0ea5e9', // 蔚藍晴空 (Sky Blue, L=60%)
  ];

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

  /** Material 分頁器引用 (採用 setter 確保 @if 延遲渲染時自動完成 dataSource 綁定) */
  @ViewChild(MatPaginator)
  public set matPaginator(mp: MatPaginator | undefined) {
    if (mp) {
      this.paginator = mp;
      this.dataSource.paginator = mp;
    }
  }
  public paginator!: MatPaginator;

  /** Material 欄位排序器引用 (採用 setter 確保 @if 延遲渲染時自動完成 dataSource 綁定) */
  @ViewChild(MatSort)
  public set matSort(ms: MatSort | undefined) {
    if (ms) {
      this.sort = ms;
      this.dataSource.sort = ms;
    }
  }
  public sort!: MatSort;

  // ─────────────────────────────────────────────────────────────────────────
  // 計算屬性
  // ─────────────────────────────────────────────────────────────────────────

  /** 計算屬性：總問卷數量 */
  public readonly totalCount = computed(() => this.surveyList().length);
  /** 計算屬性：已公開發布之問卷數量 (大廳公開) */
  public readonly publishedCount = computed(
    () => this.surveyList().filter((s) => s.isPublicToLobby ?? s.isPublished).length,
  );
  /** 計算屬性：未公開（私人／專屬連結）之問卷數量 */
  public readonly privateCount = computed(
    () => this.surveyList().filter((s) => !(s.isPublicToLobby ?? s.isPublished)).length,
  );

  /**
   * 計算屬性：根據發布狀態篩選與關鍵字過濾後所產生的顯示清單
   */
  public readonly displaySurveys = computed(() => {
    let list = this.surveyList();
    const filter = this.activeFilter();
    if (filter === 'published') {
      list = list.filter((s) => s.isPublicToLobby ?? s.isPublished);
    } else if (filter === 'private') {
      list = list.filter((s) => !(s.isPublicToLobby ?? s.isPublished));
    }
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      const qNum = q.replace(/^#/, '');
      list = list.filter(
        (s) =>
          s.title.toLowerCase().includes(q) ||
          s.id.toLowerCase().includes(q) ||
          (s.userSurveyNumber && String(s.userSurveyNumber).includes(qNum)) ||
          (s.authorName && s.authorName.toLowerCase().includes(q)) ||
          s.category.toLowerCase().includes(q),
      );
    }
    return list;
  });

  // ─────────────────────────────────────────────────────────────────────────
  // 建構與生命週期
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 建構函式：監聽 displaySurveys 變化 (effect)，自動更新 Material Table 並配置全域自訂排序
   */
  constructor() {
    // [Why]: 配置自訂排序器，支援繁體中文筆畫 (localeCompare)、編號數值排序與多維度狀態綜合排序
    this.dataSource.sortingDataAccessor = (item: SurveySummaryItem, property: string) => {
      switch (property) {
        case 'id':
          if (this.isAdmin()) {
            return parseInt(String(item.id).replace(/\D/g, ''), 10) || 0;
          }
          return item.userSurveyNumber || parseInt(String(item.id).replace(/\D/g, ''), 10) || 0;
        case 'title':
          return item.title || '';
        case 'category':
          return item.category || '';
        case 'status':
          // 狀態維度綜合權重：大廳公開 (2) + 開放填寫中 (1)
          return (item.isPublicToLobby ? 2 : 0) + (item.isPublished ? 1 : 0);
        default:
          return (item as any)[property] || '';
      }
    };

    this.dataSource.sortData = (data: SurveySummaryItem[], sort: MatSort) => {
      const active = sort.active;
      const direction = sort.direction;
      if (!active || direction === '') {
        return data;
      }
      const isAsc = direction === 'asc';
      return data.slice().sort((a, b) => {
        const valA = this.dataSource.sortingDataAccessor(a, active);
        const valB = this.dataSource.sortingDataAccessor(b, active);
        if (typeof valA === 'string' && typeof valB === 'string') {
          return isAsc ? valA.localeCompare(valB, 'zh-Hant') : valB.localeCompare(valA, 'zh-Hant');
        }
        return (valA < valB ? -1 : 1) * (isAsc ? 1 : -1);
      });
    };

    effect(() => {
      const data = this.displaySurveys();
      this.dataSource.data = data;
      if (this.paginator) {
        this.paginator.firstPage();
      }
    });
  }

  /**
   * Angular 生命週期鉤子 (ngOnInit)：檢驗當前使用者身分權限、載入問卷清單，
   * 並監聽 URL queryParams 實現瀏覽器「上一頁/下一頁」無縫切換數據報表與列表視圖。
   */
  public ngOnInit(): void {
    const currentAccess = this._userState.access();
    this.isAdmin.set(currentAccess === 'admin');
    this.fetchSurveys();

    // [Why]: 監聽網址參數，使瀏覽器上一頁/下一頁能精準在問卷列表與數據分析之間流暢切換
    this._route.queryParams.subscribe((params) => {
      const view = params['view'];
      const surveyId = params['surveyId'];

      if (view === 'analytics' && surveyId) {
        if (this.currentAnalyticsSurveyId() !== surveyId) {
          this._loadAnalyticsById(surveyId);
        }
      } else {
        // 若參數無 view=analytics，確保返回問卷列表模式
        if (this.activeView() !== 'list') {
          this.activeView.set('list');
          this.currentAnalyticsReport.set(null);
          this.currentAnalyticsSurveyId.set(null);
          this.isExportMenuOpen.set(false);
        }
      }
    });
  }

  /**
   * Angular 生命週期鉤子 (ngAfterViewInit)：完成視圖渲染後防禦性綁定 Material 分頁器與排序器
   */
  public ngAfterViewInit(): void {
    if (this.paginator) {
      this.dataSource.paginator = this.paginator;
    }
    if (this.sort) {
      this.dataSource.sort = this.sort;
    }
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

  /**
   * 監聽鍵盤 ESC 鍵按下事件 (window:keydown)
   * 當時限與發布設定 Modal 彈窗或匯出選單開啟時，按下 ESC 鍵即刻關閉，符合 WCAG 無障礙規範。
   * @param event 鍵盤按鍵事件
   */
  @HostListener('window:keydown', ['$event'])
  public onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Escape' || event.key === 'Esc') {
      if (this.isSettingsModalOpen()) {
        event.preventDefault();
        this.onCloseSettings();
      } else if (this.isExportMenuOpen()) {
        event.preventDefault();
        this.isExportMenuOpen.set(false);
      }
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
    this.isServerUnavailable.set(false);

    if (this.isAdmin()) {
      this._surveyGetter.getAllSurveys().subscribe({
        next: (surveys) => {
          this.surveyList.set(surveys);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
          this.isServerUnavailable.set(true);
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
        this.isServerUnavailable.set(true);
      },
    });
  }

  /**
   * 預覽指定問卷
   * 載入完整問卷資料後，將模式設定為受訪者預覽模式並導向填答器展示。
   * @param surveyId 目標問卷 ID
   */
  /**
   * 預覽指定問卷
   * 載入完整問卷資料後，將模式設定為受訪者預覽模式並導向填答器展示，
   * 同步傳入 from=survey-management 讓預覽頁面上的「返回」按鈕精準回到問卷管理。
   * @param surveyId 目標問卷 ID
   */
  public onPreviewSurvey(surveyId: string): void {
    this._surveyGetter.getSurveyById(surveyId).subscribe((fullSurvey) => {
      if (!fullSurvey) {
        return;
      }
      this._surveyState.setForPreviewMode(fullSurvey);
      this._router.navigate(['/survey-runner'], {
        queryParams: { from: 'survey-management', surveyId },
      });
    });
  }

  /**
   * 開啟問卷數據統計儀表板
   * 實作細粒度權限安全守衛：
   *   1. 系統管理員可檢視全站所有問卷數據。
   *   2. 一般會員僅能檢視所屬自己建立之問卷，若越權存取則安全攔截阻斷。
   * 通過後更新 URL queryParams 觸發路由同步與載入。
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

    // 透過更新 queryParams 寫入歷史記錄，使瀏覽器上一頁可精準回到列表
    this._router.navigate([], {
      relativeTo: this._route,
      queryParams: { view: 'analytics', surveyId: survey.id },
    });
  }

  /**
   * 依據問卷 ID 載入數據分析報表並切換視圖
   * @param surveyId 目標問卷 ID
   */
  private _loadAnalyticsById(surveyId: string): void {
    this.isAnalyticsLoading.set(true);
    this.currentAnalyticsSurveyId.set(surveyId);

    this._analyticsService.getSurveyAnalytics(surveyId).subscribe({
      next: (report) => {
        this.currentAnalyticsReport.set(report);
        this.activeView.set('analytics');
        this.isAnalyticsLoading.set(false);
        // 切換至分析視圖後平滑置頂
        setTimeout(() => window.scrollTo({ top: 0, behavior: 'smooth' }), 50);
      },
      error: () => {
        this.isAnalyticsLoading.set(false);
        this.currentAnalyticsSurveyId.set(null);
        this.activeView.set('list');
        // 載入失敗時移除 queryParams 回到列表
        this._router.navigate([], {
          relativeTo: this._route,
          queryParams: {},
        });
      },
    });
  }

  /**
   * 自數據分析儀表板返回問卷管理清單視圖
   * 清除 queryParams 促使路由觸發切換回列表，並能讓瀏覽器記錄正確對應
   */
  public onBackToList(): void {
    this._router.navigate([], {
      relativeTo: this._route,
      queryParams: {},
    });
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

  /**
   * 判斷某題目是否為是非題（Boolean 或選項恰好為是/否、同意/不同意）
   */
  public isBooleanQuestion(q: any): boolean {
    if (q.type === 'boolean') return true;
    if (q.optionsStats && q.optionsStats.length === 2) {
      const texts = q.optionsStats.map((o: any) => o.text.trim());
      const boolKeywords = ['是', '否', '對', '不對', '同意', '不同意', 'yes', 'no', 'true', 'false'];
      const matchCount = texts.filter((t: string) =>
        boolKeywords.some((kw) => t.toLowerCase() === kw)
      ).length;
      return matchCount >= 1;
    }
    return false;
  }

  /**
   * 判斷某題目是否為多選題 (Checkbox)
   */
  public isMultipleChoiceQuestion(q: any): boolean {
    return q.type === 'checkbox' || q.type === 'multiRadio' || q.type === 'MULTI';
  }

  /** 最近一次成功複製的簡答回覆 ID */
  public readonly copiedResponseId = signal<string | null>(null);

  /**
   * 複製文字簡答內容至剪貼簿（Google 表單風格便利功能）
   */
  public copyResponseText(content: string, id: string, event: Event): void {
    event.stopPropagation();
    if (!content) return;
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(content).then(() => {
        this.copiedResponseId.set(id);
        setTimeout(() => {
          if (this.copiedResponseId() === id) {
            this.copiedResponseId.set(null);
          }
        }, 2000);
      });
    }
  }

  /**
   * 取得得票率最高之選項
   */
  public getTopOption(options?: OptionStatItem[]): OptionStatItem | null {
    if (!options || options.length === 0) return null;
    return [...options].sort((a, b) => b.count - a.count)[0];
  }

  /**
   * 判斷指定選項是否為得票最高者（支援同票數並列最高票 TOP 1）
   * @param options 題目選項統計清單
   * @param opt 當前檢查之選項物件
   * @returns 若選項票數大於 0 且等於最高得票數，則判定為最高票回傳 true
   */
  public isTopOption(options?: OptionStatItem[], opt?: OptionStatItem): boolean {
    if (!options || options.length === 0 || !opt || opt.count <= 0) {
      return false;
    }
    const maxCount = Math.max(...options.map((o) => o.count));
    return maxCount > 0 && opt.count === maxCount;
  }

  /**
   * 判斷量表題指定分數級距是否為得票最高者（支援同票並列最高 👑 皇冠標記）
   * @param distribution 量表分數分佈清單
   * @param item 當前分數項目
   * @returns 若該分段票數大於 0 且等於最高票數，回傳 true
   */
  public isScalePeakScore(
    distribution?: Array<{ score: number; count: number; percentage: number }>,
    item?: { score: number; count: number; percentage: number },
  ): boolean {
    if (!distribution || distribution.length === 0 || !item || item.count <= 0) {
      return false;
    }
    const maxCount = Math.max(...distribution.map((d) => d.count));
    return maxCount > 0 && item.count === maxCount;
  }

  /**
   * 根據評分級距與滿意度語意計算高明度、高飽和度的專業澄澈色彩
   * 杜絕暗濁灰沉色彩，1~5 分與 0~10 分自然明朗演進
   * @param item 分段數據物件
   * @param sIdx 分段索引
   * @param total 分段總柱數 (如 5 或 11)
   * @returns 鮮明澄澈的 HEX 色碼
   */
  public getScaleBarColor(
    item: { score: number; count: number; percentage: number },
    sIdx: number,
    total: number,
  ): string {
    if (!item || item.count <= 0) {
      return '#e2e8f0'; // 0 票淡灰底
    }

    // 5 級分制 (1 至 5 分滿意度)：明亮珊瑚紅 -> 暖金橘 -> 晴空藍 -> 皇家藍 -> 翡翠純綠
    if (total === 5) {
      const fiveGradeColors = [
        '#fb7185', // 1 分: 明亮活力珊瑚粉紅 (Detractor / 非常不滿意)
        '#fb923c', // 2 分: 明亮陽光金橘 (不滿意)
        '#38bdf8', // 3 分: 澄澈晴空天藍 (Neutral / 普通)
        '#3b82f6', // 4 分: 明亮深邃皇家藍 (Satisfied / 滿意)
        '#10b981', // 5 分: 鮮明翡翠純綠 (Promoter / 極度滿意)
      ];
      return fiveGradeColors[sIdx] || '#3b82f6';
    }

    // 11 級分制 NPS (0 至 10 分淨推薦值)
    if (total === 11) {
      if (sIdx <= 6) {
        // 0-6 分 批評者 (Detractor: 明亮暖紅到暖橘梯度)
        const detractorColors = [
          '#fda4af', '#fb7185', '#f43f5e', '#f87171', '#fb923c', '#f59e0b', '#fbbf24'
        ];
        return detractorColors[sIdx] || '#fb923c';
      } else if (sIdx <= 8) {
        // 7-8 分 被動者 (Passive: 明亮澄澈湖藍/天藍)
        return sIdx === 7 ? '#38bdf8' : '#60a5fa';
      } else {
        // 9-10 分 推薦者 (Promoter: 鮮亮電氣藍至翡翠純綠)
        return sIdx === 9 ? '#3b82f6' : '#10b981';
      }
    }

    // 一般級距：採用高明度、高彩度現代色調
    const modernVibrantColors = [
      '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4',
      '#ec4899', '#f97316', '#6366f1', '#14b8a6', '#84cc16'
    ];
    return modernVibrantColors[sIdx % modernVibrantColors.length];
  }

  /**
   * 計算多選題之全部選項總勾選次數 (人次)
   * @param options 題目選項統計清單
   * @returns 選項票數加總
   */
  public getTotalSelections(options?: OptionStatItem[]): number {
    if (!options || options.length === 0) return 0;
    return options.reduce((sum, opt) => sum + (opt.count || 0), 0);
  }

  /**
   * 計算多選題平均每位受訪者勾選之項目數
   * @param q 題目統計物件
   * @returns 格式化至小數點一位的字串 (例如 "1.8")
   */
  public getAvgSelectionsPerUser(q: QuestionAnalyticsItem): string {
    const totalResponses = q.totalResponses || q.totalAnswerCount || 0;
    if (totalResponses === 0) return '0.0';
    const totalSelections = this.getTotalSelections(q.optionsStats);
    return (totalSelections / totalResponses).toFixed(1);
  }

  /**
   * 取得多選題最高選項之勾選百分比 (最高滲透率)
   * @param options 題目選項統計清單
   * @returns 最高百分比數值
   */
  public getPeakPrevalence(options?: OptionStatItem[]): number {
    if (!options || options.length === 0) return 0;
    return Math.max(...options.map((o) => o.percentage || 0));
  }

  /**
   * 取得多選題專屬 SVG 垂直直方柱狀分佈圖之幾何佈局資料 (基準 viewBox: 0 0 240 160)
   * @param options 題目選項統計清單
   * @returns 包含坐標、尺寸與樣式之幾何物件陣列
   */
  public getColumnChartData(options?: OptionStatItem[]): Array<{
    text: string;
    count: number;
    percentage: number;
    color: string;
    x: number;
    y: number;
    width: number;
    height: number;
    labelX: number;
    labelY: number;
    bottomLabel: string;
    isTop: boolean;
  }> {
    if (!options || options.length === 0) return [];
    const n = options.length;
    const chartLeft = 34;
    const chartWidth = 196;
    const chartBaseY = 130;
    const chartMaxHeight = 105;

    const slotWidth = chartWidth / n;
    const barWidth = Math.min(24, Math.max(12, slotWidth * 0.55));
    const maxCount = Math.max(...options.map((o) => o.count || 0));

    return options.map((opt, idx) => {
      const pct = Math.min(100, Math.max(0, opt.percentage || 0));
      const barHeight = (pct / 100) * chartMaxHeight;
      const x = chartLeft + idx * slotWidth + (slotWidth - barWidth) / 2;
      const y = chartBaseY - barHeight;
      const isTop = opt.count > 0 && opt.count === maxCount;

      return {
        text: opt.text,
        count: opt.count,
        percentage: pct,
        color: this.chartColors[idx % this.chartColors.length],
        x: Math.round(x * 10) / 10,
        y: Math.round(y * 10) / 10,
        width: Math.round(barWidth * 10) / 10,
        height: Math.max(2, Math.round(barHeight * 10) / 10),
        labelX: Math.round((x + barWidth / 2) * 10) / 10,
        labelY: Math.max(16, Math.round((y - 4) * 10) / 10),
        bottomLabel: `#${idx + 1}`,
        isTop,
      };
    });
  }

  /**
   * 計算 SVG 甜甜圈圖各區段之弧長與偏移量
   * 基準周長 C = 2 * PI * 40 ≈ 251.327
   */
  public getDonutSegments(options?: OptionStatItem[]): Array<{
    text: string;
    percentage: number;
    count: number;
    color: string;
    dashArray: string;
    dashOffset: number;
  }> {
    if (!options || options.length === 0) return [];
    const circumference = 251.327; // 2 * Math.PI * 40
    let accumulatedPct = 0;

    return options.map((opt, idx) => {
      const pct = opt.percentage || 0;
      const length = (pct / 100) * circumference;
      const offset = -(accumulatedPct / 100) * circumference;
      accumulatedPct += pct;

      return {
        text: opt.text,
        percentage: pct,
        count: opt.count,
        color: this.chartColors[idx % this.chartColors.length],
        dashArray: `${length} ${circumference}`,
        dashOffset: offset,
      };
    });
  }

  /**
   * 監聽簡答題搜尋框輸入事件
   */
  public onSearchTextAnswers(questionUid: string, event: Event): void {
    const val = (event.target as HTMLInputElement).value || '';
    this.textSearchMap.update((map) => ({
      ...map,
      [questionUid]: val,
    }));
  }

  /**
   * 依關鍵字篩選簡答題文字回覆清單
   */
  public getFilteredTextResponses(
    questionUid: string,
    responses?: TextResponseItem[]
  ): TextResponseItem[] {
    if (!responses) return [];
    const query = (this.textSearchMap()[questionUid] || '').trim().toLowerCase();
    if (!query) return responses;

    return responses.filter(
      (r) =>
        (r.content && r.content.toLowerCase().includes(query)) ||
        (r.respondentName && r.respondentName.toLowerCase().includes(query))
    );
  }

  /**
   * 檢查某筆簡答回覆是否已被使用者展開
   */
  public isResponseExpanded(id: string): boolean {
    return this.expandedResponseSet().has(id);
  }

  /**
   * 切換某筆簡答回覆之展開/收合狀態
   */
  public toggleResponseExpanded(id: string): void {
    this.expandedResponseSet.update((set) => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
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
      startDate: start ? start : null,
      endDate: end ? end : null,
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

  /**
   * 取得指定問卷專屬作答分享網址
   * @param surveyId 目標問卷 ID
   * @returns 完整填答網址字串
   */
  public getSurveyShareUrl(surveyId?: string | null): string {
    if (!surveyId) return '';
    return `${window.location.origin}/survey-runner?surveyId=${surveyId}`;
  }

  /**
   * 於時限與設定彈窗內一鍵複製專屬連結至剪貼簿
   * @param survey 當前編輯中問卷
   */
  public onCopyLinkFromModal(survey: SurveySummaryItem | null): void {
    if (!survey) return;
    const shareUrl = this.getSurveyShareUrl(survey.id);
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(shareUrl).then(() => {
        this.isCopiedFromModal.set(true);
        setTimeout(() => this.isCopiedFromModal.set(false), 2200);
      }).catch(() => {
        this._fallbackCopyShareLink(survey, shareUrl);
      });
    } else {
      this._fallbackCopyShareLink(survey, shareUrl);
    }
  }
}
