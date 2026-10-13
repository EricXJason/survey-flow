/**
 * ============================================================================
 * 檔案名稱: survey-design.component.ts
 * 所屬模組: 問卷設計與題型編輯模組 (Survey Studio / Design Module)
 * 責任描述: 負責視覺化問卷設計編輯器，提供基本資料配置、受訪者身分欄位開關、動態題目增刪改（單選/多選/文字/量表/時間/日期）、條件跳題解鎖、拖曳排序與即時填答預覽。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/dialog.service, ../../../@services/user-emitter.service, ../../../@services/survey-state.service, ../../../@interfaces/survey-model
 *   - 下游消費: app.routes.ts 路由導航調用, SurveyReorderDialogComponent, SurveyRunnerComponent
 * 邊界處理: 路由離開守衛 canDeactivate 攔截跳出防呆；題目 UID 保持穩定綁定，即使排序或刪除亦能維持條件跳題依賴關係完整性
 * ============================================================================
 */

import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Observable } from 'rxjs';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import {
  SurveyReorderDialogComponent,
  ReorderDialogItem,
} from './survey-reorder-dialog/survey-reorder-dialog.component';
import {
  SurveyPayload,
  SurveyQuestion,
  SURVEY_CATEGORIES,
} from '../../../@interfaces/survey-model';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { SurveyRunnerComponent } from '../survey-runner/survey-runner.component';

/**
 * 問卷設計編輯器組件 (SurveyDesignComponent)
 * 負責提供可視化問卷編輯介面，包含問卷基本資料配置（標題、分類、期限、前言與受訪者背景欄位）、
 * 動態題目增刪改（單選、複選、文字、多維度量表、日期、時間）、
 * 單選題條件動態跳題解鎖、選項與題目拖曳排序，以及即時受訪者視角預覽。
 */
@Component({
  selector: 'app-survey-design',
  templateUrl: './survey-design.component.html',
  styleUrl: './survey-design.component.scss',
  imports: [
    ReactiveFormsModule,
    DragDropModule,
    SurveyReorderDialogComponent,
    SurveyRunnerComponent,
  ],
})
export class SurveyDesignComponent implements OnInit, OnDestroy {
  /** 響應式表單建構器 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 系統全域對話框服務 */
  private readonly _dialogService = inject(DialogService);
  /** 使用者跨組件事件發送器（發布問卷） */
  private readonly _userEmitterService = inject(UserEmitterService);
  /** 問卷預覽與運行狀態管理服務 */
  public readonly surveyStateService = inject(SurveyStateService);

  /** 今日日期（YYYY-MM-DD 格式，用於日期限制預設最小起始值） */
  protected readonly todayDate = new Date().toISOString().split('T')[0];
  /** 官方標準定義之 12 大問卷主題分類清單 */
  public readonly surveyCategories = SURVEY_CATEGORIES;
  /** 控制題目整體重排對話框 (Reorder Modal) 開啟狀態 */
  public isReorderModalOpen: boolean = false;
  /** 問卷是否已成功送出發布之狀態旗標 */
  private _isSubmitted: boolean = false;

  /**
   * Angular 生命週期鉤子 (ngOnInit)
   * 初始化組件時，預設自動為問卷新增第一個空白問題。
   */
  public ngOnInit(): void {
    this.addQuestion();
  }

  /**
   * 路由離開守衛 (canDeactivate)
   * 當設計者試圖離開問卷設計頁面時，若尚未正式送出發布問卷，
   * 攔截跳轉並彈出操作確認對話框詢問「是否確定跳出？尚未儲存的問卷設計內容將會遺失。」。
   * 只有在使用者點選「是」確認離開時才放行路由跳出；
   * 若點選「返回」或關閉對話框，則由 Angular Router 取消跳轉，完整維持當前問卷編輯內容。
   */
  public canDeactivate(): Observable<boolean> | boolean {
    if (!this._isSubmitted) {
      const confirmData: DialogData = {
        title: '跳出確認',
        message: '是否確定跳出？尚未儲存的問卷設計內容將會遺失。',
        mode: 'checkMode',
        confirmText: '是',
        cancelText: '返回',
        showCloseButton: true,
      };

      return this._dialogService.showDialog(confirmData);
    }
    return true;
  }

