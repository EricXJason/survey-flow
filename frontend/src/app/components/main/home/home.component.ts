/**
 * ============================================================================
 * 檔案名稱: home.component.ts
 * 所屬模組: 平台官方首頁模組 (Home Landing Module)
 * 責任描述: 負責呈現 SurveyFlow 產品官方首頁，整合 Hero 橫幅、核心特色展示輪盤 (Feature Showcase Wheel)、七大題型 Bento Grid、建立流程及常見問答。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ./hero/hero.component, @angular/router (RouterLink)
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 整合動態計時器與手動節點互動，銷毀時確實清理定時器
 * ============================================================================
 */

import { Component, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HeroComponent } from './hero/hero.component';

/**
 * 題型示範清單項目結構介面
 */
export interface DemoQuestionTab {
  id: string;
  number: string;
  tag: string;
  title: string;
  benefit: string;
  description: string;
  type: 'binary' | 'jump' | 'multi' | 'scale' | 'text' | 'datetime';
}

/**
 * 平台主首頁組件 (Home Component)
 * 組織與展示 SurveyFlow 首頁主視覺 (Hero)、即時問卷填寫體驗區 (Live Survey Interactive Demo)、
 * 三步驟發布流程、多元應用情境、常見問題 (FAQ) 與行動呼籲 (CTA)。
 */
