/**
 * ============================================================================
 * 檔案名稱: survey-action.service.spec.ts
 * 測試對象: SurveyActionService (建立問卷請求組裝、刪除、開放設定與樂觀鎖版本號、填答送出轉換)
 * 測試方式: ApiClientService、DialogService、Router 以 Spy 取代；檢查送往後端的請求內容
 * ============================================================================
 */

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SurveyPayload, SurveySubmissionPayload } from '../@interfaces/survey-model';
import { ApiClientService, ApiResponse } from './api-client.service';
import { DialogService } from './dialog.service';
import { SurveyActionService } from './survey-action.service';

/**
 * 包成後端成功回應
 * @param data 回應資料
 * @returns ApiResponse 的 Observable
 */
function ok(data: unknown = null) {
  return of({ status: 200, success: true, message: '', data, timestamp: '' } as ApiResponse<never>);
}

/**
 * 建立一份最小的問卷設計稿
 * @param overrides 要覆寫之欄位
 * @returns SurveyPayload
 */
function design(overrides: Record<string, unknown> = {}): SurveyPayload {
  return { title: '滿意度', isPublished: true, hasTimeLimit: false, questions: [], ...overrides } as unknown as SurveyPayload;
}

describe('SurveyActionService', () => {
  let service: SurveyActionService;
  let api: jasmine.SpyObj<ApiClientService>;
  let dialog: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;

  /**
   * 取出最後一次 POST 的請求內容
   * @returns 請求 body
   */
  const postedBody = (): any => api.post.calls.mostRecent().args[1];

  /**
   * 取出最後一次對話框的標題
   * @returns 對話框標題
   */
  const lastDialogTitle = (): string | undefined => dialog.showDialog.calls.mostRecent().args[0].title;

  beforeEach(() => {
    api = jasmine.createSpyObj('ApiClientService', ['post', 'put', 'delete', 'resolveErrorMessage']);
    api.resolveErrorMessage.and.callFake((_err: unknown, fallback: string) => fallback);
    dialog = jasmine.createSpyObj('DialogService', ['showDialog']);
    dialog.showDialog.and.returnValue(of(true));
    router = jasmine.createSpyObj('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiClientService, useValue: api },
        { provide: DialogService, useValue: dialog },
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(SurveyActionService);
    spyOn(window, 'scrollTo');
  });

  describe('saveSurvey', () => {
    it('取消確認時不送出', () => {
      dialog.showDialog.and.returnValue(of(false));

      service.saveSurvey(design());

      expect(api.post).not.toHaveBeenCalled();
    });

    it('發布：POST /api/user/surveys，公開到大廳，成功後導回問卷管理', fakeAsync(() => {
      api.post.and.returnValue(ok());

      service.saveSurvey(design());
      tick(50);

      expect(api.post.calls.mostRecent().args[0]).toBe('/api/user/surveys');
      expect(postedBody()).toEqual(jasmine.objectContaining({
        title: '滿意度', description: '', category: '綜合調查', coverImageUrl: '', isPublicToLobby: true, isOpen: true,
        startDate: null, endDate: null, questions: [],
      }));
      expect(postedBody().basicInfoConfig.collectBasicInfo).toBeFalse();
      expect(lastDialogTitle()).toBe('發布成功');
      expect(router.navigateByUrl).toHaveBeenCalledWith('/survey-management');
    }));

    it('草稿：不公開到大廳，成功訊息為儲存成功', () => {
      api.post.and.returnValue(ok());

      service.saveSurvey(design({ isPublished: false }));

      expect(dialog.showDialog.calls.first().args[0].title).toBe('確認保存問卷');
      expect(postedBody().isPublicToLobby).toBeFalse();
      expect(lastDialogTitle()).toBe('儲存成功');
    });

    it('時限：開始補 :00、結束補 :59；沒填時間時使用 00:00 與 23:59；已含秒數則不再補', () => {
      api.post.and.returnValue(ok());

      service.saveSurvey(design({ hasTimeLimit: true, timeLimit: { startDate: '2026-01-01', startTime: '08:30', endDate: '2026-01-31', endTime: '' } }));
      expect([postedBody().startDate, postedBody().endDate]).toEqual(['2026-01-01T08:30:00', '2026-01-31T23:59:59']);

      service.saveSurvey(design({ hasTimeLimit: true, timeLimit: { startDate: '2026-01-01', startTime: ' ', endDate: '2026-01-31', endTime: '18:00:30' } }));
      expect([postedBody().startDate, postedBody().endDate]).toEqual(['2026-01-01T00:00:00', '2026-01-31T18:00:30']);
    });

    it('基本資料設定：前端欄位名稱對應後端欄位名稱', () => {
      api.post.and.returnValue(ok());

      service.saveSurvey(design({ basicInfoRequirements: {
        collectBasicInfo: true, requireMobilePhone: true, requireJobIndustry: true, requireResidentialAddress: true,
      } }));

      expect(postedBody().basicInfoConfig).toEqual(jasmine.objectContaining({
        collectBasicInfo: true, requirePhone: true, requireIndustry: true, requireResidence: true, requireEmail: false,
      }));
    });

    it('題型與量表範圍轉換；是非題自動產生是 / 否選項', () => {
      api.post.and.returnValue(ok());
      const questions = [
        { type: 't/f' }, { type: 'radio', options: ['A', 'B'] }, { type: 'multiRadio' }, { type: 'time' }, { type: 'date' },
        { type: undefined }, { type: 'scale', scaleType: 'nps' }, { type: 'scale', scaleType: 'scale-num-1-10' },
        { type: 'scale', scaleType: 'scale-text-satisfaction-4' }, { type: 'rating', scaleType: 'custom', minRating: 1, maxRating: 7 },
        { type: 'scale', scaleType: 'custom' },
      ];

      service.saveSurvey(design({ questions }));

      const sent = postedBody().questions;
      expect(sent.map((q: any) => q.type)).toEqual([
        'BOOLEAN', 'SINGLE', 'MULTI', 'TIME', 'DATE', 'TEXT', 'RATING', 'RATING', 'RATING', 'RATING', 'RATING',
      ]);
      expect(sent.slice(6).map((q: any) => [q.minRating, q.maxRating])).toEqual([[0, 10], [1, 10], [1, 4], [1, 7], [1, 5]]);
      expect(sent[0].options.map((o: any) => o.optionText)).toEqual(['是', '否']);
      expect(sent[1].options).toEqual([
        { optionText: 'A', sortOrder: 1, triggerQuestionUid: null },
        { optionText: 'B', sortOrder: 2, triggerQuestionUid: null },
      ]);
      expect(sent[0]).toEqual(jasmine.objectContaining({ questionCode: 'Q1', questionText: '題目 1', sortOrder: 1, isRequired: false }));
    });

    it('跳題設定：只有啟用解鎖的選項才送出 triggerQuestionUid', () => {
      api.post.and.returnValue(ok());

      service.saveSurvey(design({ questions: [{
        uid: 'Q9', type: 'radio', description: '是否吸菸', isRequired: true, imageBase64: 'data:x',
        optionsConfig: [
          { text: '是', enableUnlockQuestion: true, unlockTargetUid: 'Q10' },
          { text: '否', enableUnlockQuestion: false, unlockTargetUid: 'Q10' },
          { text: '不確定', enableUnlockQuestion: true, unlockTargetUid: '' },
        ],
      }] }));

      const [question] = postedBody().questions;
      expect(question).toEqual(jasmine.objectContaining({ questionCode: 'Q9', questionText: '是否吸菸', isRequired: true, imageUrl: 'data:x' }));
      expect(question.options.map((o: any) => o.triggerQuestionUid)).toEqual(['Q10', null, null]);
    });

    it('失敗：顯示儲存失敗', () => {
      api.post.and.returnValue(throwError(() => ({ status: 400 })));

      service.saveSurvey(design());

      expect(lastDialogTitle()).toBe('儲存失敗');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('deleteSurvey', () => {
    it('確認後 DELETE (編號只取數字)，成功時呼叫 onSuccess', () => {
      api.delete.and.returnValue(ok());
      const onSuccess = jasmine.createSpy('onSuccess');

      service.deleteSurvey('S-12', '問卷', onSuccess);

      expect(api.delete).toHaveBeenCalledWith('/api/user/surveys/12');
      expect(onSuccess).toHaveBeenCalled();
      expect(lastDialogTitle()).toBe('刪除成功');
    });

    it('編號沒有數字時原樣帶入；沒有 onSuccess 也不出錯', () => {
      api.delete.and.returnValue(ok());

      service.deleteSurvey('abc', '問卷');

      expect(api.delete).toHaveBeenCalledWith('/api/user/surveys/abc');
    });

    it('取消確認不刪除；刪除失敗顯示錯誤且不呼叫 onSuccess', () => {
      const onSuccess = jasmine.createSpy('onSuccess');
      dialog.showDialog.and.returnValue(of(false));
      service.deleteSurvey('1', '問卷', onSuccess);
      expect(api.delete).not.toHaveBeenCalled();

      dialog.showDialog.and.returnValue(of(true));
      api.delete.and.returnValue(throwError(() => ({ status: 403 })));
      service.deleteSurvey('1', '問卷', onSuccess);

      expect(lastDialogTitle()).toBe('刪除失敗');
      expect(onSuccess).not.toHaveBeenCalled();
    });
  });

  describe('updateSurveySettings (樂觀鎖)', () => {
    const settings = { isPublicToLobby: true, isOpen: false, version: 3 };

    it('PUT 設定 (含版本號)，成功時把新版本號交給呼叫端，不另外跳對話框', () => {
      api.put.and.returnValue(ok({ version: 4 }));
      const onSuccess = jasmine.createSpy('onSuccess');

      service.updateSurveySettings('7', settings, onSuccess);

      expect(api.put).toHaveBeenCalledWith('/api/user/surveys/7/settings', settings);
      expect(onSuccess).toHaveBeenCalledWith(4);
      expect(dialog.showDialog).not.toHaveBeenCalled();
    });

    it('編號沒有數字時原樣帶入；回應沒有資料時版本號為 undefined', () => {
      api.put.and.returnValue(of(null as unknown as ApiResponse<unknown>));
      const onSuccess = jasmine.createSpy('onSuccess');

      service.updateSurveySettings('draft', settings, onSuccess);
      service.updateSurveySettings('draft', settings);

      expect(api.put).toHaveBeenCalledWith('/api/user/surveys/draft/settings', settings);
      expect(onSuccess).toHaveBeenCalledWith(undefined);
    });

    it('409 版本衝突：顯示後端訊息「已被其他人修改」', () => {
      api.put.and.returnValue(throwError(() => ({ status: 409 })));
      api.resolveErrorMessage.and.returnValue('此問卷已被其他人修改，請重新整理後再試');

      service.updateSurveySettings('7', settings);

      expect(dialog.showDialog).toHaveBeenCalledWith(jasmine.objectContaining({
        title: '更新失敗', message: '此問卷已被其他人修改，請重新整理後再試',
      }));
    });
  });

  describe('submitSurvey', () => {
    /**
     * 送出一份填答並回傳送往後端的內容
     * @param payload 填答內容 (只需提供要測的欄位)
     * @returns 送出的 SubmissionRequest
     */
    const submit = (payload: Record<string, unknown>): any => {
      api.post.and.returnValue(ok());
      service.submitSurvey(payload as unknown as SurveySubmissionPayload);
      return postedBody();
    };

    it('取消確認時不送出', () => {
      dialog.showDialog.and.returnValue(of(false));

      service.submitSurvey({} as SurveySubmissionPayload);

      expect(api.post).not.toHaveBeenCalled();
    });

    it('成功：POST /api/public/submissions 後導向大廳', fakeAsync(() => {
      const body = submit({ surveyId: 'S-5', durationSeconds: 120, answers: [] });
      tick(50);

      expect(api.post.calls.mostRecent().args[0]).toBe('/api/public/submissions');
      expect(body).toEqual({ surveyId: 5, shareToken: null, durationSeconds: 120, basicInfo: null, answers: [] });
      expect(router.navigateByUrl).toHaveBeenCalledWith('/lobby');
    }));

    it('由分享連結進入：分享代碼原樣帶入請求，供後端放行未公開於大廳之問卷', () => {
      expect(submit({ surveyId: 27, shareToken: 'LPOQP2Z38hvCM9OFiMM84Q' }).shareToken).toBe('LPOQP2Z38hvCM9OFiMM84Q');
    });

    it('問卷編號：數字直接使用，可用 id 欄位，無法解析時為 1；作答秒數缺少時為 60', () => {
      expect(submit({ surveyId: 8 }).surveyId).toBe(8);
      expect(submit({ id: '9' }).surveyId).toBe(9);
      expect(submit({ surveyId: 'x' }).surveyId).toBe(1);
      expect(submit({}).durationSeconds).toBe(60);
    });

    it('基本資料：欄位名稱轉換、空值轉 null、身高體重轉數字', () => {
      const body = submit({ basicInfo: {
        name: '王', mobilePhone: '0912', jobIndustry: '資訊', residentialAddress: '臺北', height: '170', weight: 'abc',
      } });

      expect(body.basicInfo).toEqual(jasmine.objectContaining({
        name: '王', gender: null, phone: '0912', industry: '資訊', residence: '臺北', height: 170, weight: null, ageGroup: null,
      }));
      expect(submit({ basicInfo: { phone: '0933', industry: '金融', residence: '臺中' } }).basicInfo)
        .toEqual(jasmine.objectContaining({ phone: '0933', industry: '金融', residence: '臺中', height: null }));
    });

    it('年齡層：年齡數字換算；沒有年齡時沿用年齡層文字 (區間原樣、單一數字換算、無數字原樣)', () => {
      const ageGroupOf = (basicInfo: Record<string, unknown>): string | null => submit({ basicInfo }).basicInfo.ageGroup;

      expect([10, 20, 30, 40, 50].map((age) => ageGroupOf({ age }))).toEqual(['18歲以下', '18-24歲', '25-34歲', '35-44歲', '45歲以上']);
      expect(ageGroupOf({ age: '', ageGroup: '25-34歲' })).toBe('25-34歲');
      expect(ageGroupOf({ age: 'abc', ageGroup: '30至39' })).toBe('30至39');
      expect(['16', '20', '30', '40', '60'].map((ageGroup) => ageGroupOf({ ageGroup }))).toEqual(['18歲以下', '18-24歲', '25-34歲', '35-44歲', '45歲以上']);
      expect(ageGroupOf({ ageGroup: '未透露' })).toBe('未透露');
    });

    it('答案：量表轉分數 (數字或文字)，選項編號轉數字，其他答案轉文字', () => {
      const body = submit({ answers: [
        { questionId: 1, type: 'scale', answer: '4' },
        { questionId: 2, type: 'scale', answer: '非常滿意' },
        { questionId: 3, type: 'scale', answer: '說不上來' },
        { questionId: 'q4', type: 'radio', answer: ' 是 ', selectedOptionId: 'opt-11' },
        { type: 'multiRadio', answer: ['A'], optionIds: [12, 'o13', 'x'] },
        { questionId: 6, type: 'text', optionId: 'none' },
        { questionId: 'x', type: 'scale' },
      ] });

      const answers = body.answers;
      expect(answers.map((a: any) => a.ratingValue)).toEqual([4, 5, null, null, null, null, null]);
      expect(answers[3]).toEqual(jasmine.objectContaining({ questionId: 4, selectedOptionId: 11, optionId: 11, textAnswer: '是' }));
      expect(answers[4]).toEqual(jasmine.objectContaining({ questionId: 5, selectedOptionIds: [12, 13], textAnswer: '["A"]' }));
      expect(answers[5]).toEqual(jasmine.objectContaining({ selectedOptionId: null, textAnswer: null }));
      expect(answers[6].questionId).toBe(1);
    });

    it('失敗：顯示提交失敗且不跳轉', () => {
      api.post.and.returnValue(throwError(() => ({ status: 400 })));

      service.submitSurvey({ surveyId: 1 } as SurveySubmissionPayload);

      expect(lastDialogTitle()).toBe('提交失敗');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });
});
