/**
 * ============================================================================
 * 檔案名稱: app.component.spec.ts
 * 測試對象: AppComponent (全站版面：頁首、主內容、頁尾、對話框、回到頂端按鈕)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  it('組出全站版面所有區塊', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const html: HTMLElement = fixture.nativeElement;

    ['app-header', 'app-main', 'app-footer', 'app-dialog', 'app-scroll-to-top-button'].forEach((selector) =>
      expect(html.querySelector(selector)).withContext(selector).not.toBeNull());
  });
});
