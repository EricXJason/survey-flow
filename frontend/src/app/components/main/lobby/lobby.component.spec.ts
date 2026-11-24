/**
 * ============================================================================
 * 檔案名稱: lobby.component.spec.ts
 * 測試對象: LobbyComponent (載入公開問卷、只顯示期間內問卷、分類與關鍵字篩選、統計數字、進入填答、Ctrl+K 聚焦搜尋)
 * ============================================================================
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { SurveyPayload, SurveySummaryItem } from '../../../@interfaces/survey-model';
import { SurveyGetterService } from '../../../@services/survey-getter.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { LobbyComponent } from './lobby.component';

/**
 * 建立一筆問卷摘要
 * @param id 問卷編號
 * @param overrides 要覆寫之欄位
 * @returns 問卷摘要
 */
function summary(id: string, overrides: Partial<SurveySummaryItem> = {}): SurveySummaryItem {
  return { id, title: `問卷${id}`, category: '顧客滿意度', startDate: '', endDate: '', isPublished: true, ...overrides };
}

describe('LobbyComponent', () => {
  let fixture: ComponentFixture<LobbyComponent>;
  let component: LobbyComponent;
  let getter: jasmine.SpyObj<SurveyGetterService>;
  let router: Router;

  /**
   * 以指定清單建立元件並完成初次渲染
   * @param surveys 後端回傳的公開問卷
   */
  async function create(surveys: SurveySummaryItem[] = []): Promise<void> {
    getter.getLobbySurveys.and.returnValue(of(surveys));
    fixture = TestBed.createComponent(LobbyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    getter = jasmine.createSpyObj('SurveyGetterService', ['getLobbySurveys', 'getSurveyById']);
    await TestBed.configureTestingModule({
      imports: [LobbyComponent],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: SurveyGetterService, useValue: getter }],
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  it('只保留目前在開放期間內的問卷 (已結束、尚未開始的排除；日期格式錯誤視為不限)', async () => {
    await create([
      summary('1'),
      summary('2', { endDate: '2000-01-01 00:00' }),
      summary('3', { startDate: '2999-01-01T00:00' }),
      summary('4', { startDate: 'abc', endDate: 'xyz' }),
    ]);

    expect(component.isLoading()).toBeFalse();
    expect(component.surveyList().map((s) => s.id)).toEqual(['1', '4']);
  });

  it('伺服器錯誤時顯示無法連線狀態', async () => {
    getter.getLobbySurveys.and.returnValue(throwError(() => new Error('down')));
    fixture = TestBed.createComponent(LobbyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isServerUnavailable()).toBeTrue();
    expect(component.isLoading()).toBeFalse();
  });

  it('分類與關鍵字篩選 (標題、編號、分類皆可搜尋，不分大小寫)', async () => {
    await create([
      summary('1', { title: 'Coffee 調查' }),
      summary('2', { category: '教育學術研究' }),
      summary('3', { title: '員工福利', category: '自訂分類' }),
    ]);

    component.setCategory('教育學術研究');
    expect(component.filteredSurveys().map((s) => s.id)).toEqual(['2']);

    component.onCategorySelectChange({ target: { value: 'all' } } as unknown as Event);
    component.onSearchInput({ target: { value: ' coffee ' } } as unknown as Event);
    expect(component.filteredSurveys().map((s) => s.id)).toEqual(['1']);

    component.onSearchInput({ target: { value: '3' } } as unknown as Event);
    expect(component.filteredSurveys().map((s) => s.id)).toEqual(['3']);

    component.clearSearch();
    expect(component.filteredSurveys().length).toBe(3);
  });

  it('統計：分類清單包含後端出現的自訂分類；各分類數量', async () => {
    await create([
      summary('1'),
      summary('2', { category: '自訂分類', isPublished: false }),
      summary('3', { category: '' }),
    ]);

    expect(component.categories()).toContain('自訂分類');
    expect(component.getCategoryCount('顧客滿意度')).toBe(1);
  });

  it('點選問卷：載入完整問卷、切換填答模式並前往填答頁；查無問卷時不跳轉', async () => {
    await create([summary('7')]);
    const full = { id: '7', title: '問卷7' } as SurveyPayload;
    getter.getSurveyById.and.returnValue(of(full));

    component.onSelectSurvey('7');

    expect(TestBed.inject(SurveyStateService).currentSurvey()).toBe(full);
    expect(router.navigate).toHaveBeenCalledWith(['/survey-runner'], { queryParams: { from: 'lobby', surveyId: '7' } });

    getter.getSurveyById.and.returnValue(of(null));
    component.onSelectSurvey('8');
    expect(router.navigate).toHaveBeenCalledTimes(1);
  });

  it('Ctrl+K / ⌘K 聚焦搜尋框；其他按鍵不處理', async () => {
    await create([summary('1')]);
    const input = component.searchInputRef!.nativeElement;
    spyOn(input, 'focus');

    component.handleGlobalKeydown(new KeyboardEvent('keydown', { key: 'j', ctrlKey: true }));
    expect(input.focus).not.toHaveBeenCalled();

    component.handleGlobalKeydown(new KeyboardEvent('keydown', { key: 'K', metaKey: true }));
    expect(input.focus).toHaveBeenCalled();
  });

  it('日期顯示格式：YYYY/MM/DD HH:mm；空值顯示 -', async () => {
    await create();
    const format = (value?: string | null): string => (component as any).formatDateTimeShort(value);

    expect(format('2026-01-02T08:30:00')).toBe('2026/01/02 08:30');
    expect(format('2026-01-02')).toBe('2026/01/02');
    expect(format(null)).toBe('-');
  });
});
