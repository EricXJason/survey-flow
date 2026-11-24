/**
 * ============================================================================
 * 檔案名稱: survey-getter.service.spec.ts
 * 測試對象: SurveyGetterService (大廳 / 個人 / 全站清單轉換、單一問卷題型與量表對應、跳題鎖定摘要、錯誤處理)
 * 測試方式: ApiClientService 以 Spy 取代，直接餵入後端回應格式
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SurveyPayload } from '../@interfaces/survey-model';
import { ApiClientService, ApiResponse } from './api-client.service';
import { SurveyGetterService } from './survey-getter.service';

/**
 * 包成後端成功回應
 * @param data 回應資料
 * @returns ApiResponse 的 Observable
 */
function ok(data: unknown) {
  return of({ status: 200, success: true, message: '', data, timestamp: '' } as ApiResponse<unknown>);
}

describe('SurveyGetterService', () => {
  let service: SurveyGetterService;
  let api: jasmine.SpyObj<ApiClientService>;

  beforeEach(() => {
    api = jasmine.createSpyObj('ApiClientService', ['get', 'resolveErrorMessage']);
    TestBed.configureTestingModule({ providers: [{ provide: ApiClientService, useValue: api }] });
    service = TestBed.inject(SurveyGetterService);
  });

  /**
   * 訂閱並同步取出 Observable 的值 (Spy 回傳的都是同步 of)
   * @param source 要讀取的 Observable
   * @returns 第一個發出的值
   */
  function valueOf<T>(source: import('rxjs').Observable<T>): T {
    let value!: T;
    source.subscribe((v) => (value = v));
    return value;
  }

  describe('清單', () => {
    const fullItem = {
      id: 5, userSurveyNumber: 2, title: 'A', category: '教育', userId: 9, authorName: '王',
      startDate: '2026-01-01', endDate: '2026-02-01', isPublicToLobby: false, isOpen: false, version: 3,
      shareToken: 'kPL6FRh2idiOuIO-tF3naA',
    };

    it('大廳：呼叫公開端點並完整轉換欄位 (含樂觀鎖版本號與分享代碼)', () => {
      api.get.and.returnValue(ok({ content: [fullItem] }));

      const [item] = valueOf(service.getLobbySurveys());

      expect(api.get).toHaveBeenCalledWith('/api/public/surveys/lobby?page=0&size=100');
      expect(item).toEqual({
        id: '5', userSurveyNumber: 2, title: 'A', category: '教育', authorId: '9', authorName: '王',
        startDate: '2026-01-01', endDate: '2026-02-01', isPublished: false, isPublicToLobby: false, isOpen: false, version: 3,
        shareToken: 'kPL6FRh2idiOuIO-tF3naA',
      });
    });

    it('個人清單：缺少序號與建立者時以列表順序與登入者 ID 補上；其餘缺少欄位使用預設值', () => {
      api.get.and.returnValue(ok({ content: [{ id: 1, title: 'B' }, { id: 2, title: 'C', userSurveyNumber: null }] }));

      const items = valueOf(service.getMemberSurveys('42'));

      expect(api.get).toHaveBeenCalledWith('/api/user/surveys?page=0&size=100');
      expect(items.map((i) => i.userSurveyNumber)).toEqual([1, 2]);
      expect(items[0]).toEqual(jasmine.objectContaining({
        authorId: '42', category: '一般問卷', isPublicToLobby: true, isOpen: true, startDate: '', version: undefined,
      }));
    });

    it('全站清單 (管理員)：沒有 userId 時以作者名稱當作建立者', () => {
      api.get.and.returnValue(ok({ content: [{ id: 1, title: 'D', authorName: '管理員' }] }));

      const [item] = valueOf(service.getAllSurveys());

      expect(api.get).toHaveBeenCalledWith('/api/admin/surveys?page=0&size=100');
      expect(item.authorId).toBe('管理員');
    });

    it('回應不成功、沒有 content 或請求失敗時都回傳空陣列 (畫面顯示空清單而非錯誤)', () => {
      api.get.and.returnValue(of({ success: false } as ApiResponse<unknown>));
      expect(valueOf(service.getLobbySurveys())).toEqual([]);
      expect(valueOf(service.getAllSurveys())).toEqual([]);

      api.get.and.returnValue(ok({}));
      expect(valueOf(service.getMemberSurveys('1'))).toEqual([]);

      api.get.and.returnValue(throwError(() => ({ status: 500 })));
      expect(valueOf(service.getLobbySurveys())).toEqual([]);
      expect(valueOf(service.getMemberSurveys('1'))).toEqual([]);
      expect(valueOf(service.getAllSurveys())).toEqual([]);
    });
  });

  describe('getSurveyByShareToken', () => {
    it('請求分享代碼端點 (代碼經 URL 編碼)，並與依 ID 查詢共用相同轉換', () => {
      api.get.and.returnValue(ok({ id: 27, title: '讀書會', questions: [] }));

      const survey = valueOf(service.getSurveyByShareToken('a/b')) as SurveyPayload;

      expect(api.get).toHaveBeenCalledWith('/api/public/surveys/shared/a%2Fb');
      expect(survey.id).toBe('27');
      expect(survey.title).toBe('讀書會');
    });
  });

  describe('getSurveyById', () => {
    it('編號無法解析時直接回傳 null，不發出請求', () => {
      expect(valueOf(service.getSurveyById('abc'))).toBeNull();
      expect(api.get).not.toHaveBeenCalled();
    });

    it('預覽模式帶 ?preview=true；編號中的非數字字元會先去除', () => {
      api.get.and.returnValue(of({ success: false } as ApiResponse<unknown>));

      expect(valueOf(service.getSurveyById('S-012', true))).toBeNull();
      expect(api.get).toHaveBeenCalledWith('/api/public/surveys/12?preview=true');
    });

    it('後端題型對應前端題型，未知題型視為文字題', () => {
      const types = ['BOOLEAN', 'SINGLE', 'MULTI', 'RATING', 'TIME', 'DATE', 'TEXT', undefined];
      api.get.and.returnValue(ok({ id: 1, title: 'T', questions: types.map((type) => ({ type, questionText: 'q' })) }));

      const survey = valueOf(service.getSurveyById('1')) as SurveyPayload;

      expect(survey.questions.map((q) => q.type)).toEqual(['t/f', 'radio', 'multiRadio', 'scale', 'time', 'date', 'text', 'text']);
      expect(survey.questions[0].uid).toBe('Q1');
      expect(survey.questions[0].isRequired).toBeTrue();
    });

    it('評分題依 min-max 決定量表類型，其他範圍沿用後端 scaleType，否則預設 5 級滿意度', () => {
      const ranges = [[0, 10], [1, 10], [1, 5], [1, 7], [1, 3]];
      api.get.and.returnValue(ok({
        id: 1,
        questions: ranges.map(([minRating, maxRating], i) => ({
          type: 'RATING', minRating, maxRating, scaleType: i === 3 ? 'scale-text-agree-7' : undefined,
        })),
      }));

      const survey = valueOf(service.getSurveyById('1')) as SurveyPayload;

      expect(survey.questions.map((q) => q.scaleType)).toEqual([
        'scale-num-0-10', 'scale-num-1-10', 'scale-num-1-5', 'scale-text-agree-7', 'scale-text-satisfaction-5',
      ]);
    });

    it('跳題：被選項解鎖的題目標記為鎖定並產生來源摘要 (可用 questionCode 或 id 對應)', () => {
      api.get.and.returnValue(ok({
        id: 1,
        questions: [
          { id: 10, questionCode: 'Q1', type: 'SINGLE', options: [
            { id: 1, optionText: '是', triggerQuestionUid: 'Q2' },
            { id: 2, optionText: '', triggerQuestionUid: '30' },
          ] },
          { id: 20, questionCode: 'Q2', type: 'SINGLE', options: [{ id: 3, optionText: '否', triggerQuestionUid: 'Q3' }] },
          { id: 30, questionCode: 'Q3', type: 'TEXT' },
        ],
      }));

      const survey = valueOf(service.getSurveyById('1')) as SurveyPayload;
      const [q1, q2, q3] = survey.questions;

      expect(q1.isLockedByOther).toBeFalse();
      expect(q1.optionsConfig?.[0]).toEqual(jasmine.objectContaining({ text: '是', enableUnlockQuestion: true, unlockTargetUid: 'Q2' }));
      expect(q2.isLockedByOther).toBeTrue();
      expect(q2.lockedBySummary).toBe('【問題 01】選項 01（是）');
      expect(q3.lockedBySummary).toBe('【問題 01】選項 02 或 【問題 02】選項 01（否）');
    });

    it('問卷層級欄位：起訖日期拆成日期與時間、基本資料設定對應前端欄位名稱', () => {
      api.get.and.returnValue(ok({
        id: 7, title: 'T', description: '說明', coverImageUrl: 'data:img', isOpen: false, isPublicToLobby: true,
        startDate: '2026-01-01T08:30:00', endDate: '2026-01-31',
        basicInfoConfig: { requireName: true, requirePhone: true, requireResidence: true, requireIndustry: true },
        questions: [{ type: 'TEXT', imageUrl: 'data:q', isRequired: false }],
      }));

      const survey = valueOf(service.getSurveyById('7')) as SurveyPayload;

      expect(survey.id).toBe('7');
      expect(survey.isOpen).toBeFalse();
      expect(survey.isPublished).toBeFalse();
      expect(survey.hasTimeLimit).toBeTrue();
      expect(survey.timeLimit).toEqual({ startDate: '2026-01-01', startTime: '08:30', endDate: '2026-01-31', endTime: '23:59' });
      expect(survey.hasDescriptionImage).toBeTrue();
      expect(survey.basicInfoRequirements).toEqual(jasmine.objectContaining({
        collectBasicInfo: true, requireName: true, requireMobilePhone: true, requireResidentialAddress: true,
        requireJobIndustry: true, requireEmail: false,
      }));
      expect(survey.questions[0].hasImage).toBeTrue();
      expect(survey.questions[0].isRequired).toBeFalse();
    });

    it('沒有日期與基本資料設定時：無時限、預設時間、不收集基本資料；isOpen 缺少時視為開放', () => {
      api.get.and.returnValue(ok({ id: 7, title: 'T' }));

      const survey = valueOf(service.getSurveyById('7')) as SurveyPayload;

      expect(survey.hasTimeLimit).toBeFalse();
      expect(survey.timeLimit).toEqual({ startDate: '', startTime: '00:00', endDate: '', endTime: '23:59' });
      expect(survey.basicInfoRequirements.collectBasicInfo).toBeFalse();
      expect(survey.isOpen).toBeTrue();
      expect(survey.isPublicToLobby).toBeFalse();
      expect(survey.questions).toEqual([]);
    });

    it('請求失敗：以 resolveErrorMessage 轉成只帶繁中訊息的 Error (不外洩 Angular 原始英文訊息)', () => {
      const httpError = { status: 0, message: 'Http failure response for x: 0 Unknown Error' };
      api.get.and.returnValue(throwError(() => httpError));
      api.resolveErrorMessage.and.returnValue('無法連線至伺服器');
      let message = '';

      service.getSurveyById('1').subscribe({ error: (e: Error) => (message = e.message) });

      expect(api.resolveErrorMessage).toHaveBeenCalledWith(httpError, '問卷不存在或目前未開放填答');
      expect(message).toBe('無法連線至伺服器');
    });
  });
});
