/**
 * ============================================================================
 * 檔案名稱: survey-design.component.spec.ts
 * 測試對象: SurveyDesignComponent (題目與選項增刪、跳題鎖定與清除、排序、各階段欄位檢核、組裝設計稿、預覽切換、離開確認)
 * ============================================================================
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { FormArray } from '@angular/forms';
import { Observable } from 'rxjs';
import { createDialogSpy, lastDialog } from '../../../../testing/test-doubles';
import { SurveyPayload } from '../../../@interfaces/survey-model';
import { DialogService } from '../../../@services/dialog.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { ValidationService } from '../../../@services/validation.service';
import { SurveyDesignComponent } from './survey-design.component';

describe('SurveyDesignComponent', () => {
  let fixture: ComponentFixture<SurveyDesignComponent>;
  let component: any;
  let dialog: jasmine.SpyObj<DialogService>;
  let emitter: jasmine.SpyObj<UserEmitterService>;

  /**
   * 取得第 n 題的表單群組
   * @param index 題目索引
   * @returns 題目 FormGroup
   */
  const question = (index: number) => component.questionArray.at(index);

  /**
   * 填入一份可以通過檢核的最小問卷 (一題文字題)
   */
  const fillValidSurvey = (): void => {
    component.formBuilderGroup.patchValue({ title: '滿意度', category: '顧客滿意度' });
    question(0).patchValue({ type: 'text', description: '有什麼建議？' });
  };

  /**
   * 執行送出檢核並回傳錯誤訊息 (沒有錯誤時為空字串)
   * @returns 錯誤對話框訊息
   */
  const validate = (): string => {
    dialog.showDialog.calls.reset();
    component.checkSurveyData();
    return dialog.showDialog.calls.count() > 0 ? lastDialog(dialog).message : '';
  };

  beforeEach(async () => {
    dialog = createDialogSpy();
    emitter = jasmine.createSpyObj('UserEmitterService', ['emitDesignSurvey']);
    await TestBed.configureTestingModule({
      imports: [SurveyDesignComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DialogService, useValue: dialog },
        { provide: UserEmitterService, useValue: emitter },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SurveyDesignComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('開啟時預設一題、三個空白選項；日期最小值為本地今天', () => {
    expect(component.questionArray.length).toBe(1);
    expect(component.getOptionsArray(0).length).toBe(3);
    expect(component.todayDate).toBe(TestBed.inject(ValidationService).getLocalDate());
  });

  describe('題目與選項編輯', () => {
    it('新增、插入、收合題目；新增與刪除選項 (至少保留一個)', () => {
      component.addQuestion();
      component.insertQuestionAt(0);
      component.toggleQuestion(0);
      expect(component.questionArray.length).toBe(3);
      expect(question(0).get('isCollapsed').value).toBeTrue();

      component.addOption(1);
      expect(component.getOptionsArray(1).length).toBe(4);
      [0, 0, 0, 0].forEach(() => component.removeOption(1, 0));
      expect(component.getOptionsArray(1).length).toBe(1);
      expect(component.getOptionLabel(8)).toBe('09');
      expect(component.detectTextLength('')).toBe('0');
      expect(component.detectTextLength('abc')).toBe('3');
    });

    it('拖曳選項調整順序；拖回原位不變', () => {
      const options: FormArray = component.getOptionsArray(0);
      options.at(0).patchValue({ text: 'A' });
      options.at(2).patchValue({ text: 'C' });

      component.dropOption({ previousIndex: 0, currentIndex: 2 } as CdkDragDrop<FormArray>, 0);
      component.dropOption({ previousIndex: 1, currentIndex: 1 } as CdkDragDrop<FormArray>, 0);

      expect(options.controls.map((c) => c.get('text')!.value)).toEqual(['', 'C', 'A']);
    });

    it('跳題：列出後面的題目、標示被哪些選項鎖定；刪除目標題目時清除指向它的跳題設定', () => {
      component.addQuestion();
      component.addQuestion();
      question(0).patchValue({ type: 'radio' });
      question(2).patchValue({ description: '這是一段超過十八個字的很長很長很長題目描述' });
      const target = question(2).get('uid').value;
      component.getOptionsArray(0).at(1).patchValue({ text: '是', enableUnlockQuestion: true, unlockTargetUid: target });

      const subsequent = component.getSubsequentQuestions(0);
      expect(subsequent.map((s: { uid: string }) => s.uid)).toEqual([question(1).get('uid').value, target]);
      expect(subsequent[0].displayLabel).toBe('問題 2：未命名題目');
      expect(subsequent[1].displayLabel).toContain('...');
      expect(component.getLockSources(target)).toBe('【問題 01】選項 02（是）');
      expect(component.getLockSources('')).toBeNull();

      component.removeQuestion(2);
      expect(component.getOptionsArray(0).at(1).value).toEqual(jasmine.objectContaining({ enableUnlockQuestion: false, unlockTargetUid: '' }));
    });

    it('排序對話框：題目描述過長時截斷；開啟時設定旗標', () => {
      question(0).patchValue({ description: '一二三四五六七八九十一二三四五六七八九十一二三四五六' });

      const [item] = component.getReorderDialogItems();
      component.openReorderModal();

      expect(item.description.endsWith('...')).toBeTrue();
      expect(component.isReorderModalOpen).toBeTrue();
    });

    it('上傳圖片：壓縮後轉成 base64 存入前言與題目；沒有選檔時不變', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 2000;
      canvas.height = 10;
      const blob = await new Promise<Blob>((resolve) => canvas.toBlob((b) => resolve(b!), 'image/png'));
      const file = new File([blob], 'cover.png', { type: 'image/png' });
      const changeEvent = { target: { files: [file] } } as unknown as Event;

      await component.onDescriptionImageChange(changeEvent);
      await component.onQuestionImageChange(changeEvent, 0);
      await component.onQuestionImageChange({ target: { files: [] } } as unknown as Event, 0);

      expect(component.formBuilderGroup.value.descriptionImageBase64).toMatch(/^data:image\/png;base64,/);
      expect(question(0).value.hasImage).toBeTrue();
    });

    it('移除前言圖片與題目圖片時清空檔案欄位與資料', () => {
      const input = document.createElement('input');
      component.formBuilderGroup.patchValue({ hasDescriptionImage: true, descriptionImageBase64: 'data:x' });
      question(0).patchValue({ hasImage: true, imageBase64: 'data:y' });

      component.removeDescriptionImage(input);
      component.removeQuestionImage(0, input);

      expect(component.formBuilderGroup.value.descriptionImageBase64).toBe('');
      expect(question(0).value.hasImage).toBeFalse();
    });
  });

  describe('送出檢核', () => {
    it('全部空白：列出標題、分類、題型與題目內容錯誤', () => {
      const message = validate();

      ['問卷標題為必填', '問卷分類尚未選擇', '尚未選擇題目類型', '題目內容尚未填寫'].forEach((text) => expect(message).toContain(text));
      expect(emitter.emitDesignSurvey).not.toHaveBeenCalled();
    });

    it('前言與基本資料：開啟前言但沒內容、勾選圖片沒上傳、開啟基本資料但沒勾任何欄位', () => {
      fillValidSurvey();
      component.formBuilderGroup.patchValue({ hasDescription: true, hasDescriptionImage: true, collectBasicInfo: true });

      const message = validate();

      expect(message).toContain('尚未上傳圖片');
      expect(message).toContain('請至少輸入文字說明或上傳說明圖片');
      expect(message).toContain('請至少勾選一項欲蒐集的欄位');
    });

    it('題目層級：沒有題目、圖片未上傳、選項空白、跳題未選目標、時間與日期範圍顛倒', () => {
      fillValidSurvey();
      component.questionArray.clear();
      expect(validate()).toContain('問卷至少需建立一個問題');

      ['radio', 'multiRadio', 'time', 'date'].forEach(() => component.addQuestion());
      question(0).patchValue({ type: 'radio', description: 'Q1', hasImage: true });
      component.getOptionsArray(0).at(0).patchValue({ text: 'A', enableUnlockQuestion: true });
      question(1).patchValue({ type: 'multiRadio', description: 'Q2' });
      component.getOptionsArray(1).clear();
      question(2).patchValue({ type: 'time', description: 'Q3', hasTimeLimit: true, minTime: '18:00', maxTime: '09:00' });
      question(3).patchValue({ type: 'date', description: 'Q4', hasDateLimit: true, minDate: '2026-12-31', maxDate: '2026-01-01' });

      const message = validate();

      expect(message).toContain('問題 1：已勾選附加示意圖片');
      expect(message).toContain('問題 1 選項 02：內容尚未填寫');
      expect(message).toContain('問題 1 選項 01：已啟用題目開啟設定');
      expect(message).toContain('問題 2：至少需建立一個選項');
      expect(message).toContain('最早時間不能晚於');
      expect(message).toContain('最早日期不能晚於');
    });

    it('發布期間：未填日期時間、開始早於今天、結束不晚於開始', () => {
      fillValidSurvey();
      component.formBuilderGroup.patchValue({ hasTimeLimit: true });
      ['開始日期尚未填寫', '開始時間尚未填寫', '結束日期尚未填寫', '結束時間尚未填寫'].forEach((text) => expect(validate()).toContain(text));

      component.formBuilderGroup.patchValue({ startDate: '2000-01-01', startTime: '10:00', endDate: '2000-01-01', endTime: '09:00' });
      const message = validate();
      expect(message).toContain('開始日期不能早於今日');
      expect(message).toContain('結束時間必須晚於開始時間');

      component.formBuilderGroup.patchValue({ startDate: '2099-01-01', endDate: '2099-01-02' });
      expect(validate()).toBe('');
    });

    it('全部正確：組裝設計稿並送出，之後離開不再詢問', () => {
      fillValidSurvey();

      expect(validate()).toBe('');
      expect(emitter.emitDesignSurvey).toHaveBeenCalled();
      expect(component.canDeactivate()).toBeTrue();
    });
  });

  describe('組裝設計稿 formatSurveyData', () => {
    it('依題型只帶入相關欄位；跳題目標標示為鎖定', () => {
      component.formBuilderGroup.patchValue({
        title: 'T', category: 'C', hasDescription: true, description: '說明', hasDescriptionImage: true, descriptionImageBase64: 'data:d',
        hasTimeLimit: true, startDate: '2099-01-01', startTime: '08:00', endDate: '2099-01-02', endTime: '18:00',
        collectBasicInfo: true, requireName: true,
      });
      ['multiRadio', 'scale', 'time', 'date'].forEach(() => component.addQuestion());
      question(0).patchValue({ type: 'radio', description: 'Q1', hasImage: true, imageBase64: 'data:q' });
      component.getOptionsArray(0).at(0).patchValue({ text: 'A', enableUnlockQuestion: true, unlockTargetUid: question(1).get('uid').value });
      question(1).patchValue({ type: 'multiRadio', description: 'Q2' });
      question(2).patchValue({ type: 'scale', description: 'Q3', scaleType: 'scale-num-0-10' });
      question(3).patchValue({ type: 'time', description: 'Q4', hasTimeLimit: true, minTime: '09:00', timeValue: '10:00' });
      question(4).patchValue({ type: 'date', description: 'Q5', hasDateLimit: false, minDate: '2026-01-01' });

      const payload: SurveyPayload = component.formatSurveyData();
      const [q1, q2, q3, q4, q5] = payload.questions;

      expect(payload.timeLimit).toEqual({ startDate: '2099-01-01', startTime: '08:00', endDate: '2099-01-02', endTime: '18:00' });
      expect(payload.descriptionImageBase64).toBe('data:d');
      expect(payload.basicInfoRequirements.requireName).toBeTrue();
      expect(q1.optionsConfig?.length).toBe(3);
      expect(q1.imageBase64).toBe('data:q');
      expect(q2.options).toEqual(['', '', '']);
      expect(q2.optionsConfig).toBeUndefined();
      expect(q2.isLockedByOther).toBeTrue();
      expect(q3.scaleType).toBe('scale-num-0-10');
      expect([q4.minTime, q4.maxTime, q4.timeValue]).toEqual(['09:00', undefined, '10:00']);
      expect([q5.hasDateLimit, q5.minDate]).toEqual([false, undefined]);
    });

    it('未開啟前言與期限時不帶相關欄位', () => {
      component.formBuilderGroup.patchValue({ hasDescriptionImage: true, descriptionImageBase64: 'data:d' });

      const payload: SurveyPayload = component.formatSurveyData();

      expect(payload.timeLimit).toBeUndefined();
      expect(payload.description).toBeUndefined();
      expect(payload.hasDescriptionImage).toBeFalse();
      expect(payload.descriptionImageBase64).toBeUndefined();
    });
  });

  it('預覽切換：進入時把設計稿放進預覽狀態，再按一次離開', () => {
    const state = TestBed.inject(SurveyStateService);
    fillValidSurvey();

    component.togglePreviewMode();
    expect(state.isPreviewMode()).toBeTrue();
    expect(state.currentSurvey().title).toBe('滿意度');

    component.togglePreviewMode();
    expect(state.isPreviewMode()).toBeFalse();
  });

  it('離開確認：尚未送出時詢問；元件銷毀時重設預覽狀態', () => {
    let leave: boolean | undefined;
    (component.canDeactivate() as Observable<boolean>).subscribe((value) => (leave = value));
    expect(leave).toBeTrue();
    expect(lastDialog(dialog).title).toBe('跳出確認');

    const state = TestBed.inject(SurveyStateService);
    state.setForPreviewMode({ title: 'x' } as SurveyPayload);
    fixture.destroy();
    expect(state.isPreviewMode()).toBeFalse();
  });
});
