/**
 * ============================================================================
 * 檔案名稱: user-emitter.service.spec.ts
 * 測試對象: UserEmitterService (把元件的動作委派給 AuthActionService 與 SurveyActionService)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { SurveyPayload, SurveySubmissionPayload } from '../@interfaces/survey-model';
import { AuthActionService } from './auth-action.service';
import { SurveyActionService } from './survey-action.service';
import { UserEmitterService } from './user-emitter.service';

describe('UserEmitterService', () => {
  let service: UserEmitterService;
  let authAction: jasmine.SpyObj<AuthActionService>;
  let surveyAction: jasmine.SpyObj<SurveyActionService>;

  beforeEach(() => {
    authAction = jasmine.createSpyObj('AuthActionService', ['resolveErrorMessage', 'login', 'logout', 'updateProfile']);
    surveyAction = jasmine.createSpyObj('SurveyActionService', ['saveSurvey', 'deleteSurvey', 'updateSurveySettings', 'submitSurvey']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthActionService, useValue: authAction },
        { provide: SurveyActionService, useValue: surveyAction },
      ],
    });
    service = TestBed.inject(UserEmitterService);
  });

  it('會員相關動作委派給 AuthActionService', () => {
    authAction.resolveErrorMessage.and.returnValue('訊息');
    const profile = { name: '王小明', nationalId: 'A123456789', phone: '0912345678' };

    expect(service.resolveErrorMessage({ status: 500 }, '預設')).toBe('訊息');
    service.emitLogin({ username: 'u', password: 'p' });
    service.emitLogout();
    service.emitUpdateProfile(profile);

    expect(authAction.login).toHaveBeenCalledWith({ username: 'u', password: 'p' });
    expect(authAction.logout).toHaveBeenCalled();
    expect(authAction.updateProfile).toHaveBeenCalledWith(profile);
  });

  it('問卷相關動作委派給 SurveyActionService (參數原封不動)', () => {
    const survey = { title: 'S' } as SurveyPayload;
    const submission = { surveyId: '1' } as unknown as SurveySubmissionPayload;
    const onDeleted = (): void => undefined;
    const onSaved = (): void => undefined;
    const settings = { isPublicToLobby: true, isOpen: true, version: 2 };

    service.emitDesignSurvey(survey);
    service.emitDeleteSurvey('1', 'S', onDeleted);
    service.emitUpdateSurveySettings('1', settings, onSaved);
    service.emitSubmitSurvey(submission);

    expect(surveyAction.saveSurvey).toHaveBeenCalledWith(survey);
    expect(surveyAction.deleteSurvey).toHaveBeenCalledWith('1', 'S', onDeleted);
    expect(surveyAction.updateSurveySettings).toHaveBeenCalledWith('1', settings, onSaved);
    expect(surveyAction.submitSurvey).toHaveBeenCalledWith(submission);
  });
});
