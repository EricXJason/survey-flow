/**
 * ============================================================================
 * 檔案名稱: user-emitter.service.ts
 * 所屬模組: 使用者操作統一門面服務 (User Action Facade Service)
 * 責任描述: 作為前端使用者與問卷生命週期操作的統一調度 Facade，落實 SRP 原則，
 *           將具體業務拆分委派給專職的 AuthActionService 與 SurveyActionService。
 * 架構分層: Service Layer (Facade Pattern / SRP 單一職責原則落實)
 * 聯動關係:
 *   - 上游依賴: AuthActionService, SurveyActionService
 *   - 下游消費: HeaderComponent, LogInComponent, SurveyDesignComponent,
 *               SurveyManagementComponent, SurveyRunnerComponent, UserProfileComponent
 * 邊界處理: 統一向後相容公開 API，委派具體操作至專職領域服務，消弭上帝類
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { AuthActionService } from './auth-action.service';
import { SurveyActionService } from './survey-action.service';
import {
  AccountLogInPayload,
  AccountRegisterPayload,
  AccountModifyProfilePayload,
  AccountForgotPasswordPayload,
} from '../@interfaces/account-model';
import {
  SurveyPayload,
  SurveySubmissionPayload,
} from '../@interfaces/survey-model';

@Injectable({
  providedIn: 'root',
})
export class UserEmitterService {
  private readonly authAction = inject(AuthActionService);
  private readonly surveyAction = inject(SurveyActionService);

  // ─────────────────────────────────────────────────────────────────────────
  // 1. 會員帳號與認證行為 (委派 AuthActionService)
  // ─────────────────────────────────────────────────────────────────────────

  public resolveErrorMessage(err: any, fallbackMessage: string): string {
    return this.authAction.resolveErrorMessage(err, fallbackMessage);
  }

  public emitLogin(payload: AccountLogInPayload): void {
    this.authAction.login(payload);
  }

  public emitSignUp(payload: AccountRegisterPayload): void {
    this.authAction.signUp(payload);
  }

  public emitLogout(): void {
    this.authAction.logout();
  }

  public emitForgotPassword(payload: AccountForgotPasswordPayload): void {
    this.authAction.forgotPassword(payload);
  }

  public emitUpdateProfile(payload: AccountModifyProfilePayload): void {
    this.authAction.updateProfile(payload);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 2. 問卷生命週期與填答行為 (委派 SurveyActionService)
  // ─────────────────────────────────────────────────────────────────────────

  public emitSaveSurvey(payload: SurveyPayload): void {
    this.surveyAction.saveSurvey(payload);
  }

  public emitDesignSurvey(payload: SurveyPayload): void {
    this.surveyAction.saveSurvey(payload);
  }

  public emitDeleteSurvey(surveyId: string, surveyTitle: string, onSuccess?: () => void): void {
    this.surveyAction.deleteSurvey(surveyId, surveyTitle, onSuccess);
  }

  public emitTogglePublish(surveyId: string, currentPublishedState: boolean, onSuccess?: () => void): void {
    this.surveyAction.togglePublish(surveyId, currentPublishedState, onSuccess);
  }

  public emitUpdateSurveySettings(
    surveyId: string,
    settings: {
      startDate?: string | null;
      endDate?: string | null;
      isPublicToLobby: boolean;
      isOpen: boolean;
    },
    onSuccessCallback?: () => void,
  ): void {
    this.surveyAction.updateSurveySettings(surveyId, settings, onSuccessCallback);
  }

  public emitSubmitSurvey(payload: SurveySubmissionPayload): void {
    this.surveyAction.submitSurvey(payload);
  }
}
