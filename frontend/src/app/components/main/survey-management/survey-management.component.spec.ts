/**
 * ============================================================================
 * 檔案名稱: survey-management.component.spec.ts
 * 測試對象: SurveyManagementComponent (清單載入與篩選、排序、預覽、分析頁切換與權限、刪除、分享連結、
 *           匯出、圖表計算、開放設定視窗與樂觀鎖版本號)
 * ============================================================================
 */

import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MatSort } from '@angular/material/sort';
import { createDialogSpy, lastDialog } from '../../../../testing/test-doubles';
import { SurveyAnalyticsReport, OptionStatItem, QuestionAnalyticsItem, DemographicStats } from '../../../@interfaces/survey-analytics-model';
import { SurveyPayload, SurveySummaryItem } from '../../../@interfaces/survey-model';
import { DialogService } from '../../../@services/dialog.service';
import { SurveyAnalyticsService } from '../../../@services/survey-analytics.service';
import { SurveyGetterService } from '../../../@services/survey-getter.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { UserStateService } from '../../../@services/user-state.service';
import { SurveyManagementComponent } from './survey-management.component';

/**
 * 建立一筆問卷摘要
 * @param id 問卷編號
 * @param overrides 要覆寫之欄位
 * @returns 問卷摘要
 */
function summary(id: string, overrides: Partial<SurveySummaryItem> = {}): SurveySummaryItem {
  return {
    id, userSurveyNumber: Number(id), title: `問卷${id}`, category: '顧客滿意度', authorId: '1',
    startDate: '', endDate: '', isPublicToLobby: true, isPublished: true, isOpen: true, version: 0,
    shareToken: `token-${id}`, ...overrides,
  };
}

/** 分析報表 (只含測試會用到的欄位) */
const REPORT = { surveyId: '1', surveyTitle: '問卷1', questionsAnalytics: [] } as unknown as SurveyAnalyticsReport;

