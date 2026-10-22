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

import { ChangeDetectorRef, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
import { SurveyGetterService } from '../../../@services/survey-getter.service';
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
  imports: [ReactiveFormsModule, RouterLink],
})
export class SurveyRunnerComponent implements OnInit {
  /** 路由參數服務 */
  private readonly _route = inject(ActivatedRoute);
  /** 問卷查詢服務 */
  private readonly _surveyGetter = inject(SurveyGetterService);
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

  /** Angular 路由服務 */
  private readonly _router = inject(Router);
  /** 畫面變更檢測服務 */
  private readonly _cdr = inject(ChangeDetectorRef);
  /** 填答執行表單群組 */
  protected runnerFormGroup: FormGroup = this._formBuilder.group({});
  /** 問卷填答是否已成功提交送出之狀態旗標 */
  private _isSubmitted: boolean = false;

  /** 問卷是否處於未開放或已截止狀態信號 */
  public readonly isUnavailable = signal<boolean>(false);
  /** 未開放狀態主標題信號 */
  public readonly unavailableTitle = signal<string>('問卷暫未開放填答');
  /** 未開放狀態具體說明敘述信號 */
  public readonly unavailableMessage = signal<string>('');
  /** 填答時程區間說明（若有） */
  public readonly unavailableTimeRange = signal<string>('');

  /** 產業領域是否選取「其他」自訂選項 */
  public readonly isOtherIndustrySelected = signal<boolean>(false);
  /** 自訂其他產業領域文字暫存 */
  public readonly customOtherIndustry = signal<string>('');

  /**
   * 計算返回按鈕的友善提示文字
   */
  public get backButtonLabel(): string {
    const from = this._route.snapshot.queryParams['from'];
    if (from === 'survey-management') {
      return '返回問卷管理';
    }
    if (from === 'lobby') {
      return '返回問卷大廳';
    }
    return '返回上一頁';
  }

  /**
   * 點擊頂部返回按鈕，依來源精準回到問卷管理或大廳
   */
  public onBack(): void {
    const from = this._route.snapshot.queryParams['from'];
    if (from === 'survey-management') {
      this._router.navigateByUrl('/survey-management');
      return;
    }
    if (from === 'lobby') {
      this._router.navigateByUrl('/lobby');
      return;
    }
    if (window.history.length > 1) {
      window.history.back();
    } else {
      this._router.navigateByUrl('/lobby');
    }
  }

  /**
   * 檢查問卷當前開放與時效狀態
   * 若為非預覽模式且問卷關閉或逾期，設定未開放狀態以展示說明敘述
   * @param survey 目標問卷載荷
   * @param errorMsg 遠端請求錯誤訊息 (若有)
   */
  private _checkSurveyAvailability(survey: SurveyPayload | null, errorMsg?: string): void {
    if (this.isPreview) {
      this.isUnavailable.set(false);
      return;
    }

    if (errorMsg) {
      this.isUnavailable.set(true);
      if (errorMsg.includes('截止') || errorMsg.includes('過期')) {
        this.unavailableTitle.set('問卷填答已截止');
        this.unavailableMessage.set(errorMsg);
      } else if (errorMsg.includes('尚未') || errorMsg.includes('未到達')) {
        this.unavailableTitle.set('問卷尚未開始開放填答');
        this.unavailableMessage.set(errorMsg);
      } else {
        this.unavailableTitle.set('本問卷目前未開放填答');
        this.unavailableMessage.set(errorMsg);
      }
      return;
    }

    if (!survey) {
      this.isUnavailable.set(true);
      this.unavailableTitle.set('查無此問卷專案');
      this.unavailableMessage.set('問卷專案不存在或已被發起人移除。');
      return;
    }

    // 檢查開放填答狀態：若發起人關閉填答 (isOpen === false)，則停止受訪者作答
    const isCurrentlyOpen = survey.isOpen !== undefined ? survey.isOpen : survey.isPublished;
    if (isCurrentlyOpen === false) {
      this.isUnavailable.set(true);
      this.unavailableTitle.set('問卷目前未開放填答');
      this.unavailableMessage.set('本問卷發起人目前已關閉開放填寫，暫不接受受訪者作答。若有任何疑問，請洽詢問卷發起人。');
      return;
    }

    // 檢查時效設定 (hasTimeLimit)
    if (survey.hasTimeLimit && survey.timeLimit) {
      const { startDate, startTime, endDate, endTime } = survey.timeLimit;
      const now = new Date();

      if (startDate && startTime) {
        const startDt = new Date(`${startDate}T${startTime}`);
        if (!isNaN(startDt.getTime()) && now < startDt) {
          this.isUnavailable.set(true);
          this.unavailableTitle.set('問卷尚未開始開放填答');
          this.unavailableMessage.set(`本問卷預計於 ${startDate} ${startTime} 開放填寫，目前尚未到達作答時間，請於開放後再次前往填答。`);
          this.unavailableTimeRange.set(`填答有效期間：${startDate} ${startTime} 至 ${endDate} ${endTime}`);
          return;
        }
      }

      if (endDate && endTime) {
        const endDt = new Date(`${endDate}T${endTime}`);
        if (!isNaN(endDt.getTime()) && now > endDt) {
          this.isUnavailable.set(true);
          this.unavailableTitle.set('問卷填答已截止');
          this.unavailableMessage.set(`本問卷已於 ${endDate} ${endTime} 截止填答，已無法再進行填寫，非常感謝您的關注與支持！`);
          this.unavailableTimeRange.set(`填答有效期間：${startDate} ${startTime} 至 ${endDate} ${endTime}`);
          return;
        }
      }
    }

    this.isUnavailable.set(false);
  }

