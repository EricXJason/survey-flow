/**
 * ============================================================================
 * 檔案名稱: footer.component.spec.ts
 * 測試對象: FooterComponent、MainComponent、NotFoundComponent (純展示元件：能正常渲染)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MainComponent } from '../main/main.component';
import { NotFoundComponent } from '../main/not-found/not-found.component';
import { FooterComponent } from './footer.component';

describe('純展示元件', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  it('頁尾顯示今年的版權年份', () => {
    const fixture = TestBed.createComponent(FooterComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(String(new Date().getFullYear()));
  });

  it('主內容容器包含 router-outlet', () => {
    const fixture = TestBed.createComponent(MainComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });

  it('找不到頁面提供返回首頁的連結', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('a[href="/home"]')).not.toBeNull();
  });
});