  /**
   * Angular 生命週期鉤子 (ngOnDestroy)
   * 當確認離開問卷設計頁面時，重設全域問卷編輯與預覽狀態。
   */
  public ngOnDestroy(): void {
    this.surveyStateService.resetSurveyState();
  }

  /** 全問卷主要響應式表單群組 */
  protected readonly formBuilderGroup = this.formBuilderGroupInit();

  /**
   * 初始化問卷主要表單群組結構
   * 配置標題、類別、公開性、時限設定、前言說明、受訪者個人資料開關及問題陣列。
   * @returns FormGroup 響應式表單物件
   */
  private formBuilderGroupInit(): FormGroup {
    return this._formBuilder.group({
      // === 階段 01：問卷核心資訊 ===
      title: ['', [Validators.required, Validators.minLength(1)]],
      category: ['', Validators.required],
      hasDescription: [false],
      description: ['', Validators.required],
      hasDescriptionImage: [false],
      descriptionImageBase64: [''],

      // === 階段 02：受訪者身分與背景欄位 ===
      collectBasicInfo: [false],
      // (1) 個人身分識別
      requireName: [false],
      requireGender: [false],
      requireAge: [false],
      requireBirthday: [false],
      requireNationalId: [false],
      // (2) 聯絡通訊資料
      requireMobilePhone: [false],
      requireHomePhone: [false],
      requireEmail: [false],
      requireResidentialAddress: [false],
      requireRegisteredAddress: [false],
      // (3) 職場組織資訊
      requireJobIndustry: [false],
      requireJobTitle: [false],
      requireDepartment: [false],
      requireEmployeeId: [false],
      // (4) 學校與教育背景
      requireAcademicMajor: [false],
      requireClassName: [false],
      requireStudentId: [false],
      // (5) 生理與健康體質
      requireHeight: [false],
      requireWeight: [false],

      // === 階段 03：問卷題目與邏輯 ===
      questions: this._formBuilder.array([]),

      // === 階段 04：發布規範與時程控管 ===
      isPublished: [true],
      hasTimeLimit: [false],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
    });
  }

  /**
   * 取得問卷題目表單陣列 (FormArray)
   */
  public get questionArray(): FormArray {
    return this.formBuilderGroup.get('questions') as FormArray;
  }

  /**
   * 取得特定問題的選項表單陣列 (FormArray)
   * @param questionIndex 目標題目在 questionArray 中的索引值
   * @returns 該題目的選項 FormArray
   */
  protected getOptionsArray(questionIndex: number): FormArray {
    return this.questionArray.at(questionIndex).get('options') as FormArray;
  }

  /**
   * 建立單一選項表單群組 (FormGroup)
   * @param initialText 選項初始文字內容
   * @returns 包含文字、是否開啟關聯題目及目標題目 UID 的 FormGroup
   */
  private createOptionGroup(initialText: string = ''): FormGroup {
    return this._formBuilder.group({
      text: [initialText, [Validators.required, Validators.minLength(1)]],
      enableUnlockQuestion: [false],
      unlockTargetUid: [''],
    });
  }

  /**
   * 產生隨機唯一識別碼 (UID)
   * 用於題目關聯跳題綁定，即使題目在陣列中順序改變，UID 仍然維持穩定綁定。
   * @returns 隨機 UUID 字串
   */
  private generateUid(): string {
    return crypto.randomUUID();
  }

