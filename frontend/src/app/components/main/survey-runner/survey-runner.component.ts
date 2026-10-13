/**
 * ============================================================================
 * 檔案名稱: survey-runner.component.ts
 * 所屬模組: 問卷填答與運行器模組 (Survey Runner & Response Module)
 * 責任描述: 負責受訪者端問卷填答介面呈現與校驗，涵蓋背景身分資料驗證、單選跳題解鎖邏輯（isQuestionVisible）、多選題 FormArray 同步、量表選取、離開守衛防呆及作答送出。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/survey-state.service, ../../../@services/dialog.service, ../../../@services/validation.service, ../../../@services/user-emitter.service, ../../../@interfaces/survey-model
 *   - 下游消費: app.routes.ts 路由導航調用, SurveyDesignComponent (內嵌預覽)
 * 邊界處理: 預覽模式自動 disable 全部表單控制項防止誤觸；路由離開守衛 canDeactivate 攔截未送出作答；跳題隱藏題目自動從答案酬載排除
 * ============================================================================
 */

import { Component, inject, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { SurveyStateService } from '../../../@services/survey-state.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';
import { ValidationService } from '../../../@services/validation.service';
import { UserEmitterService } from '../../../@services/user-emitter.service';
import {
  SurveyPayload,
  SurveyQuestion,
  SurveySubmissionPayload,
  SurveyAnswerItem,
} from '../../../@interfaces/survey-model';

/** 受訪者個人基本資料欄位設定結構 */
interface BasicInfoFieldConfig {
  key: string;
  label: string;
  control: AbstractControl;
}

/** 受訪者基本資料欄位元資料結構 */
interface BasicInfoFieldMeta {
  key: string;
  label: string;
}

/**
 * 問卷填答與運行器組件 (SurveyRunnerComponent)
 * 負責實際受訪者填答問卷流程與預覽視窗展示。
 * 支援動態受訪者資料驗證（臺灣身分證字號雙向權重演算法、手機 09 開頭）、
 * 單選題條件動態跳題解鎖（isQuestionVisible）、多選題 FormArray 動態綁定、
 * 各類量表選項轉換與送出防呆驗證。
 */
@Component({
  selector: 'app-survey-runner',
  templateUrl: './survey-runner.component.html',
  styleUrl: './survey-runner.component.scss',
  imports: [ReactiveFormsModule],
})
export class SurveyRunnerComponent implements OnInit {
  /** 響應式表單建構器 */
  private readonly _formBuilder = inject(FormBuilder);
  /** 系統對話框管理服務 */
  private readonly _dialogService = inject(DialogService);
  /** 自訂字串與格式防呆檢驗服務 */
  private readonly _validationService = inject(ValidationService);
  /** 使用者事件廣播服務（提交問卷回饋） */
  private readonly _userEmitter = inject(UserEmitterService);
  /** 問卷狀態管理服務（取得當前正在填答的問卷模型） */
  protected readonly surveyStateService = inject(SurveyStateService);

  /** 填答執行表單群組 */
  protected runnerFormGroup: FormGroup = this._formBuilder.group({});
  /** 問卷填答是否已成功提交送出之狀態旗標 */
  private _isSubmitted: boolean = false;

  /**
   * Angular 生命週期鉤子 (ngOnInit)
   * 依據問卷定義動態初始化受訪者欄位與題目 controls；若當前為預覽模式則自動禁用全部表單控制項。
   */
  public ngOnInit(): void {
    this.runnerFormGroup = this.formBuilderGroupInit();
    if (this.isPreview) {
      this.runnerFormGroup.disable();
    }
  }

  /**
   * 路由離開守衛 (canDeactivate)
   * 當填答者試圖離開問卷填答頁面時，若非設計者預覽模式且尚未送出填答結果，
   * 攔截跳轉並彈出操作確認對話框詢問「是否確定跳出？尚未提交的問卷填答內容將會遺失。」。
   * 只有在使用者點選「是」確認離開時才放行路由跳出；
   * 若點選「返回」或關閉對話框，則由 Angular Router 取消跳轉，完整保留當前已填答之所有題目與控制項內容。
   */
  public canDeactivate(): Observable<boolean> | boolean {
    if (!this.isPreview && !this._isSubmitted) {
      const confirmData: DialogData = {
        title: '跳出確認',
        message: '是否確定跳出？尚未提交的問卷填答內容將會遺失。',
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
   * 取得當前填答的問卷資料載荷 (SurveyPayload)
   */
  protected get survey(): SurveyPayload {
    return this.surveyStateService.currentSurvey();
  }

  /**
   * 判斷當前是否處於設計者「純預覽」模式
   */
  protected get isPreview(): boolean {
    return this.surveyStateService.isPreviewMode();
  }

  /**
   * 初始化填答表單群組
   * 包含受訪者基本資料控制項以及每道題目的 FormArray 或 FormControl。
   * @returns FormGroup 填答表單物件
   */
  private formBuilderGroupInit(): FormGroup {
    const group = this._formBuilder.group({});

    const basicInfoFields = this.createBasicInfoFields();
    for (const field of basicInfoFields) {
      group.addControl(field.key, field.control);
    }

    for (const question of this.survey.questions) {
      if (question.type === 'multiRadio') {
        const arrayControl = this._formBuilder.array(
          [],
          question.isRequired ? [Validators.required] : [],
        );
        group.addControl(question.uid, arrayControl);
      } else {
        const defaultVal =
          question.type === 'time'
            ? (question.timeValue ?? '')
            : question.type === 'date'
              ? (question.dateValue ?? '')
              : '';
        const control = this._formBuilder.control(
          defaultVal,
          question.isRequired ? [Validators.required] : [],
        );
        group.addControl(question.uid, control);
      }
    }

    return group;
  }

  /**
   * 依據問卷基本資料需求動態生成個人資料欄位控制項
   * 包括身分證字號雙向演算法核驗、臺灣手機格式檢核、姓名中文長度檢核等。
   * @returns BasicInfoFieldConfig 陣列
   */
  private createBasicInfoFields(): BasicInfoFieldConfig[] {
    const fields: BasicInfoFieldConfig[] = [];
    const requirements = this.survey.basicInfoRequirements;

    if (!requirements?.collectBasicInfo) {
      return fields;
    }

    if (requirements.requireName) {
      fields.push({
        key: 'name',
        label: '姓名（須為 2 至 6 字中文）',
        control: this._formBuilder.control('', [
          Validators.required,
          this._validationService.chineseNameValidator(2, 6),
        ]),
      });
    }
    if (requirements.requireGender) {
      fields.push({
        key: 'gender',
        label: '性別',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireAge) {
      fields.push({
        key: 'age',
        label: '年紀',
        control: this._formBuilder.control(null, [
          Validators.required,
          Validators.min(0),
        ]),
      });
    }
    if (requirements.requireBirthday) {
      fields.push({
        key: 'birthday',
        label: '生日',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireHeight) {
      fields.push({
        key: 'height',
        label: '身高',
        control: this._formBuilder.control(null, [Validators.required]),
      });
    }
    if (requirements.requireWeight) {
      fields.push({
        key: 'weight',
        label: '體重',
        control: this._formBuilder.control(null, [Validators.required]),
      });
    }
    if (requirements.requireMobilePhone) {
      fields.push({
        key: 'mobilePhone',
        label: '手機電話（09 開頭）',
        control: this._formBuilder.control('', [
          Validators.required,
          this._validationService.taiwanPhoneValidator(),
        ]),
      });
    }
    if (requirements.requireHomePhone) {
      fields.push({
        key: 'homePhone',
        label: '住家電話',
        control: this._formBuilder.control('', [
          Validators.required,
          this._validationService.taiwanTelValidator(),
        ]),
      });
    }
    if (requirements.requireEmail) {
      fields.push({
        key: 'email',
        label: '電子信箱',
        control: this._formBuilder.control('', [
          Validators.required,
          Validators.email,
        ]),
      });
    }
    if (requirements.requireRegisteredAddress) {
      fields.push({
        key: 'registeredAddress',
        label: '戶籍地址',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireResidentialAddress) {
      fields.push({
        key: 'residentialAddress',
        label: '住家地址',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireDepartment) {
      fields.push({
        key: 'department',
        label: '所屬單位',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireAcademicMajor) {
      fields.push({
        key: 'academicMajor',
        label: '科系',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireClassName) {
      fields.push({
        key: 'className',
        label: '班級名稱',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireStudentId) {
      fields.push({
        key: 'studentId',
        label: '學號',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireEmployeeId) {
      fields.push({
        key: 'employeeId',
        label: '員工編號',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireNationalId) {
      fields.push({
        key: 'nationalId',
        label: '身分證字號',
        control: this._formBuilder.control('', [
          Validators.required,
          this._validationService.taiwanNationalIdValidator(),
        ]),
      });
    }
    if (requirements.requireJobIndustry) {
      fields.push({
        key: 'jobIndustry',
        label: '職業領域',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }
    if (requirements.requireJobTitle) {
      fields.push({
        key: 'jobTitle',
        label: '職業',
        control: this._formBuilder.control('', [Validators.required]),
      });
    }

    return fields;
  }

  /**
   * 取得基本資料所有可能欄位的元資訊標籤對應表
   * @returns BasicInfoFieldMeta 陣列
   */
  private getBasicInfoFieldMetaList(): BasicInfoFieldMeta[] {
    return [
      { key: 'name', label: '姓名（須為 2 至 6 字中文）' },
      { key: 'gender', label: '性別' },
      { key: 'age', label: '年紀' },
      { key: 'birthday', label: '生日' },
      { key: 'height', label: '身高' },
      { key: 'weight', label: '體重' },
      { key: 'mobilePhone', label: '手機電話（09 開頭）' },
      { key: 'homePhone', label: '住家電話' },
      { key: 'email', label: '電子信箱' },
      { key: 'registeredAddress', label: '戶籍地址' },
      { key: 'residentialAddress', label: '住家地址' },
      { key: 'department', label: '所屬單位' },
      { key: 'academicMajor', label: '科系' },
      { key: 'className', label: '班級名稱' },
      { key: 'studentId', label: '學號' },
      { key: 'employeeId', label: '員工編號' },
      { key: 'nationalId', label: '身分證字號' },
      { key: 'jobIndustry', label: '職業領域' },
      { key: 'jobTitle', label: '職業' },
    ];
  }

  /**
   * 取得特定複選題在表單中的 FormArray
   * @param questionUid 複選題的 UID
   * @returns 該題對應的 FormArray
   */
  protected getMultiRadioArray(questionUid: string): FormArray {
    return this.runnerFormGroup.get(questionUid) as FormArray;
  }

  /**
   * 判斷特定複選題選項目前是否已被勾選
   * @param questionUid 複選題 UID
   * @param optionValue 選項文字值
   * @returns 若已勾選則為 true，否則為 false
   */
  protected isMultiRadioChecked(
    questionUid: string,
    optionValue: string,
  ): boolean {
    const array = this.getMultiRadioArray(questionUid);
    if (!array) return false;

    for (let i = 0; i < array.length; i++) {
      if (array.at(i).value === optionValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * 處理複選題 Checkbox 勾選/取消狀態異動
   * 勾選時推入 control，取消時尋找索引並從 FormArray 中移除。
   * @param questionUid 複選題 UID
   * @param optionValue 選項值
   * @param event 勾選框變更事件
   */
  protected onMultiRadioChange(
    questionUid: string,
    optionValue: string,
    event: Event,
  ): void {
    if (this.isPreview) return;

    const checkbox = event.target as HTMLInputElement;
    const array = this.getMultiRadioArray(questionUid);

    if (checkbox.checked) {
      array.push(this._formBuilder.control(optionValue));
    } else {
      let targetIndex = -1;
      for (let i = 0; i < array.length; i++) {
        if (array.at(i).value === optionValue) {
          targetIndex = i;
          break;
        }
      }
      if (targetIndex !== -1) {
        array.removeAt(targetIndex);
      }
    }
    array.markAsTouched();
    array.updateValueAndValidity();
  }

  /**
   * 判斷題目當前是否應該顯示（動態條件跳題邏輯計算核心）
   * 若題目未受任何跳題規則鎖定，則預設常態顯示；
   * 若受到上游單選題鎖定，則必須在使用者勾選對應之解鎖選項時，才回傳 true 展開顯示。
   * @param question 目標題目資料模型
   * @returns 該題目是否處於可見狀態
   */
  protected isQuestionVisible(question: SurveyQuestion): boolean {
    if (!question.isLockedByOther) return true;

    for (const parentQuestion of this.survey.questions) {
      if (parentQuestion.type === 'radio' && parentQuestion.optionsConfig) {
        const val = this.runnerFormGroup.get(parentQuestion.uid)?.value;
        for (const option of parentQuestion.optionsConfig) {
          if (
            option.text === val &&
            option.enableUnlockQuestion &&
            option.unlockTargetUid === question.uid
          ) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * 根據量表設定類型 (scaleType) 解析並回傳對應的量表選項標籤清單
   * @param scaleType 量表類型字串代碼
   * @returns 依序排列的量表級距標籤字串陣列
   */
  protected getScaleOptions(scaleType?: string): string[] {
    switch (scaleType) {
      case 'scale-text-satisfaction-5':
        return ['非常不滿意', '不滿意', '普通', '滿意', '非常滿意'];
      case 'scale-text-agreement-5':
        return ['非常不同意', '不同意', '普通', '同意', '非常同意'];
      case 'scale-text-importance-5':
        return ['非常不重要', '不重要', '普通', '重要', '非常重要'];
      case 'scale-text-preference-5':
        return ['非常不喜歡', '不喜歡', '普通', '喜歡', '非常喜歡'];
      case 'scale-text-frequency-5':
        return ['從不', '很少', '有時', '經常', '總是'];
      case 'scale-text-satisfaction-4':
        return ['非常不滿意', '不滿意', '滿意', '非常滿意'];
      case 'scale-num-1-5':
        return ['1', '2', '3', '4', '5'];
      case 'scale-num-1-10':
        return ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
      case 'scale-num-0-10':
        return ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
      default:
        return ['非常不滿意', '不滿意', '普通', '滿意', '非常滿意'];
    }
  }

  /**
   * 執行受訪者填答完整性核驗
   * 核驗所有啟用的基本資料欄位格式是否正確，並檢查所有處於「可見狀態 (isQuestionVisible)」的必填問題是否皆已作答。
   * 若有遺漏或格式不符，彈出提醒對話框並終止提交；核驗通過則呼叫 sendRunnerData 進行送出。
   */
  protected checkRunnerData(): void {
    if (this.isPreview) return;

    this.runnerFormGroup.markAllAsTouched();
    const errors: string[] = [];

    for (const meta of this.getBasicInfoFieldMetaList()) {
      const control = this.runnerFormGroup.get(meta.key);
      if (control && control.invalid) {
        errors.push(`• 基本資料：${meta.label} 尚未填寫或格式不符`);
      }
    }

    for (let questionIndex = 0; questionIndex < this.survey.questions.length; questionIndex++) {
      const question = this.survey.questions[questionIndex];
      if (this.isQuestionVisible(question)) {
        const control = this.runnerFormGroup.get(question.uid);
        if (
          question.isRequired &&
          (!control ||
            control.invalid ||
            (question.type === 'multiRadio' && control.value.length === 0))
        ) {
          errors.push(`• 問題 ${questionIndex + 1}：此必填項目尚未完成`);
        }
      }
    }

    if (errors.length > 0) {
      const errorData: DialogData = {
        title: '請修正以下欄位',
        message: errors.join('\n'),
        mode: 'errorMode',
        confirmText: '了解',
        showCloseButton: true,
      };
      this._dialogService.showDialog(errorData).subscribe();
      return;
    }

    this.sendRunnerData();
  }

  /**
   * 擷取填答表單中所有受訪者基本資料鍵值對
   * @returns 結構化的基本資料鍵值映射物件
   */
  private extractBasicInfo(): SurveySubmissionPayload['basicInfo'] {
    const raw = this.runnerFormGroup.getRawValue();
    const basicInfo: Record<string, unknown> = {};

    for (const meta of this.getBasicInfoFieldMetaList()) {
      const control = this.runnerFormGroup.get(meta.key);
      if (control) {
        basicInfo[meta.key] = raw[meta.key];
      }
    }

    return basicInfo;
  }

  /**
   * 組裝並送出問卷填答回饋資料
   * 收集受訪者基本資料與所有可見問題的答案清單，封裝為 SurveySubmissionPayload 並廣播提交事件。
   */
  private sendRunnerData(): void {
    const raw = this.runnerFormGroup.getRawValue();
    const basicInfo = this.extractBasicInfo();

    const answers: SurveyAnswerItem[] = [];
    for (const question of this.survey.questions) {
      if (this.isQuestionVisible(question)) {
        answers.push({
          questionUid: question.uid,
          questionId: question.id,
          type: question.type,
          answer: raw[question.uid] ?? null,
        });
      }
    }

    const payload: SurveySubmissionPayload = {
      surveyTitle: this.survey.title,
      surveyCategory: this.survey.category,
      submittedAt: new Date().toISOString(),
      basicInfo,
      answers,
    };

    this._isSubmitted = true;
    this._userEmitter.emitSubmitSurvey(payload);
  }
}
