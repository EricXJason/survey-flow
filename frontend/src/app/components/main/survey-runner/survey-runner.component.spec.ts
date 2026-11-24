/**
 * ============================================================================
 * 檔案名稱: survey-runner.component.spec.ts
 * 測試對象: SurveyRunnerComponent (載入問卷與開放狀態判斷、基本資料欄位、跳題解鎖、各題型作答、
 *           必填檢核、送出內容與選項編號、預覽模式、量表選項、返回按鈕、離開確認)
 * ============================================================================
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { createDialogSpy, lastDialog } from '../../../../testing/test-doubles';
import { SurveyBasicInfoRequirements, SurveyPayload, SurveyQuestion, SurveySubmissionPayload } from '../../../@interfaces/survey-model';
import { DialogService } from '../../../@services/dialog.service';
import { SurveyGetterService } from '../../../@services/survey-getter.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { SurveyRunnerComponent } from './survey-runner.component';

/**
 * 建立一題
 * @param uid 題目代碼
 * @param type 題型
 * @param overrides 要覆寫之欄位
 * @returns 題目
 */
function q(uid: string, type: string, overrides: Partial<SurveyQuestion> = {}): SurveyQuestion {
  return { uid, id: Number(uid.replace('Q', '')), type, isRequired: false, hasImage: false, description: uid, ...overrides };
}

/**
 * 建立一份問卷：Q1 單選 (選「是」解鎖 Q2)、Q2 必填文字題 (被鎖定)、Q3 必填複選、Q4 量表、Q5 是非、Q6 日期、Q7 時間
 * @param overrides 要覆寫之欄位
 * @returns 問卷
 */
function survey(overrides: Partial<SurveyPayload> = {}): SurveyPayload {
  return {
    id: '5', title: '健康調查', category: '健康', isPublished: true, isOpen: true, hasTimeLimit: false, hasDescription: false,
    hasDescriptionImage: false, basicInfoRequirements: { collectBasicInfo: false } as SurveyBasicInfoRequirements,
    questions: [
      q('Q1', 'radio', {
        options: ['是', '否'],
        optionsConfig: [
          { id: 11, text: '是', enableUnlockQuestion: true, unlockTargetUid: 'Q2' },
          { id: 12, text: '否', enableUnlockQuestion: false, unlockTargetUid: '' },
        ],
      }),
      q('Q2', 'text', { isRequired: true, isLockedByOther: true }),
      q('Q3', 'multiRadio', {
        isRequired: true, options: ['A', 'B', 'C'],
        optionsConfig: [
          { id: 31, text: 'A', enableUnlockQuestion: false, unlockTargetUid: '' },
          { id: 32, text: 'B', enableUnlockQuestion: false, unlockTargetUid: '' },
          { id: 33, text: 'C', enableUnlockQuestion: false, unlockTargetUid: '' },
        ],
      }),
      q('Q4', 'scale', { minRating: 1, maxRating: 5 }),
      q('Q5', 't/f', {
        optionsConfig: [
          { id: 51, text: '是', enableUnlockQuestion: false, unlockTargetUid: '', sortOrder: 1 },
          { id: 52, text: '否', enableUnlockQuestion: false, unlockTargetUid: '', sortOrder: 2 },
        ],
      }),
      q('Q6', 'date', { dateValue: '2026-01-01' }),
      q('Q7', 'time'),
    ],
    ...overrides,
  };
}

