/**
 * ============================================================================
 * 檔案名稱: home.component.ts
 * 所屬模組: 平台官方首頁模組 (Home Landing Module)
 * 責任描述: 負責呈現 SurveyFlow 產品官方首頁，整合 Hero 橫幅、Bento Grid 功能展示、建立流程及常見問答。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ./hero/hero.component, @angular/router (RouterLink)
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 靜態內容排版，全響應式流動網格
 * ============================================================================
 */

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HeroComponent } from './hero/hero.component';

/**
 * 平台主首頁組件 (Home Component)
 * 組織與展示 SurveyFlow 首頁主視覺 (Hero)、四大核心 Bento Grid 架構、
 * 三步驟發布流程、六大應用情境、常見問題 (FAQ) 與頂級 CTA 號召橫幅。
 */
@Component({
  selector: 'app-home',
  imports: [HeroComponent, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {}