  /**
   * 新增一筆空白問題至問卷題目陣列中
   * 預設包含唯一 UID、收合旗標、題型、必填、說明文字及預設 3 個空白選項。
   */
  protected addQuestion(): void {
    const questionGroup = this._formBuilder.group({
      uid: [this.generateUid()],
      isCollapsed: [false],
      type: ['', Validators.required],
      isRequired: [false, Validators.required],
      hasImage: [false, Validators.required],
      imageBase64: [''],
      description: ['', [Validators.required, Validators.minLength(1)]],

      scaleType: ['scale-text-satisfaction-5'],

      timeValue: [''],
      hasTimeLimit: [false],
      minTime: [''],
      maxTime: [''],

      dateValue: [''],
      hasDateLimit: [false],
      minDate: [''],
      maxDate: [''],

      options: this._formBuilder.array([
        this.createOptionGroup(''),
        this.createOptionGroup(''),
        this.createOptionGroup(''),
      ]),
    });
    this.questionArray.push(questionGroup);
  }

  /**
   * 為指定題目新增一個選項
   * @param questionIndex 目標題目索引
   */
  protected addOption(questionIndex: number): void {
    this.getOptionsArray(questionIndex).push(this.createOptionGroup(''));
  }

  /**
   * 移除指定題目的特定選項（最少保留一個選項）
   * @param questionIndex 目標題目索引
   * @param optionIndex 目標選項索引
   */
  protected removeOption(questionIndex: number, optionIndex: number): void {
    const options = this.getOptionsArray(questionIndex);
    if (options.length > 1) {
      options.removeAt(optionIndex);
    }
  }

  /**
   * 取得選項序號格式化字串（如 01, 02）
   * @param index 選項索引
   * @returns 補零格式化的序號字串
   */
  protected getOptionLabel(index: number): string {
    return (index + 1).toString().padStart(2, '0');
  }

