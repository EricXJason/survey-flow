/**
 * ============================================================================
 * 檔案名稱: scroll-to-top-button.component.spec.ts
 * 測試對象: ScrollToTopButtonComponent (捲動超過 200px 才顯示、點擊平滑回到頂端)
 * ============================================================================
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UtilityService } from '../../@services/utility.service';
import { ScrollToTopButtonComponent } from './scroll-to-top-button.component';

describe('ScrollToTopButtonComponent', () => {
  let fixture: ComponentFixture<ScrollToTopButtonComponent>;
  let scrollY: number;

  beforeEach(async () => {
    scrollY = 0;
    spyOnProperty(window, 'scrollY').and.callFake(() => scrollY);
    await TestBed.configureTestingModule({ imports: [ScrollToTopButtonComponent] }).compileComponents();
    fixture = TestBed.createComponent(ScrollToTopButtonComponent);
    fixture.detectChanges();
  });

  it('在頁面頂端時隱藏，捲動超過 200px 後顯示', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.classList).not.toContain('visible');

    scrollY = 201;
    window.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();

    expect(button.classList).toContain('visible');
  });

  it('點擊時平滑捲動到頂端', () => {
    spyOn(UtilityService, 'scrollToTopSmooth');

    fixture.nativeElement.querySelector('button').click();

    expect(UtilityService.scrollToTopSmooth).toHaveBeenCalled();
  });
});