  /**
   * Angular 生命週期鉤子 (ngOnInit)
   * 檢查網址是否有指定問卷 ID 參數 (?surveyId=... 或 ?id=...)，支援專屬連結直達載入；若為預覽模式則自動禁用全部表單控制項。
   */
  public ngOnInit(): void {
    const queryId =
      this._route.snapshot.queryParams['surveyId'] ||
      this._route.snapshot.queryParams['id'];

    if (queryId) {
      this._surveyGetter.getSurveyById(String(queryId)).subscribe({
        next: (loadedSurvey) => {
          if (loadedSurvey) {
            this.surveyStateService.setForRunMode(loadedSurvey);
            this._checkSurveyAvailability(loadedSurvey);
            this.runnerFormGroup = this.formBuilderGroupInit();
          } else {
            this._checkSurveyAvailability(null, '查無此問卷專案');
            this.runnerFormGroup = this.formBuilderGroupInit();
          }
        },
        error: (err) => {
          const msg =
            !err || err.status === 0
              ? '後端伺服器連線異常，無法載入問卷題目（請確認 8080 後端服務已啟動）。'
              : err?.message || '此問卷目前暫不開放填答。';
          this._checkSurveyAvailability(null, msg);
          this.runnerFormGroup = this.formBuilderGroupInit();
        },
      });
      return;
    }

    this._checkSurveyAvailability(this.survey);
    this.runnerFormGroup = this.formBuilderGroupInit();
  }