  /**
   * 處理問卷前言說明圖片上傳變更
   * 讀取本機圖檔並轉換為 Base64 字串寫入表單。
   * @param event 檔案選取事件
   */
  protected onDescriptionImageChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = () => {
        this.formBuilderGroup.patchValue({
          descriptionImageBase64: reader.result as string,
        });
      };
      reader.readAsDataURL(file);
    }
  }

  /**
   * 移除問卷前言說明所上傳的圖片
   * @param fileInput 原生 file input 元素，用於清空檔案選取值
   */
  protected removeDescriptionImage(fileInput: HTMLInputElement): void {
    fileInput.value = '';
    this.formBuilderGroup.patchValue({
      descriptionImageBase64: '',
    });
  }

  /**
   * 處理題目說明圖片上傳變更
   * @param event 檔案選取事件
   * @param questionIndex 目標題目索引
   */
  protected onQuestionImageChange(event: Event, questionIndex: number): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = () => {
        this.questionArray.at(questionIndex).patchValue({
          imageBase64: reader.result as string,
        });
      };
      reader.readAsDataURL(file);
    }
  }

  /**
   * 移除題目說明所上傳的圖片
   * @param questionIndex 目標題目索引
   * @param fileInput 原生 file input 元素
   */
  protected removeQuestionImage(
    questionIndex: number,
    fileInput: HTMLInputElement,
  ): void {
    fileInput.value = '';
    this.questionArray.at(questionIndex).patchValue({
      imageBase64: '',
    });
  }

  /**
   * 刪除指定問題
   * 同步檢查並解除其他題目針對該題所設置之條件跳題鎖定 (unlockTargetUid)。
   * @param questionIndex 目標刪除的題目索引
   */
  protected removeQuestion(questionIndex: number): void {
    const removedUid = this.questionArray.at(questionIndex).get('uid')?.value;
    this.questionArray.removeAt(questionIndex);

    for (let questionIdx = 0; questionIdx < this.questionArray.length; questionIdx++) {
      const optionsArray = this.getOptionsArray(questionIdx);
      for (let optionIdx = 0; optionIdx < optionsArray.length; optionIdx++) {
        const optionGroup = optionsArray.at(optionIdx) as FormGroup;
        if (optionGroup.get('unlockTargetUid')?.value === removedUid) {
          optionGroup.patchValue({ enableUnlockQuestion: false, unlockTargetUid: '' });
        }
      }
    }
  }

  /**
   * 計算輸入字串之長度
   * @param text 目標文字字串
   * @returns 字數長度字串
   */
  protected detectTextLength(text: string): string {
    return text ? text.length.toString() : '0';
  }

  /**
   * 切換題目的收合/展開狀態
   * @param questionIndex 目標題目索引
   */
  protected toggleQuestion(questionIndex: number): void {
    const control = this.questionArray.at(questionIndex).get('isCollapsed');
    if (control) {
      control.patchValue(!control.value);
    }
  }

  /**
   * 處理 CDK Drag & Drop 選項拖曳排序
   * @param event CDK 拖曳放置事件
   * @param questionIndex 目標題目索引
   */
  protected dropOption(
    event: CdkDragDrop<FormArray>,
    questionIndex: number,
  ): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const optionsArray = this.getOptionsArray(questionIndex);
    const currentControl = optionsArray.at(event.previousIndex);
    optionsArray.removeAt(event.previousIndex);
    optionsArray.insert(event.currentIndex, currentControl);
  }

  /**
   * 轉換當前所有問題為題目重排對話框所需的資料結構 (ReorderDialogItem[])
   * @returns 題目重排清單陣列
   */
  protected getReorderDialogItems(): ReorderDialogItem[] {
    return this.questionArray.controls.map((control) => {
      const group = control as FormGroup;
      const desc = group.get('description')?.value || '未命名題目';
      return {
        control: group,
        uid: group.get('uid')?.value,
        description: desc.length > 25 ? desc.substring(0, 25) + '...' : desc,
      };
    });
  }

  /**
   * 開啟題目拖曳排序對話框
   */
  protected openReorderModal(): void {
    this.isReorderModalOpen = true;
  }

  /**
   * 取得位於當前題目之後的所有題目清單
   * 用於單選題配置「勾選此選項時開啟後續題目」時的下拉選單選項，防止循環鎖定。
   * @param currentQuestionIndex 當前題目索引
   * @returns 後續題目的 UID 與截短標題清單
   */
  protected getSubsequentQuestions(
    currentQuestionIndex: number,
  ): { uid: string; displayLabel: string }[] {
    const list: { uid: string; displayLabel: string }[] = [];
    for (let i = currentQuestionIndex + 1; i < this.questionArray.length; i++) {
      const q = this.questionArray.at(i);
      const uid = q.get('uid')?.value;
      const desc = q.get('description')?.value || '未命名題目';
      const truncatedDesc =
        desc.length > 18 ? desc.substring(0, 18) + '...' : desc;
      list.push({
        uid,
        displayLabel: `問題 ${i + 1}：${truncatedDesc}`,
      });
    }
    return list;
  }

  /**
   * 檢查並取得鎖定此題目的上游來源題目與選項清單文字
   * @param targetUid 當前題目的 UID
   * @returns 鎖定來源描述文字（若無被鎖定則回傳 null）
   */
  protected getLockSources(targetUid: string): string | null {
    if (!targetUid) return null;
    const sources: string[] = [];

    for (let questionIdx = 0; questionIdx < this.questionArray.length; questionIdx++) {
      const questionType = this.questionArray.at(questionIdx).get('type')?.value;
      if (questionType !== 'radio') continue;

      const optionsArray = this.getOptionsArray(questionIdx);
      for (let optionIdx = 0; optionIdx < optionsArray.length; optionIdx++) {
        const optionGroup = optionsArray.at(optionIdx);
        if (
          optionGroup.get('enableUnlockQuestion')?.value &&
          optionGroup.get('unlockTargetUid')?.value === targetUid
        ) {
          const optionLabel = this.getOptionLabel(optionIdx);
          sources.push(`【問題 ${questionIdx + 1}】選項 ${optionLabel}`);
        }
      }
    }

    return sources.length > 0 ? sources.join('、') : null;
  }

  /**
   * 切換編輯器與受訪者視角預覽模式
   * 若當前為預覽模式則退回編輯；反之將當前表單序列化後注入預覽狀態並開啟預覽。
   */
  public togglePreviewMode(): void {
    if (this.surveyStateService.isPreviewMode()) {
      this.surveyStateService.resetSurveyState();
    } else {
      const payload: SurveyPayload = this.formatSurveyData();
      this.surveyStateService.setForPreviewMode(payload);
    }
  }

  /**
   * 完整核驗問卷設計表單的所有必填與邏輯規範
   * 包含問卷標題、分類、時限合理性（結束時間晚於開始時間）、題目必填、選項必填與跳題目標有效性。
   * 若有錯誤則彈出錯誤對話框提示；核驗完全通過則進入資料送出流程。
   */
  protected checkSurveyData(): void {
    this.formBuilderGroup.markAllAsTouched();

    const errors: string[] = [];

    // === 階段 01：問卷核心資訊檢核 ===
    if (this.formBuilderGroup.get('title')?.invalid) {
      errors.push('• 階段 01：問卷標題為必填項目');
    }
    if (this.formBuilderGroup.get('category')?.invalid) {
      errors.push('• 階段 01：問卷分類尚未選擇');
    }

    if (this.formBuilderGroup.get('hasDescription')?.value) {
      if (!this.formBuilderGroup.get('description')?.value) {
        errors.push('• 階段 01：問卷前言／填答說明內容不可為空');
      }
    }

    if (this.formBuilderGroup.get('hasDescriptionImage')?.value) {
      if (!this.formBuilderGroup.get('descriptionImageBase64')?.value) {
        errors.push('• 階段 01：已勾選附加說明圖片，但尚未上傳圖片');
      }
    }

    // === 階段 02：受訪者身分與背景欄位檢核 ===
    if (this.formBuilderGroup.get('collectBasicInfo')?.value) {
      const demographicKeys = [
        'requireName', 'requireGender', 'requireAge', 'requireBirthday', 'requireNationalId',
        'requireMobilePhone', 'requireHomePhone', 'requireEmail', 'requireResidentialAddress',
        'requireRegisteredAddress', 'requireJobIndustry', 'requireJobTitle', 'requireDepartment',
        'requireEmployeeId', 'requireAcademicMajor', 'requireClassName', 'requireStudentId',
        'requireHeight', 'requireWeight'
      ];
      const hasAnyField = demographicKeys.some(key => !!this.formBuilderGroup.get(key)?.value);
      if (!hasAnyField) {
        errors.push('• 階段 02：已啟用「蒐集填答者基本資料」，請至少勾選一項欲蒐集的欄位');
      }
    }

    // === 階段 03：問卷題目與邏輯檢核 ===
    if (this.questionArray.length === 0) {
      errors.push('• 階段 03：問卷至少需建立一個問題');
    } else {
      for (let qIndex = 0; qIndex < this.questionArray.length; qIndex++) {
        const questionControl = this.questionArray.at(qIndex);
        const qNum = qIndex + 1;
        const type = questionControl.get('type')?.value;

        if (questionControl.get('type')?.invalid || !type) {
          errors.push(`• 階段 03 問題 ${qNum}：尚未選擇題目類型`);
        }

        if (questionControl.get('description')?.invalid) {
          errors.push(`• 階段 03 問題 ${qNum}：題目內容尚未填寫`);
        }

        if (type === 'radio' || type === 'multiRadio') {
          const optionsArray = this.getOptionsArray(qIndex);
          if (optionsArray.length === 0) {
            errors.push(`• 階段 03 問題 ${qNum}：至少需建立一個選項`);
          } else {
            for (let oIndex = 0; oIndex < optionsArray.length; oIndex++) {
              const optControl = optionsArray.at(oIndex);
              if (optControl.get('text')?.invalid) {
                const optLabel = this.getOptionLabel(oIndex);
                errors.push(`• 階段 03 問題 ${qNum} 選項 ${optLabel}：內容尚未填寫`);
              }

              if (
                type === 'radio' &&
                optControl.get('enableUnlockQuestion')?.value &&
                !optControl.get('unlockTargetUid')?.value
              ) {
                const optLabel = this.getOptionLabel(oIndex);
                errors.push(
                  `• 階段 03 問題 ${qNum} 選項 ${optLabel}：已啟用題目開啟設定，但尚未選擇欲開啟之目標題目`,
                );
              }
            }
          }
        }

        if (type === 'time' && questionControl.get('hasTimeLimit')?.value) {
          const minTime = questionControl.get('minTime')?.value;
          const maxTime = questionControl.get('maxTime')?.value;
          if (minTime && maxTime && minTime > maxTime) {
            errors.push(`• 階段 03 問題 ${qNum}：允許最早時間不能晚於允許最晚時間`);
          }
        }

        if (type === 'date' && questionControl.get('hasDateLimit')?.value) {
          const minDate = questionControl.get('minDate')?.value;
          const maxDate = questionControl.get('maxDate')?.value;
          if (minDate && maxDate && new Date(minDate) > new Date(maxDate)) {
            errors.push(`• 階段 03 問題 ${qNum}：允許最早日期不能晚於允許最晚日期`);
          }
        }
      }
    }

    // === 階段 04：發布規範與時程控管檢核 ===
    if (this.formBuilderGroup.get('hasTimeLimit')?.value) {
      const startDate = this.formBuilderGroup.get('startDate')?.value;
      const startTime = this.formBuilderGroup.get('startTime')?.value;
      const endDate = this.formBuilderGroup.get('endDate')?.value;
      const endTime = this.formBuilderGroup.get('endTime')?.value;

      if (!startDate || this.formBuilderGroup.get('startDate')?.invalid) {
        errors.push('• 階段 04：開始日期尚未填寫');
      }
      if (!startTime || this.formBuilderGroup.get('startTime')?.invalid) {
        errors.push('• 階段 04：開始時間尚未填寫');
      }
      if (!endDate || this.formBuilderGroup.get('endDate')?.invalid) {
        errors.push('• 階段 04：結束日期尚未填寫');
      }
      if (!endTime || this.formBuilderGroup.get('endTime')?.invalid) {
        errors.push('• 階段 04：結束時間尚未填寫');
      }

      if (startDate && startTime && endDate && endTime) {
        const todayStr = new Date().toISOString().split('T')[0];
        if (startDate < todayStr) {
          errors.push('• 階段 04：開始日期不能早於今日');
        }

        const startDateTime = new Date(`${startDate}T${startTime}`);
        const endDateTime = new Date(`${endDate}T${endTime}`);

        if (endDateTime.getTime() <= startDateTime.getTime()) {
          errors.push('• 階段 04：結束時間必須晚於開始時間');
        }
      }
    }

    if (errors.length > 0) {
      const errorDescription = `請修正以下問題後再送出：\n\n${errors.join('\n')}`;

      const errorData: DialogData = {
        title: '欄位驗證提醒',
        message: errorDescription,
        mode: 'errorMode',
        errorText: '了解',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

    this.sendSurveyData();
  }

  /**
   * 將表單原始值格式化為標準的問卷載荷物件 (SurveyPayload)
   * 包括格式化時限結構、受訪者基本資料蒐集欄位配置以及各題目的完整設定。
   * @returns 結構化之 SurveyPayload 物件
   */
  protected formatSurveyData(): SurveyPayload {
    const formRaw = this.formBuilderGroup.getRawValue();

    const timeLimit = formRaw.hasTimeLimit
      ? {
          startDate: formRaw.startDate ?? '',
          startTime: formRaw.startTime ?? '',
          endDate: formRaw.endDate ?? '',
          endTime: formRaw.endTime ?? '',
        }
      : undefined;

    const description = formRaw.hasDescription
      ? (formRaw.description ?? '')
      : undefined;

    const questions: SurveyQuestion[] = this.questionArray.controls.map(
      (questionControl, index) => {
        const questionValue = questionControl.getRawValue();
        const lockSources = this.getLockSources(questionValue.uid);

        const questionData: SurveyQuestion = {
          uid: questionValue.uid,
          id: index + 1,
          type: questionValue.type,
          isRequired: !!questionValue.isRequired,
          hasImage: !!questionValue.hasImage,
          imageBase64: questionValue.hasImage
            ? questionValue.imageBase64 || undefined
            : undefined,
          description: questionValue.description,
          isLockedByOther: !!lockSources,
          lockedBySummary: lockSources ?? undefined,
        };

        if (questionValue.type === 'scale') {
          questionData.scaleType = questionValue.scaleType;
        } else if (questionValue.type === 'radio' || questionValue.type === 'multiRadio') {
          questionData.options = (questionValue.options as { text: string }[]).map(
            (option) => option.text,
          );
          if (questionValue.type === 'radio') {
            questionData.optionsConfig = questionValue.options;
          }
        } else if (questionValue.type === 'time') {
          questionData.timeValue = questionValue.timeValue ?? '';
          questionData.hasTimeLimit = !!questionValue.hasTimeLimit;
          questionData.minTime = questionValue.hasTimeLimit
            ? questionValue.minTime || undefined
            : undefined;
          questionData.maxTime = questionValue.hasTimeLimit
            ? questionValue.maxTime || undefined
            : undefined;
        } else if (questionValue.type === 'date') {
          questionData.dateValue = questionValue.dateValue ?? '';
          questionData.hasDateLimit = !!questionValue.hasDateLimit;
          questionData.minDate = questionValue.hasDateLimit
            ? questionValue.minDate || undefined
            : undefined;
          questionData.maxDate = questionValue.hasDateLimit
            ? questionValue.maxDate || undefined
            : undefined;
        }

        return questionData;
      },
    );

    return {
      title: formRaw.title ?? '',
      category: formRaw.category ?? '',
      isPublished: !!formRaw.isPublished,
      hasTimeLimit: !!formRaw.hasTimeLimit,
      timeLimit,
      hasDescription: !!formRaw.hasDescription,
      description,
      hasDescriptionImage: formRaw.hasDescription
        ? !!formRaw.hasDescriptionImage
        : false,
      descriptionImageBase64:
        formRaw.hasDescription && formRaw.hasDescriptionImage
          ? formRaw.descriptionImageBase64 || undefined
          : undefined,
      basicInfoRequirements: {
        collectBasicInfo: !!formRaw.collectBasicInfo,
        requireName: !!formRaw.requireName,
        requireGender: !!formRaw.requireGender,
        requireAge: !!formRaw.requireAge,
        requireBirthday: !!formRaw.requireBirthday,
        requireHeight: !!formRaw.requireHeight,
        requireWeight: !!formRaw.requireWeight,
        requireMobilePhone: !!formRaw.requireMobilePhone,
        requireHomePhone: !!formRaw.requireHomePhone,
        requireEmail: !!formRaw.requireEmail,
        requireRegisteredAddress: !!formRaw.requireRegisteredAddress,
        requireResidentialAddress: !!formRaw.requireResidentialAddress,
        requireDepartment: !!formRaw.requireDepartment,
        requireAcademicMajor: !!formRaw.requireAcademicMajor,
        requireClassName: !!formRaw.requireClassName,
        requireStudentId: !!formRaw.requireStudentId,
        requireEmployeeId: !!formRaw.requireEmployeeId,
        requireNationalId: !!formRaw.requireNationalId,
        requireJobIndustry: !!formRaw.requireJobIndustry,
        requireJobTitle: !!formRaw.requireJobTitle,
      },
      questions,
    };
  }

  /**
   * 送出並發布問卷資料
   * 序列化問卷資料載荷，透過 UserEmitterService 發送設計完成事件至後端/父層保存。
   */
  protected sendSurveyData(): void {
    this._isSubmitted = true;
    const payload: SurveyPayload = this.formatSurveyData();
    this._userEmitterService.emitDesignSurvey(payload);
  }
}
