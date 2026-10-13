/**
 * ============================================================================
 * 檔案名稱: scroll-to-top-button.component.ts
 * 所屬模組: 頁面導航置頂微組件模組 (Scroll Interaction Module)
 * 責任描述: 負責固定於視窗右下角的置頂按鈕渲染與點擊互動，調用 UtilityService 實現視窗平滑捲動回頂。
 * 架構分層: Presentation Layer / Shared UI Component
 * 聯動關係:
 *   - 上游依賴: ../../@services/utility.service
 *   - 下游消費: AppComponent 宿主直接調用
 * 邊界處理: 無障礙 focus 外框支援；靜態方法調用免除實例相依性
 * ============================================================================
 */

import { Component } from '@angular/core';
import { UtilityService } from '../../@services/utility.service';

/**
 * 獨立通用置頂按鈕組件 (ScrollToTopButtonComponent)
 * 
 * 特點：
 * 1. 永久固定於畫面右下角 (Fixed Position)。
 * 2. 純圖示無文字設計 (Icon-only)，具備高通用性，可直接移植至未來任何專案。
 * 3. 點擊直接調用 UtilityService.scrollToTopSmooth() 實現平滑捲動回頂。
 * 4. 具備深色毛玻璃質感、微光動效及 WCAG 鍵盤無障礙焦點。
 */
@Component({
  selector: 'app-scroll-to-top-button',
  imports: [],
  templateUrl: './scroll-to-top-button.component.html',
  styleUrl: './scroll-to-top-button.component.scss',
})
export class ScrollToTopButtonComponent {
  /**
   * 點擊按鈕平滑捲動至頂端（直接調用 UtilityService 靜態方法）
   */
  public onScrollToTop(): void {
    UtilityService.scrollToTopSmooth();
  }
}