describe('SurveyManagementComponent', () => {
  let fixture: ComponentFixture<SurveyManagementComponent>;
  let component: SurveyManagementComponent;
  let getter: jasmine.SpyObj<SurveyGetterService>;
  let analytics: jasmine.SpyObj<SurveyAnalyticsService>;
  let emitter: jasmine.SpyObj<UserEmitterService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: Router;
  let queryParams: BehaviorSubject<Record<string, string>>;

  /**
   * 以指定權限與清單建立元件
   * @param access 登入者權限
   * @param surveys 清單 API 回傳的問卷
   */
  async function create(access = 'member', surveys: SurveySummaryItem[] = [summary('1'), summary('2', { isPublicToLobby: false })]): Promise<void> {
    TestBed.inject(UserStateService).access.set(access);
    TestBed.inject(UserStateService).id.set('1');
    getter.getMemberSurveys.and.returnValue(of(surveys));
    getter.getAllSurveys.and.returnValue(of(surveys));
    fixture = TestBed.createComponent(SurveyManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /**
   * 建立一個帶 value / checked 的假 input 事件
   * @param props input 的屬性
   * @returns Event
   */
  const inputEvent = (props: Partial<HTMLInputElement>): Event => ({ target: { ...props } } as unknown as Event);

  beforeEach(async () => {
    getter = jasmine.createSpyObj('SurveyGetterService', ['getMemberSurveys', 'getAllSurveys', 'getSurveyById']);
    analytics = jasmine.createSpyObj('SurveyAnalyticsService', ['canAccessAnalytics', 'getSurveyAnalytics', 'exportReport']);
    emitter = jasmine.createSpyObj('UserEmitterService', ['emitDeleteSurvey', 'emitUpdateSurveySettings']);
    dialog = createDialogSpy();
    queryParams = new BehaviorSubject<Record<string, string>>({});
    await TestBed.configureTestingModule({
      imports: [SurveyManagementComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: SurveyGetterService, useValue: getter },
        { provide: SurveyAnalyticsService, useValue: analytics },
        { provide: UserEmitterService, useValue: emitter },
        { provide: DialogService, useValue: dialog },
      ],
    }).compileComponents();
    TestBed.overrideProvider(ActivatedRoute, { useValue: { queryParams } });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  it('狀態標籤：連結狀態依開放填答 (isOpen) 判斷，與是否公開於大廳無關', async () => {
    await create('member', [
      summary('1', { isPublicToLobby: false, isOpen: true, isPublished: false }),
      summary('2', { isPublicToLobby: true, isOpen: false, isPublished: true }),
    ]);
    fixture.detectChanges();
    const rows = Array.from(fixture.nativeElement.querySelectorAll('.dual-status-chips') as NodeListOf<HTMLElement>)
      .map((el) => el.textContent!.replace(/\s+/g, ''));

    expect(rows).toContain('大廳-非開放連結-開放');
    expect(rows).toContain('大廳-開放連結-非開放');
  });

  describe('清單', () => {
    it('會員載入自己的問卷；管理員載入全站問卷', async () => {
      await create();
      expect(getter.getMemberSurveys).toHaveBeenCalledWith('1');
      expect(component.isAdmin()).toBeFalse();

      await create('admin');
      expect(getter.getAllSurveys).toHaveBeenCalled();
      expect(component.isAdmin()).toBeTrue();
    });

    it('載入失敗顯示無法連線 (會員與管理員皆同)', async () => {
      getter.getMemberSurveys.and.returnValue(throwError(() => new Error('down')));
      TestBed.inject(UserStateService).access.set('member');
      fixture = TestBed.createComponent(SurveyManagementComponent);
      fixture.detectChanges();
      expect(fixture.componentInstance.isServerUnavailable()).toBeTrue();

      await create('admin');
      getter.getAllSurveys.and.returnValue(throwError(() => new Error('down')));
      component.fetchSurveys();
      expect(component.isServerUnavailable()).toBeTrue();
    });

    it('公開 / 私人篩選與統計；搜尋可用標題、#個人序號、作者與分類', async () => {
      await create('member', [
        summary('1', { title: 'Coffee' }),
        summary('2', { isPublicToLobby: false, authorName: '小美' }),
        summary('3', { isPublicToLobby: undefined, isPublished: false, category: '教育' }),
      ]);

      expect([component.totalCount(), component.publishedCount(), component.privateCount()]).toEqual([3, 1, 2]);
      component.setFilter('published');
      expect(component.displaySurveys().map((s) => s.id)).toEqual(['1']);
      component.setFilter('private');
      expect(component.displaySurveys().map((s) => s.id)).toEqual(['2', '3']);

      component.setFilter('all');
      component.onSearchInput(inputEvent({ value: '#3' }));
      expect(component.displaySurveys().map((s) => s.id)).toEqual(['3']);
      component.onSearchInput(inputEvent({ value: '小美' }));
      expect(component.displaySurveys().map((s) => s.id)).toEqual(['2']);
      component.onSearchInput(inputEvent({ value: 'coffee' }));
      expect(component.displaySurveys().map((s) => s.id)).toEqual(['1']);
      component.clearSearch();
      expect(component.displaySurveys().length).toBe(3);
    });

    it('排序：編號依數字 (管理員用 id、會員用個人序號)、標題依中文筆畫 (乙 1 畫在甲 5 畫之前)、狀態依權重；未排序時維持原順序', async () => {
      await create('member', [
        summary('10', { userSurveyNumber: 2, title: '乙', isPublicToLobby: false }),
        summary('9', { userSurveyNumber: 1, title: '甲', isPublished: false }),
      ]);
      const sortBy = (active: string, direction: 'asc' | 'desc' | ''): string[] =>
        component.dataSource.sortData(component.dataSource.data, { active, direction } as MatSort).map((s) => s.id);

      expect(sortBy('id', 'asc')).toEqual(['9', '10']);
      expect(sortBy('title', 'asc')).toEqual(['10', '9']);
      expect(sortBy('status', 'asc')).toEqual(['10', '9']);
      expect(sortBy('category', 'asc').length).toBe(2);
      expect(sortBy('startDate', 'asc').length).toBe(2);
      expect(sortBy('id', '')).toEqual(['10', '9']);

      component.isAdmin.set(true);
      expect(sortBy('id', 'desc')).toEqual(['10', '9']);
    });

    it('時間狀態：無期限、已截止、尚未開始、進行中', async () => {
      await create();

      expect(component.getTimeStatus(summary('1'))).toBe('none');
      expect(component.getTimeStatus(summary('1', { endDate: '2000-01-01 00:00' }))).toBe('expired');
      expect(component.getTimeStatus(summary('1', { startDate: '2999-01-01T00:00' }))).toBe('upcoming');
      expect(component.getTimeStatus(summary('1', { startDate: '2000-01-01', endDate: '2999-01-01' }))).toBe('active');
    });
  });

  describe('預覽、刪除與分享', () => {
    it('預覽：載入完整問卷進入預覽模式並前往填答頁；查無問卷不跳轉；失敗顯示錯誤', async () => {
      await create();
      getter.getSurveyById.and.returnValue(of({ title: '問卷1' } as SurveyPayload));
      component.onPreviewSurvey('1');
      expect(getter.getSurveyById).toHaveBeenCalledWith('1', true);
      expect(TestBed.inject(SurveyStateService).isPreviewMode()).toBeTrue();
      expect(router.navigate).toHaveBeenCalledWith(['/survey-runner'], {
        queryParams: { from: 'survey-management', surveyId: '1', preview: 'true' },
      });

      getter.getSurveyById.and.returnValue(of(null));
      component.onPreviewSurvey('2');
      expect(router.navigate).toHaveBeenCalledTimes(1);

      getter.getSurveyById.and.returnValue(throwError(() => new Error('問卷不存在')));
      component.onPreviewSurvey('3');
      expect(lastDialog(dialog).message).toBe('問卷不存在');
    });

    it('刪除：委派刪除流程，成功回呼時從清單移除', async () => {
      await create();
      emitter.emitDeleteSurvey.and.callFake((_id, _title, onSuccess) => onSuccess?.());

      component.onDeleteSurvey(summary('1'));

      expect(emitter.emitDeleteSurvey).toHaveBeenCalledWith('1', '問卷1', jasmine.any(Function));
      expect(component.surveyList().map((s) => s.id)).toEqual(['2']);
    });

    it('複製分享連結：成功時提示已複製；剪貼簿失敗或不支援時改為顯示連結', fakeAsync(async () => {
      await create();
      const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();

      component.onCopyShareLink(summary('1'));
      flush();
      expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/survey-runner?share=token-1`);
      expect(lastDialog(dialog).title).toBe('作答連結已複製');

      writeText.and.rejectWith(new Error('denied'));
      component.onCopyShareLink(summary('1'));
      flush();
      expect(lastDialog(dialog).title).toBe('專屬填答連結');

      component.onCopyLinkFromModal(summary('1'));
      flush();
      expect(lastDialog(dialog).title).toBe('專屬填答連結');

      writeText.and.resolveTo();
      component.onCopyLinkFromModal(summary('1'));
      tick();
      expect(component.isCopiedFromModal()).toBeTrue();
      tick(2200);
      expect(component.isCopiedFromModal()).toBeFalse();
      component.onCopyLinkFromModal(null);
      expect(component.getSurveyShareUrl(null)).toBe('');
      expect(component.getSurveyShareUrl(summary('2', { shareToken: undefined }))).toBe('', '沒有分享代碼時不產生以 ID 組成之連結');
    }));

    it('瀏覽器不支援剪貼簿 API 時直接顯示連結', async () => {
      await create();
      spyOnProperty(navigator, 'clipboard').and.returnValue(undefined as unknown as Clipboard);

      component.onCopyShareLink(summary('1'));
      component.onCopyLinkFromModal(summary('1'));
      component.copyResponseText('內容', 'R1', new Event('click'));

      expect(dialog.showDialog).toHaveBeenCalledTimes(2);
      expect(component.copiedResponseId()).toBeNull();
    });
  });

  describe('分析頁', () => {
    it('沒有權限時顯示錯誤；有權限時寫入網址參數', async () => {
      await create();
      analytics.canAccessAnalytics.and.returnValue(false);
      component.onViewAnalytics(summary('1'));
      expect(lastDialog(dialog).title).toBe('權限不足');

      analytics.canAccessAnalytics.and.returnValue(true);
      component.onViewAnalytics(summary('1'));
      expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { view: 'analytics', surveyId: '1' } }));
    });

    it('網址帶 view=analytics 時載入報表；同一份不重複載入；移除參數時回到清單', fakeAsync(async () => {
      analytics.getSurveyAnalytics.and.returnValue(of(REPORT));
      await create();
      spyOn(window, 'scrollTo');

      queryParams.next({ view: 'analytics', surveyId: '1' });
      queryParams.next({ view: 'analytics', surveyId: '1' });
      tick(50);
      expect(analytics.getSurveyAnalytics).toHaveBeenCalledTimes(1);
      expect(component.activeView()).toBe('analytics');
      expect(component.currentAnalyticsReport()).toBe(REPORT);

      queryParams.next({});
      expect(component.activeView()).toBe('list');
      expect(component.currentAnalyticsReport()).toBeNull();

      component.onBackToList();
      expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: {} }));
    }));

    it('報表載入失敗時回到清單並清除網址參數', async () => {
      analytics.getSurveyAnalytics.and.returnValue(throwError(() => new Error('403')));
      await create();

      queryParams.next({ view: 'analytics', surveyId: '9' });

      expect(component.activeView()).toBe('list');
      expect(component.currentAnalyticsSurveyId()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: {} }));
    });

    it('匯出：關閉選單、委派匯出並提示格式；沒有報表時不動作', async () => {
      await create();
      component.onExportReport('csv');
      expect(analytics.exportReport).not.toHaveBeenCalled();

      component.currentAnalyticsReport.set(REPORT);
      component.toggleExportMenu();
      component.onExportReport('spss');

      expect(component.isExportMenuOpen()).toBeFalse();
      expect(analytics.exportReport).toHaveBeenCalledWith(REPORT, 'spss');
      expect(lastDialog(dialog).message).toContain('SPSS 相容格式');
    });

    it('點選單外面或按 Esc 收起匯出選單；設定視窗開著時 Esc 先關設定視窗', async () => {
      await create();
      component.toggleExportMenu();
      const inside = document.createElement('div');
      inside.className = 'export-dropdown-wrap';
      component.onDocumentClick({ target: inside } as unknown as Event);
      expect(component.isExportMenuOpen()).toBeTrue();
      component.onDocumentClick({ target: document.body } as unknown as Event);
      expect(component.isExportMenuOpen()).toBeFalse();

      component.toggleExportMenu();
      component.onKeyDown(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(component.isExportMenuOpen()).toBeFalse();

      component.onOpenSettings(summary('1'));
      component.toggleExportMenu();
      component.onKeyDown(new KeyboardEvent('keydown', { key: 'Esc' }));
      expect(component.isSettingsModalOpen()).toBeFalse();
      expect(component.isExportMenuOpen()).toBeTrue();
      component.onKeyDown(new KeyboardEvent('keydown', { key: 'Enter' }));
      expect(component.isExportMenuOpen()).toBeTrue();
    });

    it('列印報表呼叫瀏覽器列印', async () => {
      await create();
      spyOn(window, 'print');

      component.onPrintReport();

      expect(window.print).toHaveBeenCalled();
    });
  });

  describe('圖表與統計輔助', () => {
    const options: OptionStatItem[] = [
      { optionId: 1, text: '是', count: 3, percentage: 75 },
      { text: '否', count: 1, percentage: 25 },
    ];

    beforeEach(async () => create());

    it('百分比、最多人數的年齡層 (同數時依優先順序)', () => {
      expect(component.getPct(1, 3)).toBe(33.3);
      expect(component.getPct(1, 0)).toBe(0);
      expect(component.getHighestAgeGroup({ under18: 2, age18to24: 2, age25to34: 0, age35to44: 0, age45plus: 0 })).toBe('age18to24');
      expect(component.getHighestAgeGroup({ under18: 0, age18to24: 0, age25to34: 0, age35to44: 0, age45plus: 0 })).toBeNull();
      expect(component.getHighestAgeGroup(undefined)).toBeNull();
    });

    it('題型判斷：是非題 (兩個選項含是 / 否等字) 與複選題', () => {
      expect(component.isBooleanQuestion({ type: 'boolean' })).toBeTrue();
      expect(component.isBooleanQuestion({ optionsStats: options })).toBeTrue();
      expect(component.isBooleanQuestion({ optionsStats: [{ text: 'A' }, { text: 'B' }] })).toBeFalse();
      expect(component.isBooleanQuestion({ optionsStats: [] })).toBeFalse();
      expect(['checkbox', 'multiRadio', 'MULTI'].every((type) => component.isMultipleChoiceQuestion({ type }))).toBeTrue();
      expect(component.isMultipleChoiceQuestion({ type: 'radio' })).toBeFalse();
    });

    it('最高票選項與量表最高峰 (0 票不算)', () => {
      expect(component.isTopOption(options, options[0])).toBeTrue();
      expect(component.isTopOption(options, options[1])).toBeFalse();
      expect(component.isTopOption([], options[0])).toBeFalse();
      const dist = [{ score: 1, count: 0, percentage: 0 }, { score: 2, count: 5, percentage: 100 }];
      expect(component.isScalePeakScore(dist, dist[1])).toBeTrue();
      expect(component.isScalePeakScore(dist, dist[0])).toBeFalse();
      expect(component.isScalePeakScore(undefined, dist[0])).toBeFalse();
      expect(component.getScaleBarColor(dist[0], 0, 5)).toBe('var(--c-neutral-200)');
      expect(component.getScaleBarColor(dist[1], 13, 5)).toBe('var(--c-chart-1)');
    });

    it('量表柱高以最高票數為滿高；0 票保留 2% 細線，全部 0 票時也是 2%', () => {
      const dist = [{ count: 0 }, { count: 2 }, { count: 4 }];
      expect(component.getScaleBarHeight(dist, dist[2])).toBe(100);
      expect(component.getScaleBarHeight(dist, dist[1])).toBe(50);
      expect(component.getScaleBarHeight(dist, dist[0])).toBe(2);
      expect(component.getScaleBarHeight([{ count: 0 }], { count: 0 })).toBe(2);
    });

    it('性別未填人數 = 總回收數 - 男 - 女 - 其他，不會小於 0', () => {
      const demo = { genderDistribution: { male: 4, female: 3, other: 2 } } as DemographicStats;
      expect(component.getGenderUnspecifiedCount(demo, 10)).toBe(1);
      expect(component.getGenderUnspecifiedCount(demo, 5)).toBe(0);
    });

    it('複選統計：總勾選數、平均每人勾選數、最高佔比', () => {
      const q = { totalResponses: 2, optionsStats: options } as QuestionAnalyticsItem;

      expect(component.getTotalSelections(options)).toBe(4);
      expect(component.getTotalSelections(undefined)).toBe(0);
      expect(component.getAvgSelectionsPerUser(q)).toBe('2.0');
      expect(component.getAvgSelectionsPerUser({ totalResponses: 0, totalAnswerCount: 0 } as QuestionAnalyticsItem)).toBe('0.0');
      expect(component.getPeakPrevalence(options)).toBe(75);
      expect(component.getPeakPrevalence([])).toBe(0);
    });

    it('長條圖與圓餅圖座標：高度依百分比、最高票標記、圓餅依序累加位移', () => {
      const bars = component.getColumnChartData(options);
      expect(bars.map((b) => b.key)).toEqual(['opt-1', 'idx-1-否']);
      expect(bars[0].isTop).toBeTrue();
      expect(bars[0].height).toBeGreaterThan(bars[1].height);
      expect(component.getColumnChartData([])).toEqual([]);

      const donut = component.getDonutSegments(options);
      expect(donut[0].dashOffset).toBe(-0);
      expect(donut[1].dashOffset).toBeCloseTo(-188.5, 1);
      expect(component.getDonutSegments(undefined)).toEqual([]);
    });

    it('文字回覆：依內容或受訪者搜尋、展開收合、複製後 2 秒恢復', fakeAsync(() => {
      const responses = [
        { id: 'R1', content: '很棒', respondentName: '訪客 1' },
        { id: 'R2', content: '普通', respondentName: '訪客 2' },
      ] as never[];
      component.onSearchTextAnswers('Q1', inputEvent({ value: '訪客 2' }));
      expect(component.getFilteredTextResponses('Q1', responses).length).toBe(1);
      expect(component.getFilteredTextResponses('Q2', responses).length).toBe(2);
      expect(component.getFilteredTextResponses('Q1', undefined)).toEqual([]);

      component.toggleResponseExpanded('R1');
      expect(component.isResponseExpanded('R1')).toBeTrue();
      component.toggleResponseExpanded('R1');
      expect(component.isResponseExpanded('R1')).toBeFalse();

      spyOn(navigator.clipboard, 'writeText').and.resolveTo();
      component.copyResponseText('', 'R1', new Event('click'));
      component.copyResponseText('很棒', 'R1', new Event('click'));
      tick();
      expect(component.copiedResponseId()).toBe('R1');
      tick(2000);
      expect(component.copiedResponseId()).toBeNull();
    }));
  });

  describe('開放設定視窗 (樂觀鎖)', () => {
    beforeEach(async () => create());

    it('開啟時把日期轉成 datetime-local 格式並帶入目前狀態；開放填答只看 isOpen，未提供時視為開放', () => {
      component.onOpenSettings(summary('1', { startDate: '2026-01-02T08:30:00', endDate: '2026/02/03 18:00', isPublicToLobby: undefined, isOpen: undefined, isPublished: false }));

      expect(component.settingsStartDate()).toBe('2026-01-02T08:30');
      expect(component.settingsEndDate()).toBe('2026-02-03T18:00');
      expect(component.settingsIsPublicToLobby()).toBeTrue();
      expect(component.settingsIsOpen()).toBeTrue();

      component.onOpenSettings(summary('1', { isOpen: false }));
      expect(component.settingsIsOpen()).toBeFalse();
      expect(component.isSettingsModalOpen()).toBeTrue();
    });

    it('日期格式轉換：已是目標格式原樣、無效值為空、年份超出範圍時夾在 1900~2099', () => {
      expect(component.normalizeDateTimeLocal('2026-01-02T08:30')).toBe('2026-01-02T08:30');
      expect(component.normalizeDateTimeLocal('not a date')).toBe('');
      expect(component.normalizeDateTimeLocal(null)).toBe('');
      expect(component.normalizeDateTimeLocal('1800-01-02 08:30')).toBe('1900-01-02T08:30');
    });

    it('欄位輸入：年份超過 4 位數會截斷；開關與清除時限', () => {
      const input = { value: '20266-01-02T08:30' } as HTMLInputElement;
      component.onStartDateChange({ target: input } as unknown as Event);
      component.onEndDateChange(inputEvent({ value: '' }));
      component.onTogglePublicToLobby(inputEvent({ checked: false }));
      component.onToggleIsOpen(inputEvent({ checked: false }));

      expect(input.value).toBe('2026-01-02T08:30');
      expect(component.settingsStartDate()).toBe('2026-01-02T08:30');
      expect(component.settingsIsPublicToLobby()).toBeFalse();
      expect(component.settingsIsOpen()).toBeFalse();
      expect(component.isSettingsExpired()).toBeFalse();

      component.settingsEndDate.set('2000-01-01T00:00');
      expect(component.isSettingsExpired()).toBeTrue();
      component.onClearTimeRange();
      expect(component.settingsStartDate() + component.settingsEndDate()).toBe('');
    });

    it('儲存：年份不是 4 位數或開始晚於截止時不送出', () => {
      component.onSaveSettings();
      expect(emitter.emitUpdateSurveySettings).not.toHaveBeenCalled();

      component.onOpenSettings(summary('1'));
      component.settingsStartDate.set('20266-01-01T00:00');
      component.onSaveSettings();
      expect(lastDialog(dialog).message).toContain('開放填寫時間之年份');

      component.settingsStartDate.set('');
      component.settingsEndDate.set('202-01-01T00:00');
      component.onSaveSettings();
      expect(lastDialog(dialog).message).toContain('截止填寫時間之年份');

      component.settingsStartDate.set('2026-02-01T00:00');
      component.settingsEndDate.set('2026-01-01T00:00');
      component.onSaveSettings();
      expect(lastDialog(dialog).title).toBe('時程設定無效');
      expect(emitter.emitUpdateSurveySettings).not.toHaveBeenCalled();
    });

    it('儲存成功：送出讀取時的版本號，並以後端回傳的新版本號更新清單', () => {
      emitter.emitUpdateSurveySettings.and.callFake((_id, _settings, onSuccess) => onSuccess?.(4));
      component.onOpenSettings(summary('1', { version: 3 }));
      component.settingsStartDate.set('2026-01-01T08:00');
      component.settingsEndDate.set('2026-01-31T18:00');
      component.settingsIsOpen.set(false);

      component.onSaveSettings();

      expect(emitter.emitUpdateSurveySettings).toHaveBeenCalledWith('1', {
        startDate: '2026-01-01T08:00:00', endDate: '2026-01-31T18:00:59', isPublicToLobby: true, isOpen: false, version: 3,
      }, jasmine.any(Function));
      const updated = component.surveyList().find((s) => s.id === '1')!;
      expect(updated).toEqual(jasmine.objectContaining({ version: 4, isOpen: false, startDate: '2026-01-01T08:00:00' }));
      expect(component.surveyList().find((s) => s.id === '2')!.version).toBe(0);
      expect(component.isSettingsModalOpen()).toBeFalse();
      expect(lastDialog(dialog).title).toBe('時限與發布狀態已更新');
    });

    it('清除時限時送出 null；後端沒回版本號時保留原版本號；沒有版本號時送 null', () => {
      emitter.emitUpdateSurveySettings.and.callFake((_id, _settings, onSuccess) => onSuccess?.(undefined));
      component.onOpenSettings(summary('1', { version: undefined }));

      component.onSaveSettings();

      const [, settings] = emitter.emitUpdateSurveySettings.calls.mostRecent().args;
      expect(settings).toEqual(jasmine.objectContaining({ startDate: null, endDate: null, version: null }));
      expect(component.surveyList()[0].startDate).toBe('');
    });
  });

  it('日期顯示格式：YYYY/MM/DD HH:mm；空值顯示 -', async () => {
    await create();
    const format = (value?: string | null): string => (component as any).formatDateTimeShort(value);

    expect(format('2026-01-02T08:30:00')).toBe('2026/01/02 08:30');
    expect(format('2026-01-02')).toBe('2026/01/02');
    expect(format(undefined)).toBe('-');
  });
});
