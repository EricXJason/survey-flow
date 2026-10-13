/**
 * ============================================================================
 * 檔案名稱: hero.component.ts
 * 所屬模組: 首頁主視覺視窗模組 (Hero Section Module)
 * 責任描述: 負責呈現首頁頂部 Hero 橫幅視覺、核心價值呼籲及 macOS 擬真問卷設計調研視窗互動預覽。
 * 架構分層: Presentation Layer / UI Sub-Component
 * 聯動關係:
 *   - 上游依賴: @angular/router (RouterLink)
 *   - 下游消費: HomeComponent 宿主調用
 * 邊界處理: 擬真視窗微動態視覺效果與流動排版
 * ============================================================================
 */

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * 首頁主視覺視窗組件 (Hero Component)
 * 展示平台核心標語、多元題型、直覺表單設計、邏輯跳題與多領域應用，
 * 以及 macOS 擬真「SurveyFlow Studio 即時調研預覽視窗」。
 */
@Component({
  selector: 'app-hero',
  imports: [RouterLink],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.scss',
})
export class HeroComponent {}
