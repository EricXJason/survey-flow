/**
 * ============================================================================
 * 檔案名稱: utility.service.spec.ts
 * 測試對象: UtilityService (換頁自動回到頂端、平滑捲動)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { UtilityService } from './utility.service';

describe('UtilityService', () => {
  let routerEvents: Subject<unknown>;
  let service: UtilityService;

  beforeEach(() => {
    routerEvents = new Subject();
    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: { events: routerEvents } }],
    });
    service = TestBed.inject(UtilityService);
    spyOn(window, 'scrollTo');
  });

  it('換頁完成 (NavigationEnd) 時平滑捲動到頂端', () => {
    routerEvents.next(new NavigationEnd(1, '/a', '/a'));

    expect((window.scrollTo as jasmine.Spy).calls.mostRecent().args).toEqual([{ top: 0, left: 0, behavior: 'smooth' }]);
  });

  it('換頁開始 (NavigationStart) 時不捲動', () => {
    routerEvents.next(new NavigationStart(1, '/a'));

    expect(window.scrollTo).not.toHaveBeenCalled();
  });

  it('服務銷毀後不再監聽換頁', () => {
    service.ngOnDestroy();
    routerEvents.next(new NavigationEnd(1, '/a', '/a'));

    expect(window.scrollTo).not.toHaveBeenCalled();
  });
});
