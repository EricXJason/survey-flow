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
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
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
  SurveyBasicInfoRequirements,
} from '../../../@interfaces/survey-model';

/**
 * 受訪者基本資料欄位定義 (表單控制項、檢核錯誤訊息、送出內容共用這一份，避免欄位與文字分散在多處)
 *   key            表單控制項名稱，也是送出時 basicInfo 的欄位名稱
 *   requirement    問卷設定中對應的「是否收集」開關
 *   label          錯誤訊息中顯示的欄位名稱
 *   initialValue   初始值 (數字欄位為 null，其餘為空字串)
 *   formatValidators 除了必填以外的格式驗證
 */
interface BasicInfoFieldDefinition {
  key: string;
  requirement: keyof SurveyBasicInfoRequirements;
  label: string;
  initialValue: string | null;
  formatValidators: (validation: ValidationService) => ValidatorFn[];
}

/** 不需要額外格式驗證的欄位 */
const NO_FORMAT_VALIDATORS = (): ValidatorFn[] => [];

/** 基本資料欄位 (順序即畫面與錯誤訊息的順序) */
const BASIC_INFO_FIELDS: BasicInfoFieldDefinition[] = [
  { key: 'name', requirement: 'requireName', label: '姓名（須為 2 至 6 字中文）', initialValue: '', formatValidators: (v) => [v.chineseNameValidator(2, 6)] },
  { key: 'gender', requirement: 'requireGender', label: '性別', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'age', requirement: 'requireAge', label: '年紀', initialValue: null, formatValidators: () => [Validators.min(0)] },
  { key: 'birthday', requirement: 'requireBirthday', label: '生日', initialValue: '', formatValidators: (v) => [v.fourDigitYearDateValidator(1900, 2099)] },
  { key: 'height', requirement: 'requireHeight', label: '身高', initialValue: null, formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'weight', requirement: 'requireWeight', label: '體重', initialValue: null, formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'mobilePhone', requirement: 'requireMobilePhone', label: '手機電話（09 開頭）', initialValue: '', formatValidators: (v) => [v.taiwanPhoneValidator()] },
  { key: 'homePhone', requirement: 'requireHomePhone', label: '住家電話', initialValue: '', formatValidators: (v) => [v.taiwanTelValidator()] },
  { key: 'email', requirement: 'requireEmail', label: '電子信箱', initialValue: '', formatValidators: () => [Validators.email] },
  { key: 'registeredAddress', requirement: 'requireRegisteredAddress', label: '戶籍地址', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'residentialAddress', requirement: 'requireResidentialAddress', label: '住家地址', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'department', requirement: 'requireDepartment', label: '所屬單位', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'academicMajor', requirement: 'requireAcademicMajor', label: '科系', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'className', requirement: 'requireClassName', label: '班級名稱', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'studentId', requirement: 'requireStudentId', label: '學號', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'employeeId', requirement: 'requireEmployeeId', label: '員工編號', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'nationalId', requirement: 'requireNationalId', label: '身分證字號', initialValue: '', formatValidators: (v) => [v.taiwanNationalIdValidator()] },
  { key: 'jobIndustry', requirement: 'requireJobIndustry', label: '職業領域', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
  { key: 'jobTitle', requirement: 'requireJobTitle', label: '職業', initialValue: '', formatValidators: NO_FORMAT_VALIDATORS },
];

/**
 * 題目文字中的分數範圍提示 → 量表選項 (舊問卷沒有 minRating / maxRating 時，從題目文字推斷)
 * 依序比對，第一個符合者生效
 */
const SCALE_RANGE_HINTS: { keywords: string[]; min: number; max: number }[] = [
  { keywords: ['0-10', '0~10', '0至10', 'NPS', '淨推薦值'], min: 0, max: 10 },
  { keywords: ['1-10', '1~10', '1至10'], min: 1, max: 10 },
  { keywords: ['1-5', '1~5', '1至5', '1最低，5最高', '1最低、5最高'], min: 1, max: 5 },
  { keywords: ['1-7', '1~7', '1至7'], min: 1, max: 7 },
];

/** 文字量表代碼 → 選項文字 (數字量表在程式中依範圍產生) */
const TEXT_SCALE_OPTIONS: Record<string, string[]> = {
  'scale-text-agreement-5': ['非常不同意', '不同意', '普通', '同意', '非常同意'],
  'scale-text-importance-5': ['非常不重要', '不重要', '普通', '重要', '非常重要'],
  'scale-text-preference-5': ['非常不喜歡', '不喜歡', '普通', '喜歡', '非常喜歡'],
  'scale-text-frequency-5': ['從不', '很少', '有時', '經常', '總是'],
  'scale-text-satisfaction-4': ['非常不滿意', '不滿意', '滿意', '非常滿意'],
  'scale-text-satisfaction-5': ['非常不滿意', '不滿意', '普通', '滿意', '非常滿意'],
};

/** 數字量表代碼 → 分數範圍 */
const NUMERIC_SCALE_RANGES: Record<string, [number, number]> = {
  'scale-num-1-5': [1, 5],
  'scale-num-1-10': [1, 10],
  'scale-num-0-10': [0, 10],
  nps: [0, 10],
};

/**
 * 產生 min 到 max 的分數字串清單
 * @param min 最小分數
 * @param max 最大分數
 * @returns 例如 ['1','2','3','4','5']
 */
function scoreRange(min: number, max: number): string[] {
  const scores: string[] = [];
  for (let score = min; score <= max; score++) {
    scores.push(String(score));
  }
  return scores;
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
  /** 生日最大允許日期（嚴格限制不能選今天與未來，最大只能選到昨天） */
  protected readonly maxBirthday = this._validationService.getLocalDate(-1);
  /** 問卷填答是否已成功提交送出之狀態旗標 */
  private _isSubmitted: boolean = false;

  /** 問卷是否處於未開放或已截止狀態信號 */
  public readonly isUnavailable = signal<boolean>(false);

  /**
   * 是否正在向後端載入問卷 (?share= 或 ?surveyId= 進入時)
   * [Why]: 載入期間表單尚未建立題目欄位，若先渲染填答內容，formControlName 會找不到控制項而拋錯；載入完成並重建表單後才渲染
   */
  public readonly isLoadingSurvey = signal<boolean>(false);
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

  /** 單選題當前選取的選項索引映射表 (questionUid -> optionIndex)，精準杜絕同名選項解鎖與選取污染 */
  public readonly selectedRadioIndexMap = signal<Record<string, number>>({});

  /** 複選題當前選取的選項索引清單映射表 (questionUid -> optionIndex[])，精準杜絕同名選項時「選一個選到全部」之污染 */
  public readonly selectedMultiRadioIndexMap = signal<Record<string, number[]>>({});

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

      if (startDate) {
        const sTime = startTime || '00:00';
        const startDt = new Date(`${startDate}T${sTime}`);
        if (!isNaN(startDt.getTime()) && now < startDt) {
          const sDisplay = `${startDate.replace(/-/g, '/')} ${sTime}`;
          const eDisplay = endDate ? `${endDate.replace(/-/g, '/')} ${endTime || '23:59'}` : '';
          this.isUnavailable.set(true);
          this.unavailableTitle.set('問卷尚未開始開放填答');
          this.unavailableMessage.set(`本問卷預計於 ${sDisplay} 開放填寫，目前尚未到達作答時間，請於開放後再次前往填答。`);
          this.unavailableTimeRange.set(eDisplay ? `填答有效期間：${sDisplay} 至 ${eDisplay}` : `填答開放時間：${sDisplay} 起`);
          return;
        }
      }

      if (endDate) {
        const eTime = endTime || '23:59';
        const endDt = new Date(`${endDate}T${eTime}`);
        if (!isNaN(endDt.getTime()) && now > endDt) {
          const sDisplay = startDate ? `${startDate.replace(/-/g, '/')} ${startTime || '00:00'}` : '';
          const eDisplay = `${endDate.replace(/-/g, '/')} ${eTime}`;
          this.isUnavailable.set(true);
          this.unavailableTitle.set('問卷填答已截止');
          this.unavailableMessage.set(`本問卷已於 ${eDisplay} 截止填答，已無法再進行填寫，非常感謝您的關注與支持！`);
          this.unavailableTimeRange.set(sDisplay ? `填答有效期間：${sDisplay} 至 ${eDisplay}` : `填答截止時間：至 ${eDisplay}`);
          return;
        }
      }
    }

    this.isUnavailable.set(false);
  }

  /**
   * Angular 生命週期鉤子 (ngOnInit)
   * 依網址參數決定載入來源：?share=<分享代碼> (專屬填答連結，公開與未公開問卷皆可) 優先；
   * 否則 ?surveyId=... 或 ?id=... (大廳進入或建立者預覽，未公開於大廳之問卷會被後端擋下)；
   * 都沒有時沿用設計頁目前編輯中之問卷。預覽模式下自動禁用全部表單控制項。
   */
  public ngOnInit(): void {
    const shareToken = this.shareTokenFromUrl;
    const queryId =
      this._route.snapshot.queryParams['surveyId'] ||
      this._route.snapshot.queryParams['id'];
    const fromParam = this._route.snapshot.queryParams['from'];
    const isPreviewQuery = this._route.snapshot.queryParams['preview'] === 'true' || fromParam === 'survey-management';

    if (shareToken || queryId) {
      const loadSurvey$ = shareToken
        ? this._surveyGetter.getSurveyByShareToken(shareToken)
        : this._surveyGetter.getSurveyById(String(queryId), isPreviewQuery);
      this.isLoadingSurvey.set(true);
      loadSurvey$.subscribe({
        next: (loadedSurvey) => {
          if (loadedSurvey) {
            if (isPreviewQuery && !shareToken) {
              this.surveyStateService.setForPreviewMode(loadedSurvey);
            } else {
              this.surveyStateService.setForRunMode(loadedSurvey);
            }
          }
          this._checkSurveyAvailability(loadedSurvey);
          this.runnerFormGroup = this.formBuilderGroupInit();
          this.isLoadingSurvey.set(false);
        },
        error: (err: Error) => {
          // 錯誤訊息已由 SurveyGetterService 轉為繁中 (例如斷線、問卷已截止、尚未開始、連結無效)
          this._checkSurveyAvailability(null, err?.message || '此問卷目前暫不開放填答。');
          this.runnerFormGroup = this.formBuilderGroupInit();
          this.isLoadingSurvey.set(false);
        },
      });
      return;
    }

    this._checkSurveyAvailability(this.survey);
    this.runnerFormGroup = this.formBuilderGroupInit();
  }

  /**
   * 專屬填答連結中的分享代碼 (?share=)；送出填答時需原樣帶回，後端據此放行未公開於大廳之問卷
   * @returns 分享代碼；由大廳進入或預覽時為 undefined
   */
  private get shareTokenFromUrl(): string | undefined {
    return this._route.snapshot.queryParams['share'] || undefined;
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
   * 判斷當前是否處於「預覽審查」模式
   * 包含設計頁面即時預覽、問卷管理後臺點選預覽審查（from=survey-management）或帶有 preview=true 參數之模式
   */
  public get isPreview(): boolean {
    const fromParam = this._route.snapshot.queryParams['from'];
    const previewParam = this._route.snapshot.queryParams['preview'];
    return this.surveyStateService.isPreviewMode() || fromParam === 'survey-management' || previewParam === 'true';
  }

  /**
   * 是否顯示頂部快速返回導航按鈕
   * 規則：當處於獨立路由（由問卷管理跳轉預覽、問卷大廳進入，或網址有 from/surveyId/share 參數時）顯示；
   * 若純粹作為 <app-survey-runner /> 嵌入於設計頁面預覽容器中，則隱藏以防按鈕重複。
   */
  public get shouldShowBackButton(): boolean {
    const from = this._route.snapshot.queryParams['from'];
    const queryId = this._route.snapshot.queryParams['surveyId'] || this._route.snapshot.queryParams['id'] || this.shareTokenFromUrl;
    const isSurveyRunnerRoute = this._route.snapshot.routeConfig?.path === 'survey-runner';
    return Boolean(from || queryId || isSurveyRunnerRoute);
  }

  /**
   * 初始化填答表單群組
   * 包含受訪者基本資料控制項以及每道題目的 FormArray 或 FormControl。
   * @returns FormGroup 填答表單物件
   */
  private formBuilderGroupInit(): FormGroup {
    this.selectedRadioIndexMap.set({});
    this.selectedMultiRadioIndexMap.set({});
    const group = this._formBuilder.group({});

    for (const field of this.getActiveBasicInfoFields()) {
      const validators = [Validators.required, ...field.formatValidators(this._validationService)];
      group.addControl(field.key, this._formBuilder.control(field.initialValue, validators));
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
   * 依問卷的基本資料設定，挑出要收集的欄位 (問卷未開啟收集時為空清單)
   * @returns 要顯示的基本資料欄位定義
   */
  private getActiveBasicInfoFields(): BasicInfoFieldDefinition[] {
    const requirements = this.survey.basicInfoRequirements;
    if (!requirements?.collectBasicInfo) {
      return [];
    }
    return BASIC_INFO_FIELDS.filter((field) => requirements[field.requirement]);
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
      this._cdr.markForCheck();
    }
  }

  /**
   * 處理欄位即時輸入與選取 (Input / Change) 事件
   * 當受訪者鍵入文字、中文選字完成或切換下拉選項時，即時同步值並重新計算驗證狀態，確保合格時紅字紅框即刻消退
   * @param fieldKey 控制項名稱
   * @param event 原生 DOM 事件
   */
  public onFieldInput(fieldKey: string, event: Event): void {
    const target = event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
    if (!target) return;

    const ctrl = this.runnerFormGroup.get(fieldKey);
    if (!ctrl) return;

    let val: unknown = target.value;
    if (typeof val === 'string') {
      val = val.trim();
    }

    if (fieldKey === 'birthday' && typeof val === 'string' && val) {
      const match = val.match(/^(\d{1,6})([-/\d]*)$/);
      if (match && match[1].length > 4) {
        val = match[1].substring(0, 4) + match[2];
        target.value = val as string;
      }
    }

    if (fieldKey === 'age' || fieldKey === 'height' || fieldKey === 'weight') {
      const parsed = parseFloat(String(val));
      val = !isNaN(parsed) && parsed > 0 ? parsed : null;
    }

    ctrl.setValue(val);
    ctrl.markAsDirty();
    ctrl.updateValueAndValidity();
    this._cdr.markForCheck();
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
   * 優先依據選項索引 (optIdx) 進行精確判定，避免出現同名選項時「選一個卻選到全部」
   * @param questionUid 複選題 UID
   * @param optionValue 選項文字值
   * @param optIdx 選項所在之陣列索引
   * @returns 若已勾選則為 true，否則為 false
   */
  protected isMultiRadioChecked(
    questionUid: string,
    optionValue: string,
    optIdx: number,
  ): boolean {
    const indices = this.selectedMultiRadioIndexMap()[questionUid];
    if (indices !== undefined) {
      return indices.includes(optIdx);
    }
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
   * 採用唯一索引 (optIdx) 維護選取狀態集合，並同步重構 FormArray，確保同名選項獨立運作
   * @param questionUid 複選題 UID
   * @param optionValue 選項值
   * @param optIdx 選項所在之陣列索引
   * @param event 勾選框變更事件
   */
  protected onMultiRadioChange(
    questionUid: string,
    optionValue: string,
    optIdx: number,
    event: Event,
  ): void {
    if (this.isPreview) return;

    const checkbox = event.target as HTMLInputElement;
    const currentIndices = this.selectedMultiRadioIndexMap()[questionUid] || [];
    let newIndices: number[];

    if (checkbox.checked) {
      if (!currentIndices.includes(optIdx)) {
        newIndices = [...currentIndices, optIdx].sort((a, b) => a - b);
      } else {
        newIndices = currentIndices;
      }
    } else {
      newIndices = currentIndices.filter((i) => i !== optIdx);
    }

    this.selectedMultiRadioIndexMap.update((map) => ({
      ...map,
      [questionUid]: newIndices,
    }));

    const array = this.getMultiRadioArray(questionUid);
    if (array) {
      array.clear();
      const question = this.survey.questions.find((item) => item.uid === questionUid);
      if (question && question.options) {
        for (const idx of newIndices) {
          if (idx >= 0 && idx < question.options.length) {
            array.push(this._formBuilder.control(question.options[idx]));
          }
        }
      } else {
        newIndices.forEach(() => array.push(this._formBuilder.control(optionValue)));
      }
      array.markAsDirty();
      array.markAsTouched();
      array.updateValueAndValidity();
    }

    this._cdr.markForCheck();
  }

  /**
   * 判斷題目當前是否應該顯示（動態條件跳題邏輯計算核心）
   * 若題目未受任何跳題規則鎖定，則預設常態顯示；
   * 若受到上游單選題鎖定，則必須在使用者勾選對應之解鎖選項時，才回傳 true 展開顯示。
   * @param question 目標題目資料模型
   * @returns 該題目是否處於可見狀態
   */
  /**
   * 取得鎖定此題目的上游來源題目與選項清單文字
   * 若題目模型中已具備 lockedBySummary 則優先使用；若無則自動自問卷題目資料中即時反向關聯推算
   * @param question 目標題目資料模型
   * @returns 格式化之解鎖來源敘述（例如：「【問題 01】選項 02（滿意）」）
   */
  /**
   * 檢查解鎖目標 Key 是否匹配指定題目 (支援 UID、資料庫 ID 與 questionCode 相容比對)
   */
  private isTargetQuestionMatch(targetKey: string | null | undefined, question: SurveyQuestion): boolean {
    if (!targetKey) return false;
    const cleanKey = String(targetKey).trim();
    const targetUid = String(question.uid || '').trim();
    const targetId = String(question.id ?? '').trim();
    const targetCode = String((question as any).questionCode ?? '').trim();
    return cleanKey === targetUid || (!!targetId && cleanKey === targetId) || (!!targetCode && cleanKey === targetCode);
  }

  /**
   * 取得鎖定此題目的上游來源題目與選項清單文字
   * 若題目模型中已具備 lockedBySummary 則優先使用；若無則自動自問卷題目資料中即時反向關聯推算
   * @param question 目標題目資料模型
   * @returns 格式化之解鎖來源敘述（例如：「【問題 01】選項 02（滿意）」）
   */
  public getUnlockSourcesSummary(question: SurveyQuestion): string {
    if (question.lockedBySummary) {
      return question.lockedBySummary;
    }
    const sources: string[] = [];
    const questions = this.survey?.questions || [];

    questions.forEach((pq, pIdx) => {
      if (pq.optionsConfig && pq.optionsConfig.length > 0) {
        pq.optionsConfig.forEach((opt, oIdx) => {
          if (opt.enableUnlockQuestion && this.isTargetQuestionMatch(opt.unlockTargetUid, question)) {
            const qNum = (pIdx + 1).toString().padStart(2, '0');
            const optNum = (oIdx + 1).toString().padStart(2, '0');
            const optText = opt.text ? `（${opt.text}）` : '';
            sources.push(`【問題 ${qNum}】選項 ${optNum}${optText}`);
          }
        });
      }
    });

    return sources.length > 0 ? sources.join(' 或 ') : '';
  }

  /**
   * 判斷題目當前是否已由填答選項觸發解鎖
   * @param question 目標題目
   * @returns 若題目未被跳題規則鎖定，或已被作答選項解鎖，回傳 true；若受鎖定且尚未觸發解鎖，回傳 false
   */
  public isQuestionUnlocked(question: SurveyQuestion): boolean {
    if (!question.isLockedByOther) return true;

    for (const parentQuestion of this.survey.questions) {
      if (!parentQuestion.optionsConfig || parentQuestion.optionsConfig.length === 0) continue;

      const selectedIdx = this.selectedRadioIndexMap()[parentQuestion.uid];
      const val = this.runnerFormGroup.get(parentQuestion.uid)?.value;
      if (!val && selectedIdx === undefined) continue;

      // 若為單選題且有明確的選項點擊索引，精準比對該選項之解鎖設定
      if (parentQuestion.type === 'radio' && selectedIdx !== undefined && selectedIdx >= 0 && selectedIdx < parentQuestion.optionsConfig.length) {
        const selectedOption = parentQuestion.optionsConfig[selectedIdx];
        if (selectedOption.enableUnlockQuestion && this.isTargetQuestionMatch(selectedOption.unlockTargetUid, question)) {
          return true;
        }
        continue;
      }

      // 若為複選題且有明確勾選索引清單，精準依據勾選項目的解鎖規則觸發
      if (parentQuestion.type === 'multiRadio') {
        const multiIndices = this.selectedMultiRadioIndexMap()[parentQuestion.uid] || [];
        for (const idx of multiIndices) {
          if (idx >= 0 && idx < parentQuestion.optionsConfig.length) {
            const opt = parentQuestion.optionsConfig[idx];
            if (opt.enableUnlockQuestion && this.isTargetQuestionMatch(opt.unlockTargetUid, question)) {
              return true;
            }
          }
        }
        continue;
      }

      // 後備匹配 (是非題、複選題或非透過索引選取之單選題)
      for (const option of parentQuestion.optionsConfig) {
        if (!option.enableUnlockQuestion || !this.isTargetQuestionMatch(option.unlockTargetUid, question)) {
          continue;
        }

        if (String(val).trim() === String(option.text).trim()) {
          return true;
        }
        if (parentQuestion.type === 't/f') {
          if ((val === 'true' || val === true || val === '是') && option.sortOrder === 1) return true;
          if ((val === 'false' || val === false || val === '否') && option.sortOrder === 2) return true;
        }
        if (Array.isArray(val) && val.some((v) => String(v).trim() === String(option.text).trim())) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * 判斷題目當前是否應該顯示
   * 1. 若處於預覽審查模式，所有題目強制可見，便於作者檢閱問卷結構、題型外觀與跳題關聯；
   * 2. 若處於正式受訪者填答模式，依作答解鎖狀態動態切換顯示與隱藏。
   * @param question 目標題目資料模型
   * @returns 該題目是否處於可見狀態
   */
  protected isQuestionVisible(question: SurveyQuestion): boolean {
    if (this.isPreview) {
      return true;
    }
    return this.isQuestionUnlocked(question);
  }

  /**
   * 根據量表設定類型 (scaleType) 或題目 minRating/maxRating 解析並回傳對應的量表選項標籤清單
   * @param question 題目物件或 scaleType 字串
   * @returns 依序排列的量表級距標籤字串陣列
   */
  protected getScaleOptions(question: any): string[] {
    const defaultOptions = TEXT_SCALE_OPTIONS['scale-text-satisfaction-5'];
    if (!question) {
      return defaultOptions;
    }
    const scaleType: string = typeof question === 'string' ? question : question.scaleType;
    const minRating = typeof question === 'object' ? question.minRating : undefined;
    const maxRating = typeof question === 'object' ? question.maxRating : undefined;
    const desc: string = typeof question === 'object' ? (question.description || '') : '';

    // 1. 題目有明確的評分範圍時直接使用
    if (minRating != null && maxRating != null && minRating <= maxRating) {
      return scoreRange(minRating, maxRating);
    }

    // 2. 從題目文字中的範圍提示推斷 (舊問卷沒有 minRating / maxRating)
    const hint = SCALE_RANGE_HINTS.find((item) => item.keywords.some((keyword) => desc.includes(keyword)));
    if (hint) {
      return scoreRange(hint.min, hint.max);
    }

    // 3. 依量表代碼決定；未知代碼使用 5 級滿意度
    const numericRange = NUMERIC_SCALE_RANGES[scaleType];
    if (numericRange) {
      return scoreRange(numericRange[0], numericRange[1]);
    }
    return TEXT_SCALE_OPTIONS[scaleType] ?? defaultOptions;
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
   * @param optIdx 選項索引 (0-based)
   */
  protected selectRadioOption(questionUid: string, opt: string, optIdx?: number): void {
    if (optIdx !== undefined) {
      this.selectedRadioIndexMap.update((map) => ({
        ...map,
        [questionUid]: optIdx,
      }));
    }
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
   * 判斷單選題指定索引之選項是否已被選取
   * 優先依據選項索引 (optIdx) 進行精確比對，杜絕同文字選項同時被選中的混淆
   * @param questionUid 題目 UID
   * @param opt 選項文字
   * @param optIdx 選項索引
   */
  protected isRadioOptionSelected(questionUid: string, opt: string, optIdx: number): boolean {
    const selectedIdx = this.selectedRadioIndexMap()[questionUid];
    if (selectedIdx !== undefined) {
      return selectedIdx === optIdx;
    }
    return this.runnerFormGroup.get(questionUid)?.value === opt;
  }

  /**
   * 監聽日期或時間欄位變更，並即時同步至對應之 FormControl
   * @param questionUid 題目唯一識別碼
   * @param event 原生 DOM 事件
   */
  protected onDateTimeChange(questionUid: string, event: Event): void {
    if (this.isPreview) return;
    const input = event.target as HTMLInputElement;
    let val = input.value;
    if (val && typeof val === 'string') {
      const match = val.match(/^(\d{1,6})([-/\d]*)$/);
      if (match && match[1].length > 4) {
        val = match[1].substring(0, 4) + match[2];
        input.value = val;
      }
    }
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
      this._cdr.markForCheck();
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
      this._cdr.markForCheck();
    }
  }

  /**
   * 送出前把畫面上的實際值補回表單：基本資料欄位 (含年齡轉數字、性別依選項文字判斷、產業「其他」自填)，
   * 以及選擇類題目 (單選、是非、量表、複選) 的勾選狀態
   * [Why]: 瀏覽器自動填入 (Autofill) 或點擊標籤時，畫面已有值但 Angular 表單可能沒收到事件，
   *        若不補回，受訪者明明填了卻會被判定未填
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

    // 2. 掃描選擇類題目在 DOM 中的選取狀態 (radio、t/f、scale、multiRadio)
    // [Why]: 文字、日期、時間題由 Angular 表單在 input / change 事件即時同步，不需要從 DOM 補回
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
          // [Why]: 以 name 前綴 multi_<題目 uid>_ 定位，確保只讀取本題的勾選框 (同頁可能有多題複選)
          const matchedValues: string[] = [];
          formElement.querySelectorAll<HTMLInputElement>(`input[type="checkbox"][name^="multi_${q.uid}_"]:checked`).forEach((cb) => {
            if (cb.value && (q.options || []).includes(cb.value)) {
              matchedValues.push(cb.value);
            }
          });
          if (matchedValues.length > 0 && multiArray.length === 0) {
            matchedValues.forEach((v) => multiArray.push(this._formBuilder.control(v)));
            multiArray.markAsDirty();
            multiArray.updateValueAndValidity();
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
    for (const field of this.getActiveBasicInfoFields()) {
      if (this.runnerFormGroup.get(field.key)?.invalid) {
        errors.push(`• 基本資料：${field.label} 尚未填寫或格式不符`);
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
        const val = this.runnerFormGroup.get(question.uid)?.value;
        const hasAnswer = question.type === 'multiRadio'
          ? Array.isArray(val) && val.length > 0
          : val !== null && val !== undefined && String(val).trim().length > 0;
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

    for (const field of this.getActiveBasicInfoFields()) {
      basicInfo[field.key] = raw[field.key];
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
            // 是非題：依文字語意智慧匹配選項 ID，拒絕硬編碼索引順序
            const opts = question.optionsConfig || [];
            const isTrue = (val === 'true' || val === true || val === '是' || val === 'yes');
            const isFalse = (val === 'false' || val === false || val === '否' || val === 'no');

            const yesKeywords = ['是', 'true', 'yes', '同意', '對'];
            const noKeywords = ['否', 'false', 'no', '不同意', '不對'];

            const yesOpt = opts.find((o) => yesKeywords.some((k) => (o.text || '').trim().toLowerCase() === k)) || opts[0];
            const noOpt = opts.find((o) => noKeywords.some((k) => (o.text || '').trim().toLowerCase() === k)) || (opts.length > 1 ? opts[1] : null);

            if (isTrue && yesOpt && yesOpt.id) {
              selectedOptionId = yesOpt.id;
            } else if (isFalse && noOpt && noOpt.id) {
              selectedOptionId = noOpt.id;
            }
            break;
          }
          case 'radio': {
            // 單選題：優先依據選項選取索引精準提取 optionId，避免同名選項 ID 混淆
            const cleanVal = String(val ?? '').trim();
            const selectedIdx = this.selectedRadioIndexMap()[question.uid];
            const opts = question.optionsConfig || [];

            if (selectedIdx !== undefined && opts[selectedIdx] && opts[selectedIdx].id) {
              selectedOptionId = opts[selectedIdx].id;
            } else {
              const matchedOpt = opts.find((o) => (o.text || '').trim() === cleanVal);
              if (matchedOpt && matchedOpt.id) {
                selectedOptionId = matchedOpt.id;
              }
            }
            break;
          }
          case 'multiRadio': {
            // 複選題：優先依據選項勾選索引陣列精準提取對應 optionId 清單，避免同名選項的 ID 混淆
            const multiIndices = this.selectedMultiRadioIndexMap()[question.uid] || [];
            const opts = question.optionsConfig || [];
            if (multiIndices.length > 0 && opts.length > 0) {
              selectedOptionIds = multiIndices
                .map((idx) => opts[idx]?.id)
                .filter((id): id is number => id !== undefined && id !== null);
            } else if (Array.isArray(val)) {
              const cleanVals = val.map((v) => String(v).trim());
              selectedOptionIds = opts
                .filter((o) => cleanVals.includes((o.text || '').trim()) && o.id)
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
      shareToken: this.shareTokenFromUrl,
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
