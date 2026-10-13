/**
 * ============================================================================
 * 檔案名稱: survey-reorder-dialog.component.ts
 * 所屬模組: 問卷題目拖曳重排模組 (Survey Reorder Sub-Module)
 * 責任描述: 負責提供問卷題目順序之視覺化拖曳排序彈窗，利用 Angular CDK Drag & Drop 重新排列題目並同步回父層表單陣列。
 * 架構分層: Presentation Layer / Modal Dialog Sub-Component
 * 聯動關係:
 *   - 上游依賴: @angular/cdk/drag-drop, ../survey-design.component (父層 SurveyDesignComponent)
 *   - 下游消費: SurveyDesignComponent 宿主調用
 * 邊界處理: 複製外部陣列至本地暫存，取消時捨棄變更；確認後原子化重建父層 FormArray
 * ============================================================================
 */

import { Component, inject, Input, OnInit } from '@angular/core';
import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { FormGroup } from '@angular/forms';
import { SurveyDesignComponent } from '../survey-design.component';

/**
 * 題目拖曳重排項目介面定義
 */
export interface ReorderDialogItem {
  /** 題目所對應的 FormGroup 表單群組執行個體 */
  control: FormGroup;
  /** 題目的唯一識別碼 UID */
  uid: string;
  /** 題目描述縮短文字（供拖曳清單預覽） */
  description: string;
}

/**
 * 題目順序拖曳重排對話框組件 (SurveyReorderDialogComponent)
 * 提供視覺化整頁題目列表拖曳排序視窗，允許問卷設計者快速調動數十道題目之先後次序，
 * 確認後自動同步回父層表單陣列 (questionArray)。
 */
@Component({
  selector: 'app-survey-reorder-dialog',
  templateUrl: './survey-reorder-dialog.component.html',
  styleUrl: './survey-reorder-dialog.component.scss',
  imports: [DragDropModule],
})
export class SurveyReorderDialogComponent implements OnInit {
  /** 注入父層問卷設計組件 */
  private readonly _parent = inject(SurveyDesignComponent);

  /** 輸入屬性：當前問卷題目的初始排列清單 */
  @Input({ required: true }) public initialQuestions: ReorderDialogItem[] = [];

  /** 內部拖曳排序暫存項目陣列 */
  protected items: ReorderDialogItem[] = [];

  /**
   * Angular 生命週期鉤子 (ngOnInit)
   * 複製外部傳入的題目清單至本地暫存陣列，避免直接異動原始陣列。
   */
  public ngOnInit(): void {
    this.items = [...this.initialQuestions];
  }

  /**
   * 處理 CDK Drag & Drop 拖曳放置排序事件
   * @param event CDK 拖曳事件物件，包含移動前與移動後的索引位置
   */
  protected dropItem(event: CdkDragDrop<ReorderDialogItem[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    moveItemInArray(this.items, event.previousIndex, event.currentIndex);
  }

  /**
   * 確認並套用排序結果
   * 清空父層 questionArray 並依照目前 items 的新順序重新推入 FormGroups，最後關閉對話框。
   */
  protected onConfirm(): void {
    this._parent.questionArray.clear();
    this.items.forEach((item) => {
      this._parent.questionArray.push(item.control);
    });
    this.onCancel();
  }

  /**
   * 取消排序並關閉對話框
   * 不套用任何順序異動，直接關閉重排視窗。
   */
  protected onCancel(): void {
    this._parent.isReorderModalOpen = false;
  }
}
