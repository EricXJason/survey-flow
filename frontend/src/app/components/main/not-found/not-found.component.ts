/**
 * ============================================================================
 * 檔案名稱: not-found.component.ts
 * 所屬模組: 404 找不到頁面模組 (Not Found Page Module)
 * 責任描述: 負責呈現客戶端無效路徑、已失效連結或不存在問卷頁面之 404 友善提示視圖，
 *           統一全站黑白中性、細緻 1px 建築線條與高對比導航規格。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: app.routes.ts ('**' 通配符路由)
 *   - 下游消費: 透過 Angular Router 渲染展示
 * 邊界處理: 提供「返回首頁」與「前往問卷大廳」按鈕，防止無效網址導致白屏或迷失。
 * ============================================================================
 */

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './not-found.component.html',
  styleUrl: './not-found.component.scss',
})
export class NotFoundComponent {}