@Component({
  selector: 'app-home',
  imports: [HeroComponent, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  standalone: true,
})
export class HomeComponent {
  /**
   * 6 大核心題型導覽清單 (SSOT 題型定義)
   */
  readonly questionTabs: DemoQuestionTab[] = [
    {
      id: 'binary',
      number: '01',
      tag: '是非二元',
      title: '是非意願確認題',
      benefit: '極簡二元決策，降低填答認知負擔',
      description: '提供極速的是非二分法判斷，適合用於資格初篩、意願調查或研究知情同意書勾選。',
      type: 'binary',
    },
    {
      id: 'jump',
      number: '02',
      tag: '條件跳題',
      title: '單選動態條件跳題',
      benefit: '選取特定選項即時展開延伸子題，減少 40% 冗題',
      description: '具備互斥單選邏輯，並可設定特定關鍵選項觸發動態子題，未觸發時維持隱藏以保持問卷精簡。',
      type: 'jump',
    },
    {
      id: 'multi',
      number: '03',
      tag: '多重複選',
      title: '多維偏好勾選題',
      benefit: '支援自由勾選與多選項自動佔比統計',
      description: '允許受訪者勾選多項符合之答案，後台自動彙整各項總票數與百分比分佈，掌握受眾偏好多樣性。',
      type: 'multi',
    },
    {
      id: 'scale',
      number: '04',
      tag: '五點量表',
      title: '五點滿意度量表題',
      benefit: '自動運算平均分、中位數與滿意度指標',
      description: '以 1 至 5 分精準量化受訪者對產品、服務或特定議題的認同程度，後台自動計算平均數與標準差。',
      type: 'scale',
    },
    {
      id: 'text',
      number: '05',
      tag: '開放簡答',
      title: '深度質化文字簡答',
      benefit: '即時字數統計、字數限制與深度意見收集',
      description: '開放式文字輸入框供受訪者暢所欲言，支援即時字數檢核提醒，後台完整呈現原始意見脈絡。',
      type: 'text',
    },
    {
      id: 'datetime',
      number: '06',
      tag: '日期時間',
      title: '預約日期與時段題',
      benefit: '規格化日期時間選取，杜絕認知與時差歧異',
      description: '提供原生行事曆與時鐘微調介面，適用於實體訪談時段預約、活動參與偏好調查等精確排程場景。',
      type: 'datetime',
    },
  ];

  /** 當前選中的題型示範索引 (預設為 1: 單選條件跳題，最具互動展示亮點) */
  readonly activeTabIndex = signal<number>(1);

  /** 當前選中的題型物件 */
  readonly activeTab = computed(() => this.questionTabs[this.activeTabIndex()]);

  // ==========================================
  // 即時問卷填答作答模擬狀態 (Reactive Demo Answers)
  // ==========================================

  /** 是非題作答值 ('yes' | 'no' | null) */
  readonly demoBinary = signal<'yes' | 'no' | null>('yes');

  /** 單選條件跳題作答值 ('desktop' | 'mobile' | 'tablet') */
  readonly demoJumpDevice = signal<'desktop' | 'mobile' | 'tablet'>('desktop');

  /** 單選條件跳題之延伸子題作答值 */
  readonly demoJumpScreen = signal<string>('15-inch');

  /** 多選題作答值集合 (key 清單) */
  readonly demoMulti = signal<string[]>(['charts', 'export']);

  /** 五點量表評分值 (1 ~ 5) */
  readonly demoScale = signal<number>(5);

  /** 簡答題文字內容 */
  readonly demoText = signal<string>('SurveyFlow 介面乾淨清晰，條件跳題動態展開非常直覺，填答過程完全沒有多餘阻礙！');

  /** 預約日期值 */
  readonly demoDate = signal<string>('2026-10-20');

  /** 預約時間值 */
  readonly demoTime = signal<string>('14:30');

  // ==========================================
  // 導覽切換與題目作答方法
  // ==========================================

  /**
   * 切換選中的題型分頁
   * @param index 題型索引 (0 ~ 6)
   */
  selectTab(index: number): void {
    if (index >= 0 && index < this.questionTabs.length) {
      this.activeTabIndex.set(index);
    }
  }

  /**
   * 前往上一個題型
   */
  prevTab(): void {
    const total = this.questionTabs.length;
    this.activeTabIndex.update((curr) => (curr - 1 + total) % total);
  }

  /**
   * 前往下一個題型
   */
  nextTab(): void {
    const total = this.questionTabs.length;
    this.activeTabIndex.update((curr) => (curr + 1) % total);
  }

  /**
   * 點選是非題答案
   * @param val 'yes' 或 'no'
   */
  setBinaryAnswer(val: 'yes' | 'no'): void {
    this.demoBinary.set(val);
  }

  /**
   * 點選條件跳題設備 (若切換為 desktop，自動維持或展開延伸子題)
   * @param device 設備代號
   */
  setJumpDevice(device: 'desktop' | 'mobile' | 'tablet'): void {
    this.demoJumpDevice.set(device);
  }

  /**
   * 點選延伸子題螢幕尺寸答案
   * @param screen 尺寸代號
   */
  setJumpScreen(screen: string): void {
    this.demoJumpScreen.set(screen);
  }

  /**
   * 切換多選題指定項目的勾選狀態
   * @param key 選項識別代碼
   */
  toggleMultiOption(key: string): void {
    const current = this.demoMulti();
    if (current.includes(key)) {
      this.demoMulti.set(current.filter((k) => k !== key));
    } else {
      this.demoMulti.set([...current, key]);
    }
  }

  /**
   * 檢查多選題特定項目是否已被勾選
   * @param key 選項識別代碼
   * @returns 是否已被選取
   */
  isMultiSelected(key: string): boolean {
    return this.demoMulti().includes(key);
  }

  /**
   * 設定五點量表評分
   * @param rating 1 至 5
   */
  setScaleRating(rating: number): void {
    this.demoScale.set(rating);
  }

  /**
   * 處理簡答文字輸入變更
   * @param event 原生 Input 事件
   */
  onTextInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    if (target) {
      this.demoText.set(target.value);
    }
  }

  /**
   * 一鍵填入範例簡答文字
   * @param sampleText 範例文字字串
   */
  fillSampleText(sampleText: string): void {
    this.demoText.set(sampleText);
  }

  /**
   * 設定預約日期與時間快捷預設值
   * @param date 日期字串 (YYYY-MM-DD)
   * @param time 時間字串 (HH:mm)
   */
  setDateTimePreset(date: string, time: string): void {
    this.demoDate.set(date);
    this.demoTime.set(time);
  }

  /**
   * 處理日期手動選擇
   * @param event 日期變更事件
   */
  onDateChange(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target) {
      this.demoDate.set(target.value);
    }
  }

  /**
   * 處理時間手動選擇
   * @param event 時間變更事件
   */
  onTimeChange(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target) {
      this.demoTime.set(target.value);
    }
  }

  /**
   * 重設當前題型的作答答案
   */
  resetCurrentDemo(): void {
    const type = this.activeTab().type;
    switch (type) {
      case 'binary':
        this.demoBinary.set(null);
        break;
      case 'jump':
        this.demoJumpDevice.set('mobile');
        this.demoJumpScreen.set('');
        break;
      case 'multi':
        this.demoMulti.set([]);
        break;
      case 'scale':
        this.demoScale.set(3);
        break;
      case 'text':
        this.demoText.set('');
        break;
      case 'datetime':
        this.demoDate.set('2026-10-21');
        this.demoTime.set('10:00');
        break;
    }
  }
}
