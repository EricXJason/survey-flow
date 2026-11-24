/**
 * ============================================================================
 * 檔案名稱: utility.service.ts
 * 所屬模組: 全域通用工具服務 (Utility Service)
 * 責任描述: 1. 每次路由換頁完成後自動平滑捲動到頁面頂端 (避免停在上一頁的捲動位置)
 *           2. 提供靜態方法 scrollToTopSmooth 供「回到頂端」按鈕直接呼叫
 * 架構分層: Service Layer
 * 聯動關係:
 *   - 上游依賴: Angular Router (NavigationEnd 事件)
 *   - 下游消費: AppComponent (建立時即注入以啟動換頁監聽)、ScrollToTopButtonComponent
 * 邊界處理: 非瀏覽器環境 (無 window) 時不執行捲動；服務銷毀時取消路由訂閱
 * ============================================================================
 */

import { Injectable, OnDestroy, inject } from '@angular/core';
import { Event, NavigationEnd, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class UtilityService implements OnDestroy {
  private readonly router = inject(Router);

  /** 路由換頁事件之訂閱 (服務銷毀時取消) */
  private readonly routerSubscription: Subscription = this.router.events
    .pipe(filter((event: Event): event is NavigationEnd => event instanceof NavigationEnd))
    .subscribe(() => UtilityService.scrollToTopSmooth());

  /**
   * 平滑捲動至頁面最上方
   */
  public static scrollToTopSmooth(): void {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, left: 0, behavior: 'smooth' });
    }
  }

  /**
   * 服務銷毀時取消路由事件訂閱，避免記憶體洩漏
   */
  public ngOnDestroy(): void {
    this.routerSubscription.unsubscribe();
  }
}
