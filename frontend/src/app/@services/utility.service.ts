/**
 * ============================================================================
 * 檔案名稱: utility.service.ts
 * 所屬模組: 應用程式通用工具服務模組 (Utility Module)
 * 責任描述: 提供視窗捲動管理（平滑與即時置頂、路由自動監聽）、嚴格正整數驗證及無空格字串檢核工具，支援 DI 與靜態呼叫。
 * 架構分層: Service Layer / Cross-Cutting Concerns
 * 聯動關係:
 *   - 上游依賴: @angular/core, @angular/router, @angular/common
 *   - 下游消費: AppComponent, ScrollToTopButtonComponent, 各驗證檢核需求組件
 * 邊界處理: 內建 typeof window 判斷防範 SSR 伺服端渲染異常；正整數檢核全面防範 NaN、Infinity、浮點數與空白
 * ============================================================================
 */

import { Injectable, inject, PLATFORM_ID, OnDestroy } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router, NavigationEnd, Event } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Subscription } from 'rxjs';

/**
 * 全域通用工具服務
 * 
 * 提供：
 * 1. 視窗捲動管理：即時置頂 (scrollToTop)、平滑置頂 (scrollToTopSmooth) 與路由自動置頂。
 * 2. 數值驗證工具：正整數檢查（排除浮點數、負數與非數字，支援數值與純數字字串）。
 * 3. 字串驗證工具：無空格字串檢查（排除空白、製表符、換行與全形空格）。
 * 
 * 支援依賴注入或純靜態方法調用。
 */
@Injectable({
  providedIn: 'root',
})
export class UtilityService implements OnDestroy {
  private readonly _router = inject(Router);
  private readonly _platformId = inject(PLATFORM_ID);
  private _routerSubscription?: Subscription;

  constructor() {
    if (this._router) {
      this._routerSubscription = UtilityService.initRouterScroll(this._router);
    }
  }

  // ==========================================================================
  // 1. 視窗捲動工具（全靜態方法，免 inject 即可直接調用）
  // ==========================================================================

  /**
   * 靜態方法：立即捲動至視窗最頂端 (0, 0)
   * 支援任何環境直接調用，內建 typeof window 檢查防範 SSR 例外
   */
  public static scrollToTop(): void {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    }
  }

  /**
   * 實例相容方法：立即捲動至視窗最頂端 (0, 0)
   */
  public scrollToTop(): void {
    UtilityService.scrollToTop();
  }

  /**
   * 靜態方法：平滑捲動至視窗最頂端 (0, 0)
   * 支援任何環境直接調用，內建 typeof window 檢查防範 SSR 例外
   */
  public static scrollToTopSmooth(): void {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, left: 0, behavior: 'smooth' });
    }
  }

  /**
   * 實例相容方法：平滑捲動至視窗最頂端 (0, 0)
   */
  public scrollToTopSmooth(): void {
    UtilityService.scrollToTopSmooth();
  }

  /**
   * 靜態方法：為 Router 實例建立路由切換平滑自動置頂監聽
   * 
   * @param router Angular Router 實例
   * @returns Subscription 訂閱物件，可於銷毀時取消訂閱
   */
  public static initRouterScroll(router: Router): Subscription | undefined {
    if (typeof window === 'undefined' || !router) {
      return undefined;
    }
    return router.events
      .pipe(filter((event: Event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => {
        UtilityService.scrollToTopSmooth();
      });
  }

  // ==========================================================================
  // 2. 數字檢查工具（全靜態方法，免 inject 即可直接調用）
  // ==========================================================================

  /**
   * 檢查目標值是否為合法正整數
   * 
   * 檢核條件：
   * 1. 基本數字檢查：排除 null、undefined、空字串、NaN、Infinity。
   * 2. 數值型別檢驗：支援 number 與純數字字串。
   * 3. 嚴格整數：不可包含小數點或浮點數。
   * 4. 嚴格正數：數值必須大於 0。
   * 
   * @param value 待檢核目標值
   * @param allowNumericString 是否相容純數字字串（預設 true）
   * @returns 若為大於 0 的整數則回傳 true，否則回傳 false
   */
  public isPositiveInteger(value: unknown, allowNumericString: boolean = true): boolean {
    return UtilityService.isPositiveInteger(value, allowNumericString);
  }

  /**
   * 靜態方法：檢查目標值是否為合法正整數
   */
  public static isPositiveInteger(value: unknown, allowNumericString: boolean = true): boolean {
    if (value === null || value === undefined || value === '') {
      return false;
    }

    let num: number;

    if (typeof value === 'number') {
      num = value;
    } else if (allowNumericString && typeof value === 'string') {
      const trimmed = value.trim();
      // 僅允許純整數數字格式，嚴格排除負號、小數點、科學記號與非數字字符
      if (!/^\d+$/.test(trimmed)) {
        return false;
      }
      num = Number(trimmed);
    } else {
      return false;
    }

    // 排除 NaN、Infinity，確保為整數且大於 0
    return !Number.isNaN(num) && Number.isFinite(num) && Number.isInteger(num) && num > 0;
  }

  /**
   * 別名方法：是否為數字（符合是正整數且非浮點數之規範）
   */
  public isNumber(value: unknown, allowNumericString: boolean = true): boolean {
    return UtilityService.isNumber(value, allowNumericString);
  }

  /**
   * 靜態方法別名：是否是數字
   */
  public static isNumber(value: unknown, allowNumericString: boolean = true): boolean {
    return UtilityService.isPositiveInteger(value, allowNumericString);
  }

  // ==========================================================================
  // 3. 字串無空格檢查工具
  // ==========================================================================

  /**
   * 檢查目標值是否為有效且「無任何空格」的字串
   * 
   * 檢核條件：
   * 1. 基本字串檢查：型別必須為 string，排除 null、undefined 與非字串物件。
   * 2. 非空字串：長度必須大於 0。
   * 3. 嚴格無空格：內部、開頭與結尾皆不可包含任何半形空白、全形空白 (\u3000)、製表符 (\t) 或換行 (\n, \r)。
   * 
   * @param value 待檢核目標值
   * @returns 若為非空且完全無空格的字串則回傳 true，否則回傳 false
   */
  public isStringWithoutSpaces(value: unknown): boolean {
    return UtilityService.isStringWithoutSpaces(value);
  }

  /**
   * 靜態方法：檢查目標值是否為有效且「無任何空格」的字串
   */
  public static isStringWithoutSpaces(value: unknown): boolean {
    if (typeof value !== 'string') {
      return false;
    }

    if (value.length === 0) {
      return false;
    }

    // 檢測是否含有任何半形空白 (\s) 或全形空白 (\u3000)
    if (/\s|\u3000/.test(value)) {
      return false;
    }

    return true;
  }

  /**
   * 別名方法：檢查字串是否無空格
   */
  public isStringNoSpaces(value: unknown): boolean {
    return UtilityService.isStringNoSpaces(value);
  }

  /**
   * 靜態方法別名：檢查字串是否無空格
   */
  public static isStringNoSpaces(value: unknown): boolean {
    return UtilityService.isStringWithoutSpaces(value);
  }

  // ==========================================================================
  // 4. 生命週期清理
  // ==========================================================================

  public ngOnDestroy(): void {
    this._routerSubscription?.unsubscribe();
  }
}
