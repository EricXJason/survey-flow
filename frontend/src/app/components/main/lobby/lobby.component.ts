/**
 * ============================================================================
 * 檔案名稱: lobby.component.ts
 * 所屬模組: 公開問卷大廳模組 (Survey Public Lobby Module)
 * 責任描述: 負責開放填答之公開問卷清單檢索、多維分類標籤篩選、即時關鍵字模糊搜尋、Material Table 分頁與排序、以及選定問卷跳轉填答。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/survey-getter.service, ../../../@services/survey-state.service, ../../../@interfaces/survey-model
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 支援 ⌘K / Ctrl+K 搜尋焦點快捷鍵；非同步查詢失敗時優雅關閉載入狀態
 * ============================================================================
 */

import {
  AfterViewInit,
  Component,
  computed,
  effect,
  ElementRef,
  HostListener,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { SurveyGetterService } from '../../../@services/survey-getter.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import {
  SurveySummaryItem,
  SURVEY_CATEGORIES,
} from '../../../@interfaces/survey-model';

/**
 * 公開問卷大廳組件 (Lobby Component)
 * 負責檢索並展示所有開放填答的問卷清單，提供即時模糊搜尋、分類標籤快速篩選與 Angular Material Table 列表。
 */
@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
  ],
  templateUrl: './lobby.component.html',
  styleUrl: './lobby.component.scss',
})
export class LobbyComponent implements OnInit, AfterViewInit {
  /** 問卷資料查詢與取得服務 */
  private readonly _surveyGetter = inject(SurveyGetterService);
  /** 問卷全域狀態暫存服務（提供進入填答時傳遞問卷模型） */
  private readonly _surveyState = inject(SurveyStateService);
  /** Angular 路由跳轉器 */
  private readonly _router = inject(Router);

  /** 大廳公開問卷原始列表信號 (Signal) */
  public readonly surveyList = signal<SurveySummaryItem[]>([]);
  /** 問卷列表載入狀態信號 */
  public readonly isLoading = signal<boolean>(true);
  /** 關鍵字搜尋輸入信號 */
  public readonly searchQuery = signal<string>('');
  /** 當前選取的問卷主題分類（預設 'all' 表示全部） */
  public readonly selectedCategory = signal<string>('all');

  /** Angular Material 表格欄位定義清單 */
  public readonly displayedColumns: string[] = [
    'id',
    'title',
    'category',
    'timeRange',
    'status',
    'actions',
  ];

  /** Angular Material Table 專用資料來源執行個體 */
  public readonly dataSource = new MatTableDataSource<SurveySummaryItem>([]);

  /** Material 分頁器組件引用 */
  @ViewChild(MatPaginator) public paginator!: MatPaginator;
  /** Material 排序控制組件引用 */
  @ViewChild(MatSort) public sort!: MatSort;
  /** 搜尋輸入框參照（支援 ⌘K 快捷鍵聚焦） */
  @ViewChild('searchInput') public searchInputRef?: ElementRef<HTMLInputElement>;

  /**
   * 監聽全域鍵盤快捷鍵（⌘K 或 Ctrl+K 自動聚焦搜尋框）
   */
  @HostListener('window:keydown', ['$event'])
  public handleGlobalKeydown(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (this.searchInputRef?.nativeElement) {
        this.searchInputRef.nativeElement.focus();
        this.searchInputRef.nativeElement.select();
      }
    }
  }

  /**
   * 計算屬性：依據搜尋關鍵字與選取類別進行即時多重篩選後的問卷列表
   */
  public readonly filteredSurveys = computed(() => {
    let list = this.surveyList();
    const category = this.selectedCategory();
    if (category !== 'all') {
      list = list.filter((survey) => survey.category === category);
    }
    const query = this.searchQuery().trim().toLowerCase();
    if (query) {
      list = list.filter(
        (survey) =>
          survey.title.toLowerCase().includes(query) ||
          survey.id.toLowerCase().includes(query) ||
          survey.category.toLowerCase().includes(query),
      );
    }
    return list;
  });

  /**
   * 計算屬性：提供 10 大標準主題分類清單，若資料庫中有其他額外分類亦自動相容
   */
  public readonly categories = computed(() => {
    const list = [...SURVEY_CATEGORIES];
    const extra = new Set<string>();
    for (const survey of this.surveyList()) {
      if (survey.category && !(list as readonly string[]).includes(survey.category)) {
        extra.add(survey.category);
      }
    }
    return [...list, ...Array.from(extra)];
  });

  /**
   * 計算特定主題分類當前擁有的問卷數量
   * @param category 分類名稱字串
   * @returns 該分類所屬問卷總數
   */
  public getCategoryCount(category: string): number {
    return this.surveyList().filter((survey) => survey.category === category).length;
  }

  /**
   * 計算屬性：統計當前開放公開填答之問卷數量
   */
  public readonly publishedSurveysCount = computed(() => {
    return this.surveyList().filter((survey) => survey.isPublished !== false).length;
  });

  /**
   * 建構函式：監聽過濾問卷結果變化 (effect)，自動更新 Material Table 資料來源並重設回第一頁
   */
  constructor() {
    effect(() => {
      const data = this.filteredSurveys();
      this.dataSource.data = data;
      if (this.paginator) {
        this.paginator.firstPage();
      }
    });
  }

  /**
   * Angular 生命週期鉤子 (ngOnInit)：組件初始化時發起後端/服務查詢大廳問卷資料
   */
  public ngOnInit(): void {
    this.fetchLobbySurveys();
  }

  /**
   * Angular 生命週期鉤子 (ngAfterViewInit)：視圖初始化後綁定 Material 分頁器與排序器
   */
  public ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  /**
   * 向服務層請求大廳所有開放之公開問卷清單
   * 成功時更新 surveyList 並解除載入動畫；失敗時優雅容錯並關閉載入骨架。
   */
  public fetchLobbySurveys(): void {
    this.isLoading.set(true);
    this._surveyGetter.getLobbySurveys().subscribe({
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
   * 處理搜尋框輸入事件
   * @param event 原生 Input 事件物件，擷取輸入字串並更新 searchQuery 信號
   */
  public onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.searchQuery.set(input.value);
  }

  /**
   * 清除當前搜尋輸入框文字，還原完整問卷列表
   */
  public clearSearch(): void {
    this.searchQuery.set('');
  }

  /**
   * 設定當前選取之主題分類標籤
   * @param category 分類名稱字串（傳入 'all' 則篩選全部）
   */
  public setCategory(category: string): void {
    this.selectedCategory.set(category);
  }

  public onSelectSurvey(surveyId: string): void {
    this._surveyGetter.getSurveyById(surveyId).subscribe((fullSurvey) => {
      if (!fullSurvey) {
        return;
      }

      this._surveyState.setForRunMode(fullSurvey);
      this._router.navigateByUrl('/survey-runner');
    });
  }
}