describe('SurveyRunnerComponent', () => {
  let fixture: ComponentFixture<SurveyRunnerComponent>;
  let component: any;
  let getter: jasmine.SpyObj<SurveyGetterService>;
  let emitter: jasmine.SpyObj<UserEmitterService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let state: SurveyStateService;
  let router: Router;
  let queryParams: Record<string, string>;

  /**
   * 以目前的網址參數建立元件 (沒有 surveyId 時使用 SurveyStateService 中的問卷)
   * @param current 放入狀態服務的問卷 (選填)
   */
  function create(current?: SurveyPayload): void {
    if (current) {
      state.setForRunMode(current);
    }
    fixture = TestBed.createComponent(SurveyRunnerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  /**
   * 執行送出並回傳錯誤訊息 (通過時為空字串)
   * @returns 錯誤對話框訊息
   */
  const submit = (): string => {
    dialog.showDialog.calls.reset();
    component.checkRunnerData();
    return dialog.showDialog.calls.count() > 0 ? lastDialog(dialog).message : '';
  };

  /**
   * 取得最後一次送出的填答內容
   * @returns 填答內容
   */
  const submitted = (): SurveySubmissionPayload => emitter.emitSubmitSurvey.calls.mostRecent().args[0];

  /**
   * 建立一個假的 input 事件
   * @param props 事件目標屬性
   * @returns Event
   */
  const event = (props: Record<string, unknown>): Event => ({ target: { ...props } } as unknown as Event);

  beforeEach(async () => {
    queryParams = {};
    getter = jasmine.createSpyObj('SurveyGetterService', ['getSurveyById', 'getSurveyByShareToken']);
    emitter = jasmine.createSpyObj('UserEmitterService', ['emitSubmitSurvey']);
    dialog = createDialogSpy();
    await TestBed.configureTestingModule({
      imports: [SurveyRunnerComponent],
      providers: [
        provideRouter([]),
        { provide: SurveyGetterService, useValue: getter },
        { provide: UserEmitterService, useValue: emitter },
        { provide: DialogService, useValue: dialog },
      ],
    }).compileComponents();
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { get queryParams() { return queryParams; }, routeConfig: { path: 'survey-runner' } } },
    });
    state = TestBed.inject(SurveyStateService);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => fixture?.destroy());

  describe('載入與開放狀態', () => {
    it('網址帶 surveyId：載入問卷並切成填答模式', () => {
      queryParams = { surveyId: '5', from: 'lobby' };
      getter.getSurveyById.and.returnValue(of(survey()));

      create();

      expect(getter.getSurveyById).toHaveBeenCalledWith('5', false);
      expect(state.isPreviewMode()).toBeFalse();
      expect(component.isUnavailable()).toBeFalse();
      expect(component.runnerFormGroup.contains('Q3')).toBeTrue();
    });

    it('載入期間只顯示載入提示、不渲染表單欄位；資料回來並建好表單後才顯示填答內容', () => {
      queryParams = { share: 'LPOQP2Z38hvCM9OFiMM84Q' };
      const pending = new Subject<SurveyPayload | null>();
      getter.getSurveyByShareToken.and.returnValue(pending.asObservable());

      create(survey());

      expect(component.isLoadingSurvey()).toBeTrue();
      expect(fixture.nativeElement.querySelector('.runner-loading')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('form')).toBeNull();

      pending.next(survey());
      fixture.detectChanges();

      expect(component.isLoadingSurvey()).toBeFalse();
      expect(fixture.nativeElement.querySelector('.runner-loading')).toBeNull();
      expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
    });

    it('網址帶 share：以分享代碼載入 (優先於 surveyId)，切成填答模式並顯示返回按鈕', () => {
      queryParams = { share: 'LPOQP2Z38hvCM9OFiMM84Q', surveyId: '5' };
      getter.getSurveyByShareToken.and.returnValue(of(survey()));

      create();

      expect(getter.getSurveyByShareToken).toHaveBeenCalledWith('LPOQP2Z38hvCM9OFiMM84Q');
      expect(getter.getSurveyById).not.toHaveBeenCalled();
      expect(state.isPreviewMode()).toBeFalse();
      expect(component.isUnavailable()).toBeFalse();
    });

    it('從問卷管理進入 (或 preview=true)：以預覽模式載入，不檢查開放狀態', () => {
      queryParams = { id: '5', from: 'survey-management' };
      getter.getSurveyById.and.returnValue(of(survey({ isOpen: false })));

      create();

      expect(getter.getSurveyById).toHaveBeenCalledWith('5', true);
      expect(component.isPreview).toBeTrue();
      expect(component.isUnavailable()).toBeFalse();
    });

    it('查無問卷或載入失敗：依錯誤訊息顯示對應標題', () => {
      queryParams = { surveyId: '5' };
      const titleFor = (result: Observable<SurveyPayload | null>): string => {
        getter.getSurveyById.and.returnValue(result);
        create(survey({ questions: [] }));
        return component.unavailableTitle();
      };

      expect(titleFor(of(null))).toBe('查無此問卷專案');
      expect(titleFor(throwError(() => new Error('此問卷已截止填答')))).toBe('問卷填答已截止');
      expect(titleFor(throwError(() => new Error('此問卷尚未開放填答')))).toBe('問卷尚未開始開放填答');
      expect(titleFor(throwError(() => new Error('無法連線至伺服器')))).toBe('本問卷目前未開放填答');
      expect(titleFor(throwError(() => new Error('')))).toBe('本問卷目前未開放填答');
      expect(component.unavailableMessage()).toBe('此問卷目前暫不開放填答。');
    });

    it('沒有 surveyId 時使用目前狀態中的問卷：發起人關閉填答、尚未開始、已截止', () => {
      create(survey({ isOpen: false }));
      expect(component.unavailableTitle()).toBe('問卷目前未開放填答');

      create(survey({ isOpen: undefined, isPublished: true, hasTimeLimit: true, timeLimit: { startDate: '2999-01-01', startTime: '', endDate: '2999-02-01', endTime: '' } }));
      expect(component.unavailableTitle()).toBe('問卷尚未開始開放填答');
      expect(component.unavailableTimeRange()).toBe('填答有效期間：2999/01/01 00:00 至 2999/02/01 23:59');

      create(survey({ hasTimeLimit: true, timeLimit: { startDate: '2999-01-01', startTime: '09:00', endDate: '', endTime: '' } }));
      expect(component.unavailableTimeRange()).toBe('填答開放時間：2999/01/01 09:00 起');

      create(survey({ hasTimeLimit: true, timeLimit: { startDate: '2000-01-01', startTime: '', endDate: '2000-02-01', endTime: '18:00' } }));
      expect(component.unavailableTitle()).toBe('問卷填答已截止');
      expect(component.unavailableTimeRange()).toBe('填答有效期間：2000/01/01 00:00 至 2000/02/01 18:00');

      create(survey({ hasTimeLimit: true, timeLimit: { startDate: '', startTime: '', endDate: '2000-02-01', endTime: '' } }));
      expect(component.unavailableTimeRange()).toBe('填答截止時間：至 2000/02/01 23:59');

      create(survey({ hasTimeLimit: true, timeLimit: { startDate: '2000-01-01', startTime: '', endDate: '2999-01-01', endTime: '' } }));
      expect(component.isUnavailable()).toBeFalse();
    });
  });

  describe('基本資料欄位', () => {
    const requirements = {
      collectBasicInfo: true, requireName: true, requireAge: true, requireBirthday: true, requireJobIndustry: true, requireEmail: true,
    } as SurveyBasicInfoRequirements;

    it('只建立問卷要求收集的欄位，且都是必填並套用格式驗證', () => {
      create(survey({ basicInfoRequirements: requirements }));
      const form = component.runnerFormGroup;

      expect(['name', 'age', 'birthday', 'jobIndustry', 'email'].every((key) => form.contains(key))).toBeTrue();
      expect(form.contains('gender')).toBeFalse();
      form.get('name').setValue('Tom');
      form.get('email').setValue('bad');
      expect(form.get('name').invalid && form.get('email').invalid).toBeTrue();
      expect(form.get('age').value).toBeNull();
    });

    it('沒有開啟收集基本資料時不建立任何欄位', () => {
      create(survey({ basicInfoRequirements: { ...requirements, collectBasicInfo: false } }));

      expect(component.runnerFormGroup.contains('name')).toBeFalse();
    });

    it('輸入處理：去除空白、數字欄位轉數字 (非正數為 null)、生日年份超過 4 位數截斷', () => {
      create(survey({ basicInfoRequirements: requirements }));
      const birthdayInput = { value: '20260-01-01' };

      component.onFieldInput('name', event({ value: ' 王小明 ' }));
      component.onFieldInput('age', event({ value: '30' }));
      component.onFieldInput('birthday', { target: birthdayInput } as unknown as Event);
      component.onFieldInput('missing', event({ value: 'x' }));
      component.onFieldInput('name', { target: null } as unknown as Event);

      expect(component.runnerFormGroup.get('name').value).toBe('王小明');
      expect(component.runnerFormGroup.get('age').value).toBe(30);
      expect(birthdayInput.value).toBe('2026-01-01');
      component.onFieldInput('age', event({ value: '-1' }));
      expect(component.runnerFormGroup.get('age').value).toBeNull();
    });

    it('欄位錯誤只在碰過之後顯示', () => {
      create(survey({ basicInfoRequirements: requirements }));

      expect(component.isFieldInvalid('name')).toBeFalse();
      component.onFieldBlur('name');
      component.onFieldBlur('missing');
      expect(component.isFieldInvalid('name')).toBeTrue();
      expect(component.isFieldInvalid('missing')).toBeFalse();
    });

    it('產業選「其他」時以自填內容為值，自填空白時為「其他」', () => {
      create(survey({ basicInfoRequirements: requirements }));
      const industry = () => component.runnerFormGroup.get('jobIndustry').value;

      component.onIndustryChange(event({ value: '資訊' }));
      expect(industry()).toBe('資訊');
      component.onIndustryChange(event({ value: '其他' }));
      expect(industry()).toBe('其他');
      component.onCustomIndustryInput(event({ value: ' 農業 ' }));
      expect(industry()).toBe('農業');
      component.onIndustryChange(event({ value: '其他' }));
      expect(industry()).toBe('農業');
      component.onCustomIndustryInput(event({ value: '' }));
      expect(industry()).toBe('其他');
    });
  });

  describe('跳題與作答', () => {
    beforeEach(() => create(survey()));

    it('Q2 預設被鎖定；Q1 選「是」才解鎖，選「否」再次鎖定', () => {
      const q2 = component.survey.questions[1];
      expect(component.isQuestionUnlocked(q2)).toBeFalse();

      component.selectRadioOption('Q1', '是', 0);
      expect(component.isQuestionUnlocked(q2)).toBeTrue();
      expect(component.isRadioOptionSelected('Q1', '是', 0)).toBeTrue();

      component.selectRadioOption('Q1', '否', 1);
      expect(component.isQuestionUnlocked(q2)).toBeFalse();
      expect(component.isQuestionUnlocked(component.survey.questions[0])).toBeTrue();
    });

    it('沒有選取索引時，依答案文字比對解鎖 (含是非題與複選題的後備比對)', () => {
      const target = q('Q9', 'text', { isLockedByOther: true });
      component.survey.questions.push(target);
      component.survey.questions[4].optionsConfig[0] = { text: '是', enableUnlockQuestion: true, unlockTargetUid: '9', sortOrder: 1 };
      component.runnerFormGroup.get('Q5').setValue('true');
      expect(component.isQuestionUnlocked(target)).toBeTrue();

      component.runnerFormGroup.get('Q5').setValue('否');
      component.survey.questions[4].optionsConfig[1] = { text: '否', enableUnlockQuestion: true, unlockTargetUid: 'Q9', sortOrder: 2 };
      expect(component.isQuestionUnlocked(target)).toBeTrue();
    });

    it('複選題勾選的選項可解鎖題目；後備比對也支援陣列答案', () => {
      const target = q('Q9', 'text', { isLockedByOther: true });
      component.survey.questions.push(target);
      component.survey.questions[2].optionsConfig[1] = { id: 32, text: 'B', enableUnlockQuestion: true, unlockTargetUid: 'Q9' };

      component.onMultiRadioChange('Q3', 'B', 1, event({ checked: true }));
      expect(component.isQuestionUnlocked(target)).toBeTrue();

      component.onMultiRadioChange('Q3', 'B', 1, event({ checked: false }));
      expect(component.isQuestionUnlocked(target)).toBeFalse();

      component.survey.questions[0].type = 'legacy';
      component.survey.questions[0].optionsConfig[0].unlockTargetUid = 'Q9';
      component.runnerFormGroup.get('Q1').setValue(['是']);
      expect(component.isQuestionUnlocked(target)).toBeTrue();
    });

    it('複選題：依勾選順序排序存入表單；重複勾選不重複；未記錄索引時依表單值判斷是否勾選', () => {
      component.onMultiRadioChange('Q3', 'C', 2, event({ checked: true }));
      component.onMultiRadioChange('Q3', 'A', 0, event({ checked: true }));
      component.onMultiRadioChange('Q3', 'A', 0, event({ checked: true }));

      expect(component.runnerFormGroup.get('Q3').value).toEqual(['A', 'C']);
      expect(component.isMultiRadioChecked('Q3', 'A', 0)).toBeTrue();
      expect(component.isMultiRadioChecked('Q3', 'B', 1)).toBeFalse();

      component.selectedMultiRadioIndexMap.set({});
      expect(component.isMultiRadioChecked('Q3', 'C', 2)).toBeTrue();
      expect(component.isMultiRadioChecked('Q3', 'B', 1)).toBeFalse();
    });

    it('量表、是非、日期時間作答；日期年份超過 4 位數截斷', () => {
      const dateInput = { value: '20260-05-01' };

      component.selectScaleOption('Q4', 4);
      component.selectTfOption('Q5', 'true');
      component.onDateTimeChange('Q6', { target: dateInput } as unknown as Event);
      component.onDateTimeChange('Q7', event({ value: '' }));
      component.selectScaleOption('missing', 1);
      component.selectTfOption('missing', 'true');
      component.selectRadioOption('missing', 'x');

      expect(component.runnerFormGroup.get('Q4').value).toBe('4');
      expect(component.runnerFormGroup.get('Q5').value).toBe('true');
      expect(component.runnerFormGroup.get('Q6').value).toBe('2026-05-01');
      expect(component.runnerFormGroup.get('Q7').value).toBe('');
    });

    it('解鎖來源摘要：有預先產生的摘要就用；否則依選項設定組出', () => {
      expect(component.getUnlockSourcesSummary(component.survey.questions[1])).toBe('【問題 01】選項 01（是）');
      expect(component.getUnlockSourcesSummary({ ...component.survey.questions[1], lockedBySummary: '自訂' })).toBe('自訂');
      expect(component.getUnlockSourcesSummary(component.survey.questions[3])).toBe('');
    });
  });

  describe('送出', () => {
    beforeEach(() => create(survey()));

    it('必填未完成：列出題號；被鎖定隱藏的必填題不檢查', () => {
      const message = submit();

      expect(message).toContain('問題 3：此必填項目尚未完成');
      expect(message).not.toContain('問題 2');
      expect(emitter.emitSubmitSurvey).not.toHaveBeenCalled();
    });

    it('解鎖後的必填題也要填', () => {
      component.selectRadioOption('Q1', '是', 0);
      component.onMultiRadioChange('Q3', 'A', 0, event({ checked: true }));

      expect(submit()).toContain('問題 2：此必填項目尚未完成');
    });

    it('全部完成：送出可見題目的答案與選項編號 (單選依索引、複選依索引、是非依語意)', () => {
      component.selectRadioOption('Q1', '是', 0);
      component.runnerFormGroup.get('Q2').setValue('每天運動');
      component.onMultiRadioChange('Q3', 'A', 0, event({ checked: true }));
      component.onMultiRadioChange('Q3', 'C', 2, event({ checked: true }));
      component.selectTfOption('Q5', 'false');

      expect(submit()).toBe('');
      const payload = submitted();
      const byUid = (uid: string) => payload.answers.find((a) => a.questionUid === uid)!;
      expect(payload).toEqual(jasmine.objectContaining({ surveyId: '5', surveyTitle: '健康調查', basicInfo: {} }));
      expect(byUid('Q1').selectedOptionId).toBe(11);
      expect(byUid('Q2').answer).toBe('每天運動');
      expect(byUid('Q3').selectedOptionIds).toEqual([31, 33]);
      expect(byUid('Q5').selectedOptionId).toBe(52);
      expect(byUid('Q6').answer).toBe('2026-01-01');
      expect(component.canDeactivate()).toBeTrue();
    });

    it('由分享連結 (?share=) 進入時，送出內容帶上分享代碼；由大廳進入時為 undefined', () => {
      const fillRequired = (): void => {
        component.selectRadioOption('Q1', '是', 0);
        component.runnerFormGroup.get('Q2').setValue('每天運動');
        component.onMultiRadioChange('Q3', 'A', 0, event({ checked: true }));
        component.selectTfOption('Q5', 'false');
      };

      fillRequired();
      expect(submit()).toBe('');
      expect(submitted().shareToken).toBeUndefined();

      queryParams = { share: 'LPOQP2Z38hvCM9OFiMM84Q' };
      getter.getSurveyByShareToken.and.returnValue(of(survey()));
      create();
      fillRequired();
      expect(submit()).toBe('');
      expect(submitted().shareToken).toBe('LPOQP2Z38hvCM9OFiMM84Q');
    });

    it('沒有選取索引時以文字比對選項編號 (單選與複選)', () => {
      component.runnerFormGroup.get('Q1').setValue('否');
      component.runnerFormGroup.setControl('Q3', component._formBuilder.array([component._formBuilder.control('B')]));
      component.selectTfOption('Q5', 'yes');

      expect(submit()).toBe('');
      const answers = submitted().answers;
      expect(answers.find((a) => a.questionUid === 'Q1')!.selectedOptionId).toBe(12);
      expect(answers.find((a) => a.questionUid === 'Q3')!.selectedOptionIds).toEqual([32]);
      expect(answers.find((a) => a.questionUid === 'Q5')!.selectedOptionId).toBe(51);
    });

    it('表單沒有值但畫面上已選取時，送出前從畫面補回答案 (防止瀏覽器自動填入未同步)', () => {
      const radio = fixture.nativeElement.querySelector('input[name="radio_Q1"][value="否"]') as HTMLInputElement
        ?? fixture.nativeElement.querySelector('input[name="radio_Q1"]');
      radio.checked = true;
      component.onMultiRadioChange('Q3', 'A', 0, event({ checked: true }));
      const scale = fixture.nativeElement.querySelector('input[name="scale_Q4"]') as HTMLInputElement;
      scale.checked = true;
      const tf = fixture.nativeElement.querySelector('input[name="tf_radio_Q5"]') as HTMLInputElement;
      tf.checked = true;

      expect(submit()).toBe('');
      const answers = submitted().answers;
      expect(answers.find((a) => a.questionUid === 'Q1')!.answer).toBe(radio.value);
      expect(answers.find((a) => a.questionUid === 'Q4')!.answer).toBe(scale.value);
      expect(answers.find((a) => a.questionUid === 'Q5')!.answer).toBe(tf.value);
    });

    it('複選題也會從畫面補回，且只讀取本題的勾選框', () => {
      (fixture.nativeElement.querySelector('input[name="multi_Q3_1"]') as HTMLInputElement).checked = true;

      expect(submit()).toBe('');
      expect(submitted().answers.find((a) => a.questionUid === 'Q3')!.answer).toEqual(['B']);
    });

    it('預覽模式不送出，只提示說明', () => {
      state.setForPreviewMode(survey());

      component.checkRunnerData();

      expect(lastDialog(dialog).title).toBe('預覽模式說明');
      expect(emitter.emitSubmitSurvey).not.toHaveBeenCalled();
    });
  });

  describe('基本資料送出', () => {
    it('基本資料錯誤列在題目錯誤之前；填好後送出 basicInfo', () => {
      create(survey({
        basicInfoRequirements: { collectBasicInfo: true, requireName: true, requireGender: true } as SurveyBasicInfoRequirements,
        questions: [q('Q1', 'text')],
      }));

      expect(submit()).toContain('基本資料：姓名');

      component.onFieldInput('name', event({ value: '王小明' }));
      // 表單值是空的，但畫面上的下拉選單已選好 (例如瀏覽器自動填入)，送出前會從畫面補回
      const genderSelect = fixture.nativeElement.querySelector('[formControlName="gender"]') as HTMLSelectElement;
      genderSelect.selectedIndex = 1;

      expect(submit()).toBe('');
      expect(submitted().basicInfo).toEqual(jasmine.objectContaining({ name: '王小明' }));
      expect(['male', 'female', 'other']).toContain(submitted().basicInfo['gender'] as string);
    });
  });

  describe('基本資料從畫面補回', () => {
    beforeEach(() => create(survey({
      basicInfoRequirements: {
        collectBasicInfo: true, requireName: true, requireAge: true, requireGender: true, requireJobIndustry: true,
      } as SurveyBasicInfoRequirements,
      questions: [],
    })));

    /**
     * 取得基本資料欄位的畫面元素
     * @param key 欄位名稱
     * @returns 輸入框或下拉選單
     */
    const field = (key: string): HTMLInputElement & HTMLSelectElement =>
      fixture.nativeElement.querySelector(`[formControlName="${key}"]`);

    it('年齡轉成數字、性別依選項文字判斷、產業取下拉值；表單已有值者不被畫面空值覆蓋', () => {
      field('name').value = ' 王小明 ';
      field('age').value = '28';
      field('gender').selectedIndex = 3;
      field('jobIndustry').selectedIndex = 1;

      expect(submit()).toBe('');
      const info = submitted().basicInfo as Record<string, unknown>;
      expect(info['name']).toBe('王小明');
      expect(info['age']).toBe(28);
      expect(info['gender']).toBe('other');
      expect(info['jobIndustry']).toBe(field('jobIndustry').value);
    });

    it('性別選男性或女性；產業選其他並自填時以自填內容為準；年齡非正數不採用', () => {
      component.onFieldInput('name', event({ value: '王小明' }));
      field('age').value = '0';
      component.runnerFormGroup.get('age').setValue(30);
      field('gender').selectedIndex = 1;
      component.onIndustryChange(event({ value: '其他' }));
      component.onCustomIndustryInput(event({ value: '農業' }));

      expect(submit()).toBe('');
      const info = submitted().basicInfo as Record<string, unknown>;
      expect(info['age']).toBe(30);
      expect(info['gender']).toBe('male');
      expect(info['jobIndustry']).toBe('農業');

      field('gender').selectedIndex = 2;
      expect(submit()).toBe('');
      expect((submitted().basicInfo as Record<string, unknown>)['gender']).toBe('female');
    });
  });

  describe('量表選項', () => {
    beforeEach(() => create(survey()));

    it('優先使用 min~max，其次從題目文字推斷，再依量表代碼，最後預設 5 級滿意度', () => {
      expect(component.getScaleOptions({ minRating: 0, maxRating: 2 })).toEqual(['0', '1', '2']);
      expect(component.getScaleOptions({ description: '請以 NPS 評分' }).length).toBe(11);
      expect(component.getScaleOptions({ description: '1~7 分' })).toEqual(['1', '2', '3', '4', '5', '6', '7']);
      expect(component.getScaleOptions('scale-num-1-10').length).toBe(10);
      expect(component.getScaleOptions('scale-text-frequency-5')[0]).toBe('從不');
      expect(component.getScaleOptions('unknown')[4]).toBe('非常滿意');
      expect(component.getScaleOptions(null)[0]).toBe('非常不滿意');
      expect(component.getScaleOptions({ minRating: 5, maxRating: 1, scaleType: 'scale-text-satisfaction-4' }).length).toBe(4);
    });
  });

  describe('返回按鈕與離開確認', () => {
    it('依來源顯示返回文字並導向對應頁面；沒有來源時回上一頁', () => {
      queryParams = { from: 'survey-management' };
      create(survey());
      expect(component.backButtonLabel).toBe('返回問卷管理');
      component.onBack();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/survey-management');

      queryParams = { from: 'lobby' };
      expect(component.backButtonLabel).toBe('返回問卷大廳');
      component.onBack();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/lobby');

      queryParams = {};
      expect(component.backButtonLabel).toBe('返回上一頁');
      expect(component.shouldShowBackButton).toBeTrue();
      spyOn(window.history, 'back');
      spyOnProperty(window.history, 'length').and.returnValue(1);
      component.onBack();
      expect(router.navigateByUrl).toHaveBeenCalledTimes(3);
    });

    it('有歷史紀錄時回上一頁', () => {
      create(survey());
      spyOn(window.history, 'back');
      spyOnProperty(window.history, 'length').and.returnValue(3);

      component.onBack();

      expect(window.history.back).toHaveBeenCalled();
    });

    it('有作答未送出時離開要確認；預覽模式或沒作答時直接離開', () => {
      create(survey());
      expect(component.canDeactivate()).toBeTrue();

      component.selectScaleOption('Q4', 3);
      let leave: boolean | undefined;
      (component.canDeactivate() as Observable<boolean>).subscribe((value) => (leave = value));
      expect(leave).toBeTrue();
      expect(lastDialog(dialog).title).toBe('跳出確認');

      state.setForPreviewMode(survey());
      expect(component.canDeactivate()).toBeTrue();
    });
  });
});
