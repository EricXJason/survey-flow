/**
 * ============================================================================
 * 檔案名稱: survey-state.service.ts
 * 所屬模組: 問卷運作與預覽狀態管理模組 (Survey State Management Module)
 * 責任描述: 維護當前選定之問卷模型，以及在問卷設計預覽 (Preview Mode) 與正式作答填答 (Run Mode) 間切換之全域狀態。
 * 架構分層: Service Layer / State Management
 * 聯動關係:
 *   - 上游依賴: ../@interfaces/survey-model, ../datas/surveys.json
 *   - 下游消費: SurveyDesignComponent, SurveyRunnerComponent, SurveyManagementComponent, LobbyComponent
 * 邊界處理: 預設載入假資料第 0 筆防範空模型崩潰；提供重設方法還原預設模式
 * ============================================================================
 */

import { Injectable, signal } from '@angular/core';
import { SurveyPayload } from '../@interfaces/survey-model';

/**
 * 建立空白預設問卷酬載物件
 */
function createDefaultSurveyPayload(): SurveyPayload {
  return {
    title: '',
    category: '綜合調查',
    isPublished: false,
    hasTimeLimit: false,
    hasDescription: false,
    description: '',
    hasDescriptionImage: false,
    basicInfoRequirements: {
      collectBasicInfo: true,
      requireName: true,
      requireGender: true,
      requireAge: true,
      requireBirthday: false,
      requireHeight: false,
      requireWeight: false,
      requireMobilePhone: false,
      requireHomePhone: false,
      requireEmail: false,
      requireRegisteredAddress: false,
      requireResidentialAddress: false,
      requireDepartment: false,
      requireAcademicMajor: false,
      requireClassName: false,
      requireStudentId: false,
      requireEmployeeId: false,
      requireNationalId: false,
      requireJobIndustry: false,
      requireJobTitle: false,
    },
    questions: [],
  };
}

/**
 * 問卷狀態管理服務 (SurveyStateService)
 * 管理當前選取問卷與預覽/填答模式切換。
 */
@Injectable({
  providedIn: 'root',
})
export class SurveyStateService {
  /** 是否處於設計預覽模式 (Signal) */
  public readonly isPreviewMode = signal<boolean>(false);

  /** 當前問卷模型訊號 (Signal) */
  public readonly currentSurvey = signal<SurveyPayload>(createDefaultSurveyPayload());

  /**
   * 切換為設計預覽模式
   * @param survey 欲預覽之問卷資料載荷
   */
  public setForPreviewMode(survey: SurveyPayload): void {
    this.isPreviewMode.set(true);
    this.currentSurvey.set(survey);
  }

  /**
   * 切換為正式填答模式
   * @param survey 欲填答之問卷資料載荷
   */
  public setForRunMode(survey: SurveyPayload): void {
    this.isPreviewMode.set(false);
    this.currentSurvey.set(survey);
  }

  /**
   * 重設問卷狀態為預設值
   * 解除預覽模式並將當前問卷還原為初始範本
   */
  public resetSurveyState(): void {
    this.isPreviewMode.set(false);
    this.currentSurvey.set(createDefaultSurveyPayload());
  }
}

