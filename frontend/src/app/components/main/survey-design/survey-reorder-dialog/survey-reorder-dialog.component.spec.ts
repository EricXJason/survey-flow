/**
 * ============================================================================
 * 檔案名稱: survey-reorder-dialog.component.spec.ts
 * 測試對象: SurveyReorderDialogComponent (拖曳調整題目順序、確認後寫回父層表單、取消與 Esc 關閉)
 * 測試方式: 父層 SurveyDesignComponent 以只含 questionArray 與 isReorderModalOpen 的假物件取代
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import { SurveyDesignComponent } from '../survey-design.component';
import { ReorderDialogItem, SurveyReorderDialogComponent } from './survey-reorder-dialog.component';

describe('SurveyReorderDialogComponent', () => {
  let component: any;
  let parent: { questionArray: FormArray; isReorderModalOpen: boolean };
  let items: ReorderDialogItem[];

  /**
   * 模擬一次拖放
   * @param previousIndex 原位置
   * @param currentIndex 新位置
   * @returns CdkDragDrop 事件
   */
  const drop = (previousIndex: number, currentIndex: number) =>
    ({ previousIndex, currentIndex } as CdkDragDrop<ReorderDialogItem[]>);

  /**
   * 讀出父層表單目前的題目順序
   * @returns 題目 uid 陣列
   */
  const parentOrder = (): string[] => parent.questionArray.controls.map((c) => c.get('uid')!.value);

  beforeEach(async () => {
    items = ['Q1', 'Q2', 'Q3'].map((uid) => ({
      uid, description: uid, control: new FormGroup({ uid: new FormControl(uid) }),
    }));
    parent = { questionArray: new FormArray(items.map((i) => i.control)), isReorderModalOpen: true };
    await TestBed.configureTestingModule({
      imports: [SurveyReorderDialogComponent],
      providers: [{ provide: SurveyDesignComponent, useValue: parent }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SurveyReorderDialogComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('initialQuestions', items);
    fixture.detectChanges();
  });

  it('拖曳只改變對話框內的順序；確認後才寫回父層並關閉', () => {
    component.dropItem(drop(0, 2));
    expect(component.items.map((i: ReorderDialogItem) => i.uid)).toEqual(['Q2', 'Q3', 'Q1']);
    expect(parentOrder()).toEqual(['Q1', 'Q2', 'Q3']);

    component.onConfirm();

    expect(parentOrder()).toEqual(['Q2', 'Q3', 'Q1']);
    expect(parent.isReorderModalOpen).toBeFalse();
  });

  it('拖回原位不改變順序', () => {
    component.dropItem(drop(1, 1));

    expect(component.items.map((i: ReorderDialogItem) => i.uid)).toEqual(['Q1', 'Q2', 'Q3']);
  });

  it('按 Esc 取消：關閉且父層順序不變；其他按鍵不處理', () => {
    component.dropItem(drop(0, 2));

    component.handleKeyDown(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(parent.isReorderModalOpen).toBeTrue();

    component.handleKeyDown(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(parent.isReorderModalOpen).toBeFalse();
    expect(parentOrder()).toEqual(['Q1', 'Q2', 'Q3']);
  });
});
