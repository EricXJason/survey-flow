/**
 * ============================================================================
 * 檔案名稱: main.component.ts
 * 所屬模組: 主內容路由容器模組 (Main Content Shell Module)
 * 責任描述: 作為應用程式核心內容的 RouterOutlet 承載組件，負責呈現所有主功能頁面。
 * 架構分層: Presentation Layer / Content Container Component
 * 聯動關係:
 *   - 上游依賴: @angular/router (RouterOutlet)
 *   - 下游消費: AppComponent 宿主直接調用
 * 邊界處理: 透過 Angular Router 進行子路由隔離
 * ============================================================================
 */

import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * 主要內容路由容器組件 (Main Component)
 * 作為核心業務路由的插槽承載容器，承載各主要頁面組件（如首頁、大廳、問卷設計、會員中心等）。
 */
@Component({
  selector: 'app-main',
  imports: [RouterOutlet],
  templateUrl: './main.component.html',
  styleUrl: './main.component.scss'
})
export class MainComponent {
}
