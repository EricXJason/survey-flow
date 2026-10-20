/**
 * ============================================================================
 * 檔案名稱: hero.component.ts
 * 所屬模組: 首頁主視覺視窗模組 (Hero Section Module)
 * 責任描述: 負責呈現首頁頂部 Hero 橫幅視覺、核心功能特色及擬真「問題條件解鎖」動態問卷填答互動預覽。
 * 架構分層: Presentation Layer / UI Sub-Component
 * 聯動關係:
 *   - 上游依賴: @angular/core (signal), @angular/router (RouterLink)
 *   - 下游消費: HomeComponent 宿主模板嵌入調用
 * 邊界處理: 支援受訪者選項切換、條件子題平滑解鎖展開、滿意度評分與送出回饋狀態
 * ============================================================================
 */

import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * 首頁主視覺視窗組件 (Hero Component)
 * 展示平台核心標語、多元題型、直覺表單設計、邏輯跳題與多領域應用，
 * 以及 macOS 擬真「SurveyFlow 即時互動填答預覽視窗」，讓訪客親身體驗「題目條件解鎖」之流暢互動。
 */
@Component({
  selector: 'app-hero',
  imports: [RouterLink],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.scss',
})
export class HeroComponent {
  /**
   * Q1 單選題選取值 ('desktop' | 'mobile')
   */
  readonly selectedDevice = signal<'desktop' | 'mobile'>('desktop');

  /**
   * Q1-1 條件解鎖子題目選取值 ('ios' | 'android' | null)
   */
  readonly selectedOs = signal<'ios' | 'android' | null>(null);

  /**
   * Q2 五點量表選取值 (1 ~ 5)
   */
  readonly selectedRating = signal<number>(5);

  /**
   * 是否已點選送出體驗問卷
   */
  readonly isSubmitted = signal<boolean>(false);

  /**
   * 使用者是否已在預覽卡片中進行過點擊互動
   */
  readonly hasInteracted = signal<boolean>(false);

  /**
   * 點選 Q1 單選題裝置選項
   * 當選取行動裝置時，自動觸發條件關聯子題解鎖
   * @param device 裝置類型 ('desktop' | 'mobile')
   */
  selectDevice(device: 'desktop' | 'mobile'): void {
    this.selectedDevice.set(device);
    if (device === 'desktop') {
      this.selectedOs.set(null);
    }
    this.hasInteracted.set(true);
  }

  /**
   * 點選 Q1-1 條件解鎖子題目作業系統選項
   * @param os 行動作業系統 ('ios' | 'android')
   */
  selectOs(os: 'ios' | 'android'): void {
    this.selectedOs.set(os);
    this.hasInteracted.set(true);
  }

  /**
   * 點選 Q2 五點量表評分
   * @param score 評分分數 (1 ~ 5)
   */
  selectRating(score: number): void {
    this.selectedRating.set(score);
    this.hasInteracted.set(true);
  }

  /**
   * 提交體驗問卷，觸發成功回饋視圖
   */
  submitDemo(): void {
    this.isSubmitted.set(true);
  }

  /**
   * 重設示範互動狀態回初始預設值
   */
  resetDemo(): void {
    this.selectedDevice.set('desktop');
    this.selectedOs.set(null);
    this.selectedRating.set(5);
    this.isSubmitted.set(false);
    this.hasInteracted.set(false);
  }
}