  /**
   * 路由離開守衛 (canDeactivate)
   * 當填答者試圖離開問卷填答頁面時：
   * 1. 預覽模式直接放行，絕不彈出警告阻擋。
   * 2. 只有在尚未提交且受訪者已經實質修改表單控制項 (dirty) 時，才彈出防呆確認對話框。
   */
  public canDeactivate(): Observable<boolean> | boolean {
    if (this.isPreview || this._isSubmitted) {
      return true;
    }

    if (this.runnerFormGroup.dirty) {
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
   * 判斷指定表單控制項是否處於不合法狀態且已被觸碰或編輯過
   * 支援受訪者在輸入文字或離開焦點時，立即呈現紅字與紅框警示
   * @param fieldKey 控制項名稱
   * @returns boolean 是否應即刻呈現紅字與紅框
   */
  public isFieldInvalid(fieldKey: string): boolean {
    const ctrl = this.runnerFormGroup.get(fieldKey);
    if (!ctrl) return false;
    return ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  /**
   * 處理欄位失去焦點 (Blur) 事件
   * 當使用者離開 input/select 時，即刻標記為 touched 並觸發驗證，與登入頁面即時紅字標準對齊
   * @param fieldKey 控制項名稱
   */
  public onFieldBlur(fieldKey: string): void {
    const ctrl = this.runnerFormGroup.get(fieldKey);
    if (ctrl) {
      ctrl.markAsTouched();
      ctrl.updateValueAndValidity();
    }
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
  /**
   * 判斷題目當前是否應該顯示（動態條件跳題邏輯計算核心）
   * 若題目未受任何跳題規則鎖定，則預設常態顯示；
   * 若受到上游題目的特定選項鎖定，則必須在使用者選取對應之解鎖選項時，才展開顯示。
   * @param question 目標題目資料模型
   * @returns 該題目是否處於可見狀態
   */
  protected isQuestionVisible(question: SurveyQuestion): boolean {
    if (!question.isLockedByOther) return true;

    for (const parentQuestion of this.survey.questions) {
      if (parentQuestion.optionsConfig && parentQuestion.optionsConfig.length > 0) {
        const val = this.runnerFormGroup.get(parentQuestion.uid)?.value;
        if (!val) continue;

        for (const option of parentQuestion.optionsConfig) {
          if (!option.enableUnlockQuestion) {
            continue;
          }

          // [Why]: 條件解鎖目標支援 UUID、後端 ID、題號代碼與順序索引多維相容匹配
          const targetKey = String(option.unlockTargetUid || '').trim();
          const targetId = String(question.id ?? '').trim();
          const targetUid = String(question.uid ?? '').trim();
          const targetCode = String((question as any).questionCode ?? '').trim();

          const isTargetMatch =
            targetKey === targetUid ||
            (targetId && targetKey === targetId) ||
            (targetCode && targetKey === targetCode);

          if (!isTargetMatch) {
            continue;
          }

          // 單選或文字選項內容精準匹配
          if (String(val).trim() === String(option.text).trim()) {
            return true;
          }

          // 是非題匹配 (支援 true/false 與 是/否 相容轉換)
          if (parentQuestion.type === 't/f') {
            if ((val === 'true' || val === true || val === '是') && option.sortOrder === 1) {
              return true;
            }
            if ((val === 'false' || val === false || val === '否') && option.sortOrder === 2) {
              return true;
            }
          }

          // 複選題匹配 (陣列包含)
          if (Array.isArray(val) && val.some((v) => String(v).trim() === String(option.text).trim())) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * 根據量表設定類型 (scaleType) 或題目 minRating/maxRating 解析並回傳對應的量表選項標籤清單
   * @param question 題目物件或 scaleType 字串
   * @returns 依序排列的量表級距標籤字串陣列
   */
  protected getScaleOptions(question: any): string[] {
    if (!question) {
      return ['非常不滿意', '不滿意', '普通', '滿意', '非常滿意'];
    }

    const scaleType = typeof question === 'string' ? question : question.scaleType;
    const minRating = typeof question === 'object' ? question.minRating : undefined;
    const maxRating = typeof question === 'object' ? question.maxRating : undefined;
    const desc = typeof question === 'object' ? (question.description || '') : '';

    // 1. 若題目資料具備 minRating 與 maxRating
    if (minRating !== undefined && maxRating !== undefined && minRating !== null && maxRating !== null) {
      const opts: string[] = [];
      for (let i = minRating; i <= maxRating; i++) {
        opts.push(i.toString());
      }
      if (opts.length > 0) {
        return opts;
      }
    }

    // 2. 檢測題目文字中的顯式分值範圍提示（防禦後端或歷史問卷未填寫 minRating/maxRating 之情境）
    if (desc.includes('0-10') || desc.includes('0~10') || desc.includes('0至10') || desc.includes('NPS') || desc.includes('淨推薦值')) {
      return ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
    }
    if (desc.includes('1-10') || desc.includes('1~10') || desc.includes('1至10')) {
      return ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
    }
    if (desc.includes('1-5') || desc.includes('1~5') || desc.includes('1至5') || desc.includes('1最低，5最高') || desc.includes('1最低、5最高')) {
      return ['1', '2', '3', '4', '5'];
    }
    if (desc.includes('1-7') || desc.includes('1~7') || desc.includes('1至7')) {
      return ['1', '2', '3', '4', '5', '6', '7'];
    }

    // 3. 依據 scaleType 具體代碼判斷
    switch (scaleType) {
      case 'scale-num-1-5':
        return ['1', '2', '3', '4', '5'];
      case 'scale-num-1-10':
        return ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
      case 'scale-num-0-10':
      case 'nps':
        return ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
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
      case 'scale-text-satisfaction-5':
      default:
        return ['非常不滿意', '不滿意', '普通', '滿意', '非常滿意'];
    }
  }

  /**
   * 受訪者點擊量表選項時精準賦值，並確保單選互斥與即時驗證刷新
   * @param questionUid 題目唯一識別碼
   * @param value 所選取之值
   */
  protected selectScaleOption(questionUid: string, value: string): void {
    const ctrl = this.runnerFormGroup.get(questionUid);
    if (ctrl) {
      ctrl.setValue(String(value));
      ctrl.markAsDirty();
      ctrl.markAsTouched();
      ctrl.updateValueAndValidity();
      this._cdr.markForCheck();
    }
  }

  /**
   * 受訪者點擊是非題 (t/f) 選項時精準賦值
   * @param questionUid 題目唯一識別碼
   * @param value 所選取之布林字串值 ('true' | 'false')
   */
  protected selectTfOption(questionUid: string, value: string): void {
    const ctrl = this.runnerFormGroup.get(questionUid);
    if (ctrl) {
      ctrl.setValue(value);
      ctrl.markAsDirty();
      ctrl.markAsTouched();
      ctrl.updateValueAndValidity();
      this._cdr.markForCheck();
    }
  }

  /**
   * 受訪者選取單選題 (radio) 選項時精準賦值
   * @param questionUid 題目唯一識別碼
   * @param opt 所選取之選項文字
   */
  protected selectRadioOption(questionUid: string, opt: string): void {
    const ctrl = this.runnerFormGroup.get(questionUid);
    if (ctrl) {
      ctrl.setValue(opt);
      ctrl.markAsDirty();
      ctrl.markAsTouched();
      ctrl.updateValueAndValidity();
      this._cdr.markForCheck();
    }
  }

  /**
   * 監聽日期或時間欄位變更，並即時同步至對應之 FormControl
   * @param questionUid 題目唯一識別碼
   * @param event 原生 DOM 事件
   */
  protected onDateTimeChange(questionUid: string, event: Event): void {
    if (this.isPreview) return;
    const val = (event.target as HTMLInputElement).value;
    const ctrl = this.runnerFormGroup.get(questionUid);
    if (ctrl) {
      ctrl.setValue(val || '');
      ctrl.markAsDirty();
      ctrl.markAsTouched();
      ctrl.updateValueAndValidity();
    }
  }

  /**
   * 監聽產業領域選單變更，當選取「其他」時開啟自訂輸入框
   */
  protected onIndustryChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    const isOther = val === '其他';
    this.isOtherIndustrySelected.set(isOther);

    const control = this.runnerFormGroup.get('jobIndustry');
    if (control) {
      if (isOther) {
        const customVal = this.customOtherIndustry().trim();
        control.setValue(customVal ? customVal : '其他');
      } else {
        control.setValue(val);
      }
      control.markAsDirty();
      control.updateValueAndValidity();
    }
  }

  /**
   * 監聽自訂其他產業領域輸入
   */
  protected onCustomIndustryInput(event: Event): void {
    const val = (event.target as HTMLInputElement).value || '';
    this.customOtherIndustry.set(val);
    const control = this.runnerFormGroup.get('jobIndustry');
    if (control) {
      control.setValue(val.trim() ? val.trim() : '其他');
      control.markAsDirty();
      control.updateValueAndValidity();
    }
  }

  /**
   * 強制從原生 DOM 元素全方位雙向同步所有作答值至 Angular 表單控制項
   * [Why]: 徹底杜絕瀏覽器自動填入 (Autofill)、原生日期/時間選取器未觸發事件、
   * 或點擊標籤後 Angular 表單內部延遲未同步，導致用戶明明填寫了卻遭誤判未填的頑疾！
   */
  private _syncDomValuesToForm(): void {
    const formElement = document.querySelector('.runner-form');
    if (!formElement) return;

    // 1. 同步所有具有 [formControlName] 的輸入框與下拉選單
    const inputs = formElement.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('[formControlName]');
    inputs.forEach((inputEl) => {
      const key = inputEl.getAttribute('formControlName');
      if (!key) return;

      const control = this.runnerFormGroup.get(key);
      if (!control) return;

      let domVal = inputEl.value;
      if (typeof domVal === 'string') {
        domVal = domVal.trim();
      }

      let selectText = '';
      if (inputEl instanceof HTMLSelectElement && inputEl.selectedIndex >= 0) {
        selectText = inputEl.options[inputEl.selectedIndex]?.text?.trim() || '';
      }

      if (domVal || selectText) {
        if (key === 'age') {
          const parsed = parseInt(domVal, 10);
          if (!isNaN(parsed) && parsed > 0) {
            control.setValue(parsed);
          }
        } else if (key === 'gender') {
          const combined = `${domVal} ${selectText}`.toLowerCase();
          if (combined.includes('女') || combined.includes('female')) {
            control.setValue('female');
          } else if (combined.includes('男') || combined.includes('male')) {
            control.setValue('male');
          } else if (combined.includes('其他') || combined.includes('other')) {
            control.setValue('other');
          } else if (domVal) {
            control.setValue(domVal);
          }
        } else if (key === 'jobIndustry') {
          if (this.isOtherIndustrySelected() && this.customOtherIndustry().trim()) {
            control.setValue(this.customOtherIndustry().trim());
          } else if (domVal && domVal !== '其他') {
            control.setValue(domVal);
          }
        } else if (domVal) {
          control.setValue(domVal);
        }
        control.markAsDirty();
        control.updateValueAndValidity();
      }
    });

    // 2. 全面掃描問卷題目在 DOM 中的真實選取狀態（radio、t/f、scale、multiRadio、date、time、text）
    for (const q of this.survey.questions) {
      const control = this.runnerFormGroup.get(q.uid);
      if (!control) continue;

      switch (q.type) {
        case 'radio': {
          const checkedRadio = formElement.querySelector<HTMLInputElement>(`input[name="radio_${q.uid}"]:checked`);
          if (checkedRadio && checkedRadio.value) {
            control.setValue(checkedRadio.value);
            control.markAsDirty();
            control.updateValueAndValidity();
          }
          break;
        }
        case 't/f': {
          const checkedTf = formElement.querySelector<HTMLInputElement>(`input[name="tf_radio_${q.uid}"]:checked`);
          if (checkedTf && checkedTf.value) {
            control.setValue(checkedTf.value);
            control.markAsDirty();
            control.updateValueAndValidity();
          }
          break;
        }
        case 'scale': {
          const checkedScale = formElement.querySelector<HTMLInputElement>(`input[name="scale_${q.uid}"]:checked`);
          if (checkedScale && checkedScale.value) {
            control.setValue(checkedScale.value);
            control.markAsDirty();
            control.updateValueAndValidity();
          }
          break;
        }
        case 'multiRadio': {
          const multiArray = control as FormArray;
          const cardEl = formElement.querySelector(`.runner-question-card:has(input[type="checkbox"])`);
          if (cardEl) {
            const matchedValues: string[] = [];
            cardEl.querySelectorAll<HTMLInputElement>(`input[type="checkbox"]:checked`).forEach((cb) => {
              if (cb.value && (q.options || []).includes(cb.value)) {
                matchedValues.push(cb.value);
              }
            });
            if (matchedValues.length > 0 && multiArray.length === 0) {
              matchedValues.forEach((v) => multiArray.push(this._formBuilder.control(v)));
              multiArray.markAsDirty();
              multiArray.updateValueAndValidity();
            }
          }
          break;
        }
        case 'date':
        case 'time': {
          const dateOrTimeInput = formElement.querySelector<HTMLInputElement>(`input[formControlName="${q.uid}"]`);
          if (dateOrTimeInput && dateOrTimeInput.value && !control.value) {
            control.setValue(dateOrTimeInput.value);
            control.markAsDirty();
            control.updateValueAndValidity();
          }
          break;
        }
        case 'text': {
          const textInput = formElement.querySelector<HTMLTextAreaElement>(`textarea[formControlName="${q.uid}"]`);
          if (textInput && textInput.value && !control.value) {
            control.setValue(textInput.value.trim());
            control.markAsDirty();
            control.updateValueAndValidity();
          }
          break;
        }
        default:
          break;
      }
    }
  }

  /**
   * 執行受訪者填答完整性核驗
   * 核驗所有啟用的基本資料欄位格式是否正確，並檢查所有處於「可見狀態 (isQuestionVisible)」的必填問題是否皆已作答。
   * 若有遺漏或格式不符，彈出提醒對話框並終止提交；核驗通過則呼叫 sendRunnerData 進行送出。
   */
  protected checkRunnerData(): void {
    if (this.isPreview) {
      const previewNotice: DialogData = {
        title: '預覽模式說明',
        message: '您目前正處於問卷預覽視角，所有題目與跳題解鎖邏輯皆已啟用可供點擊測試！在此模式下填答數據免送出入庫。',
        mode: 'noticeMode',
        confirmText: '了解',
        showCloseButton: true,
      };
      this._dialogService.showDialog(previewNotice).subscribe();
      return;
    }

    // 送出前強制執行 DOM 實體值防呆全題型雙向同步
    this._syncDomValuesToForm();

    this.runnerFormGroup.markAllAsTouched();
    const errors: string[] = [];

    // 1. 基本資料核驗
    for (const meta of this.getBasicInfoFieldMetaList()) {
      const control = this.runnerFormGroup.get(meta.key);
      if (control && control.invalid) {
        errors.push(`• 基本資料：${meta.label} 尚未填寫或格式不符`);
      }
    }

    // 2. 題目完整性核驗（跳題隱藏者絕對豁免檢驗）
    for (let questionIndex = 0; questionIndex < this.survey.questions.length; questionIndex++) {
      const question = this.survey.questions[questionIndex];
      const isVisible = this.isQuestionVisible(question);

      // 若題目被條件跳題隱藏，絕對不作必填檢核
      if (!isVisible) {
        continue;
      }

      if (question.isRequired) {
        const control = this.runnerFormGroup.get(question.uid);
        let hasAnswer = false;

        if (control) {
          const val = control.value;
          if (question.type === 'multiRadio') {
            hasAnswer = Array.isArray(val) && val.length > 0;
          } else if (val !== null && val !== undefined && String(val).trim().length > 0) {
            hasAnswer = true;
          }
        }

        // 若表單控制項無值，二次檢驗 DOM 原生實體是否存在選中或輸入
        if (!hasAnswer) {
          switch (question.type) {
            case 'radio': {
              const el = document.querySelector<HTMLInputElement>(`input[name="radio_${question.uid}"]:checked`);
              if (el && el.value) {
                hasAnswer = true;
                control?.setValue(el.value);
              }
              break;
            }
            case 't/f': {
              const el = document.querySelector<HTMLInputElement>(`input[name="tf_radio_${question.uid}"]:checked`);
              if (el && el.value) {
                hasAnswer = true;
                control?.setValue(el.value);
              }
              break;
            }
            case 'scale': {
              const el = document.querySelector<HTMLInputElement>(`input[name="scale_${question.uid}"]:checked`);
              if (el && el.value) {
                hasAnswer = true;
                control?.setValue(el.value);
              }
              break;
            }
            case 'date':
            case 'time': {
              const el = document.querySelector<HTMLInputElement>(`input[formControlName="${question.uid}"]`);
              if (el && el.value) {
                hasAnswer = true;
                control?.setValue(el.value);
              }
              break;
            }
            case 'text': {
              const el = document.querySelector<HTMLTextAreaElement>(`textarea[formControlName="${question.uid}"]`);
              if (el && el.value.trim().length > 0) {
                hasAnswer = true;
                control?.setValue(el.value.trim());
              }
              break;
            }
            default:
              break;
          }
        }

        if (!hasAnswer) {
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
        const val = raw[question.uid] ?? null;
        let selectedOptionId: number | null = null;
        let selectedOptionIds: number[] | null = null;

        switch (question.type) {
          case 't/f': {
            // 是非題：若選 true 匹配「是」或第 1 個選項；若選 false 匹配「否」或第 2 個選項
            const opts = question.optionsConfig || [];
            if ((val === 'true' || val === true) && opts.length > 0) {
              selectedOptionId = opts[0].id ?? null;
            } else if ((val === 'false' || val === false) && opts.length > 1) {
              selectedOptionId = opts[1].id ?? null;
            }
            break;
          }
          case 'radio': {
            // 單選題：比對選項文字獲取 optionId
            const matchedOpt = (question.optionsConfig || []).find((o) => o.text === String(val));
            if (matchedOpt && matchedOpt.id) {
              selectedOptionId = matchedOpt.id;
            }
            break;
          }
          case 'multiRadio': {
            // 複選題：比對多個勾選項目的文字獲取對應 optionId 清單
            if (Array.isArray(val)) {
              selectedOptionIds = (question.optionsConfig || [])
                .filter((o) => val.includes(o.text) && o.id)
                .map((o) => o.id!);
            }
            break;
          }
          default:
            break;
        }

        answers.push({
          questionUid: question.uid,
          questionId: question.id,
          type: question.type,
          answer: val,
          selectedOptionId,
          selectedOptionIds,
        });
      }
    }

    const rawId = (this.survey as any).id || (this.survey as any).uid || 1;
    const payload: SurveySubmissionPayload = {
      surveyId: rawId,
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
