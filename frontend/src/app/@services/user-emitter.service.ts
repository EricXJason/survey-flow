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
 * 邊界處理: 本身不含業務邏輯，只把呼叫轉交給對應的專職服務，元件只需注入這一個服務
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { AuthActionService } from './auth-action.service';
import { SurveyActionService } from './survey-action.service';
import {
  AccountLogInPayload,
  AccountModifyProfilePayload,
} from '../@interfaces/account-model';
import {
  SurveyPayload,
  SurveySettingsPayload,
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

  /**
   * 解析 API 錯誤回應為使用者可讀之繁中訊息 (委派 AuthActionService)
   * @param err HTTP 錯誤物件
   * @param fallbackMessage 無法解析時顯示之預設訊息
   * @returns 錯誤訊息文字
   */
  public resolveErrorMessage(err: any, fallbackMessage: string): string {
    return this.authAction.resolveErrorMessage(err, fallbackMessage);
  }

  /**
   * 觸發會員登入 (委派 AuthActionService.login)
   * @param payload 帳號與密碼
   */
  public emitLogin(payload: AccountLogInPayload): void {
    this.authAction.login(payload);
  }

  /**
   * 觸發會員登出並清除前端登入狀態 (委派 AuthActionService.logout)
   */
  public emitLogout(): void {
    this.authAction.logout();
  }

  /**
   * 觸發個人資料更新 (委派 AuthActionService.updateProfile)
   * @param payload 個人資料表單內容
   */
  public emitUpdateProfile(payload: AccountModifyProfilePayload): void {
    this.authAction.updateProfile(payload);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 2. 問卷生命週期與填答行為 (委派 SurveyActionService)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * 觸發問卷設計儲存 (建立新問卷)，委派 SurveyActionService.saveSurvey
   * @param payload 問卷設計內容
   */
  public emitDesignSurvey(payload: SurveyPayload): void {
    this.surveyAction.saveSurvey(payload);
  }

  /**
   * 觸發問卷刪除 (含二次確認對話框)，委派 SurveyActionService.deleteSurvey
   * @param surveyId 問卷識別碼
   * @param surveyTitle 問卷標題 (顯示於確認對話框)
   * @param onSuccess 刪除成功後之回呼
   */
  public emitDeleteSurvey(surveyId: string, surveyTitle: string, onSuccess?: () => void): void {
    this.surveyAction.deleteSurvey(surveyId, surveyTitle, onSuccess);
  }

  /**
   * 觸發問卷開放設定整體覆蓋 (PUT /api/user/surveys/{id}/settings)，委派 SurveyActionService.updateSurveySettings
   * @param surveyId 問卷識別碼
   * @param settings 起訖時間、大廳公開與開放填答設定
   * @param onSuccessCallback 更新成功後之回呼
   */
  public emitUpdateSurveySettings(
    surveyId: string,
    settings: SurveySettingsPayload,
    onSuccessCallback?: (newVersion: number | undefined) => void,
  ): void {
    this.surveyAction.updateSurveySettings(surveyId, settings, onSuccessCallback);
  }

  /**
   * 觸發受訪者填答送出，委派 SurveyActionService.submitSurvey
   * @param payload 填答內容
   */
  public emitSubmitSurvey(payload: SurveySubmissionPayload): void {
    this.surveyAction.submitSurvey(payload);
  }
}
