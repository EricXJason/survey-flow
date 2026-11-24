/**
 * ============================================================================
 * 檔案名稱: survey-state.service.spec.ts
 * 測試對象: SurveyStateService (預覽模式 / 填答模式切換與重設)
 * ============================================================================
 */

import { SurveyPayload } from '../@interfaces/survey-model';
import { SurveyStateService } from './survey-state.service';

describe('SurveyStateService', () => {
  let service: SurveyStateService;
  const survey = { title: '滿意度調查', questions: [] } as unknown as SurveyPayload;

  beforeEach(() => (service = new SurveyStateService()));

  it('初始為非預覽模式與空白問卷', () => {
    expect(service.isPreviewMode()).toBeFalse();
    expect(service.currentSurvey().title).toBe('');
    expect(service.currentSurvey().basicInfoRequirements.collectBasicInfo).toBeTrue();
  });

  it('setForPreviewMode / setForRunMode 切換模式並保存問卷', () => {
    service.setForPreviewMode(survey);
    expect(service.isPreviewMode()).toBeTrue();
    expect(service.currentSurvey()).toBe(survey);

    service.setForRunMode(survey);
    expect(service.isPreviewMode()).toBeFalse();
  });

  it('resetSurveyState：回到空白問卷，且每次都是新的物件 (不共用參考)', () => {
    const initial = service.currentSurvey();
    service.setForPreviewMode(survey);

    service.resetSurveyState();

    expect(service.isPreviewMode()).toBeFalse();
    expect(service.currentSurvey().title).toBe('');
    expect(service.currentSurvey()).not.toBe(initial);
  });
});
