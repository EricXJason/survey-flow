/**
 * ============================================================================
 * 檔案名稱: footer.component.ts
 * 所屬模組: 全域頁尾資訊模組 (Layout Footer Module)
 * 責任描述: 負責網站全域頁尾渲染，展示品牌摘要、版權宣告動態年份與全站快速連結。
 * 架構分層: Presentation Layer / Shared Layout Component
 * 聯動關係:
 *   - 上游依賴: @angular/router (RouterLink)
 *   - 下游消費: AppComponent 宿主直接調用
 * 邊界處理: 動態取得當前本機年份，避免跨年度硬編碼過期
 * ============================================================================
 */

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * 網站全域頁尾組件 (Footer Component)
 * 展示 SurveyFlow 品牌資訊、網站快速連結以及動態版權年份。
 */
@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss',
})
export class FooterComponent {
  /**
   * 當前公元年份（動態取得，用於版權聲明自動更新）
   */
  protected readonly year = new Date().getFullYear();
}

