/**
 * ============================================================================
 * 檔案名稱: app.component.ts
 * 所屬模組: 應用程式根視圖模組 (Root Shell Module)
 * 責任描述: 負責組織全站整體骨架版面配置，整合頁首、主要內容插槽、頁尾、全局對話框與置頂浮動按鈕。
 * 架構分層: Presentation Layer / Root Shell Component
 * 聯動關係:
 *   - 上游依賴: ./@services/utility.service (全域路由滾動監聽)
 *   - 下游消費: 包含 HeaderComponent, MainComponent, FooterComponent, DialogComponent, ScrollToTopButtonComponent
 * 邊界處理: 應用程式頂層錯誤與視圖降級之宿主載體
 * ============================================================================
 */

import { Component, inject } from '@angular/core';
import { HeaderComponent } from './components/header/header.component';
import { MainComponent } from './components/main/main.component';
import { FooterComponent } from './components/footer/footer.component';
import { DialogComponent } from "./components/dialog/dialog.component";
import { ScrollToTopButtonComponent } from './components/scroll-to-top-button/scroll-to-top-button.component';

import { UtilityService } from './@services/utility.service';

/**
 * 應用程式根元件 (AppComponent)
 * 負責組織全站主要版面配置架構，包含頁首 (Header)、主體 (Main)、頁尾 (Footer)、全局對話框 (Dialog) 與置頂浮動按鈕 (ScrollToTopButton)。
 */
@Component({
  selector: 'app-root',
  imports: [HeaderComponent, MainComponent, FooterComponent, DialogComponent, ScrollToTopButtonComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  /** 注入全域通用工具服務，在全應用生命週期中自動監聽路由跳轉並平滑滾動回頂 */
  private readonly _utility = inject(UtilityService);
}

