/**
 * ============================================================================
 * 檔案名稱: SurveyService.java
 * 所屬模組: Service 業務邏輯層 - 問卷核心模組
 * 責任描述: 負責問卷專案之建立、修改、軟刪除、樹狀結構組裝與大廳公開檢索
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRepository, UserRepository, SubmissionRepository
 *   - 下游消費: SurveyController
 * 邊界處理: 嚴格核對問卷發布者權限，非建立者或管理員嚴禁修改或刪除；
 *           併發控制：修改類方法先比對前端版本號再以 saveAndFlush 寫入 (樂觀鎖)，瀏覽數以原子 UPDATE 遞增
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.dto.survey.*;
import org.jasonproduction.surveyflow.entity.*;
import org.jasonproduction.surveyflow.repository.SubmissionRepository;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 未指定分類時之預設問卷分類 */
    private static final String DEFAULT_CATEGORY = "綜合調查";
    /** 是非題未自訂選項時自動補上之選項文字 */
    private static final String BOOLEAN_OPTION_YES = "是";
    private static final String BOOLEAN_OPTION_NO = "否";

    /**
     * 建立新問卷 (支援草稿或直接發布)
     * @param request 建立參數
     * @param currentUsername 當前登入會員帳號
     * @return 建立完成後之問卷詳情
     */
    @Transactional(rollbackFor = Exception.class)
    public SurveyResponse createSurvey(SurveyCreateRequest request, String currentUsername) {
        User creator = userRepository.findByUsernameNative(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無此建立者帳號"));
        validateDateRange(request.getStartDate(), request.getEndDate());

        Survey survey = Survey.builder()
                .creator(creator)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .category(request.getCategory() != null ? request.getCategory() : DEFAULT_CATEGORY)
                .isOpen(request.getIsOpen() != null ? request.getIsOpen() : true)
                .isPublicToLobby(request.getIsPublicToLobby() != null ? request.getIsPublicToLobby() : true)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .viewCount(0)
                .build();

        if (request.getBasicInfoConfig() != null) {
            survey.assignBasicInfoConfig(toBasicInfoConfigEntity(request.getBasicInfoConfig()));
        }
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            List<SurveyQuestion> questions = new ArrayList<>();
            for (QuestionDto questionDto : request.getQuestions()) {
                questions.add(toQuestionEntity(survey, questionDto));
            }
            survey.replaceQuestions(questions);
        }

        survey = surveyRepository.save(survey);
        log.info("問卷專案建立成功: id={}, title={}, creator={}", survey.getId(), survey.getTitle(), currentUsername);
        publishSurveyEvent(AuditAction.SURVEY_CREATED, currentUsername, survey);
        return convertToDetailResponse(survey, true);
    }

    /**
     * 查詢單一問卷完整結構 (僅限建立者或管理員)
     * @param surveyId 問卷 ID
     * @param currentUsername 當前操作者帳號
     * @return 完整結構 DTO
     * @throws AccessDeniedException 非建立者亦非管理員
     */
    @Transactional(readOnly = true)
    public SurveyResponse getSurveyDetail(Long surveyId, String currentUsername) {
        return convertToDetailResponse(findSurveyByIdAndVerifyOwner(surveyId, currentUsername), true);
    }

    /**
     * 受訪者由大廳以問卷 ID 進入填答，或管理者預覽時獲取問卷
     * @param surveyId 問卷 ID
     * @param isPreview 是否為預覽模式 (不累加瀏覽次數、不檢核時窗；僅限建立者或管理員)
     * @param currentUsername 當前登入帳號，匿名時為 null
     * @return 完整填答結構 (受訪者模式不含分享代碼)
     * @throws AccessDeniedException 預覽者未登入或非建立者 / 管理員
     * @throws IllegalArgumentException 問卷不存在、未公開於大廳 (須改用分享連結)、未開放或不在時窗內
     */
    @Transactional(rollbackFor = Exception.class)
    public SurveyResponse getPublicSurvey(Long surveyId, boolean isPreview, String currentUsername) {
        if (isPreview) {
            // [Why]: 預覽會略過開放狀態與時窗檢核，僅限建立者或管理員，避免任何人以 ?preview=true 窺看未開放問卷
            if (currentUsername == null) {
                throw new AccessDeniedException("預覽問卷需以建立者或管理員身分登入");
            }
            return convertToDetailResponse(findSurveyByIdAndVerifyOwner(surveyId, currentUsername), true);
        }

        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("問卷不存在或已下架"));
        // [Why]: 以 ID 進入不帶分享代碼，未公開於大廳之問卷在此被擋下，杜絕改網址數字窺看私人問卷
        survey.ensureReachableBy(null);
        return openForRespondent(survey);
    }

    /**
     * 受訪者由專屬填答連結 (?share=代碼) 進入時獲取問卷；公開與未公開於大廳之問卷皆可
     * @param shareToken 連結中的分享代碼
     * @return 完整填答結構 (不含分享代碼)
     * @throws IllegalArgumentException 代碼不存在、問卷已刪除、未開放或不在時窗內
     */
    @Transactional(rollbackFor = Exception.class)
    public SurveyResponse getSharedSurvey(String shareToken) {
        Survey survey = surveyRepository.findByShareTokenAndDeletedAtIsNullNative(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("問卷不存在或已下架"));
        return openForRespondent(survey);
    }

    /**
     * 問卷大廳公開多條件搜尋
     * @param category 分類
     * @param keyword 關鍵字
     * @param pageable 分頁設定
     * @return 概要清單分頁結果
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> searchLobbySurveys(String category, String keyword, Pageable pageable) {
        Page<Survey> page = surveyRepository.searchPublishedSurveysNative(category, keyword, LocalDateTime.now(), pageable);
        return page.map(survey -> convertToSummaryResponse(survey, null, false));
    }

    /**
     * 查詢會員自己建立之問卷清單 (管理專區)
     * @param currentUsername 當前登入會員
     * @param pageable 分頁參數
     * @return 該會員之問卷分頁結果
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> getUserSurveys(String currentUsername, Pageable pageable) {
        User user = userRepository.findByUsernameNative(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無此使用者"));

        // [Why]: 依建立時間由舊至新為每份問卷編定該會員之個人問卷編號 (#1, #2, #3...)，使會員在個人專案中看到的是自己的問卷序號，而非系統全域 ID
        List<Long> userSurveyIds = surveyRepository.findSurveyIdsByUserIdOrderByCreatedAtAscNative(user.getId());
        Map<Long, Integer> surveyNumberById = new HashMap<>();
        for (int i = 0; i < userSurveyIds.size(); i++) {
            surveyNumberById.put(userSurveyIds.get(i), i + 1);
        }

        Page<Survey> page = surveyRepository.findByUserIdAndDeletedAtIsNullNative(user.getId(), pageable);
        return page.map(survey -> convertToSummaryResponse(survey, surveyNumberById.getOrDefault(survey.getId(), 1), true));
    }

    /**
     * 系統管理員查詢全站所有問卷清單 (包含公開與非公開/私人專案)
     * @param pageable 分頁參數
     * @return 全站所有問卷之概要分頁清單
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> getAllSurveysForAdmin(Pageable pageable) {
        Page<Survey> page = surveyRepository.findAllSurveysForAdminNative(pageable);
        return page.map(survey -> convertToSummaryResponse(survey, null, true));
    }

    /**
     * 更新問卷專案內容
     * @param surveyId 問卷 ID
     * @param request 更新請求
     * @param currentUsername 當前操作者帳號
     * @return 更新後之問卷詳情
     */
    @Transactional(rollbackFor = Exception.class)
    public SurveyResponse updateSurvey(Long surveyId, SurveyUpdateRequest request, String currentUsername) {
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);
        survey.ensureVersionMatches(request.getVersion());

        survey.updateContent(request.getTitle(), request.getDescription(), request.getCoverImageUrl(), request.getCategory());
        survey.patchSettings(request.getStartDate(), request.getEndDate(), request.getIsOpen(), request.getIsPublicToLobby());
        validateDateRange(survey.getStartDate(), survey.getEndDate());

        survey = surveyRepository.saveAndFlush(survey);
        log.info("問卷專案更新成功: id={}, version={}", survey.getId(), survey.getVersion());
        publishSurveyEvent(AuditAction.SURVEY_UPDATED, currentUsername, survey);
        return convertToDetailResponse(survey, true);
    }

    /**
     * 整體覆蓋問卷開放設定 (PUT)：起訖時間可傳 null 表示不限期
     * @param surveyId 問卷 ID
     * @param request 時限與狀態設定請求
     * @param currentUsername 當前操作者帳號
     * @return 更新後之問卷詳情
     */
    @Transactional(rollbackFor = Exception.class)
    public SurveyResponse replaceSurveySettings(Long surveyId, SurveySettingsRequest request, String currentUsername) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);
        survey.ensureVersionMatches(request.getVersion());
        survey.replaceSettings(request.getStartDate(), request.getEndDate(), request.getIsOpen(), request.getIsPublicToLobby());
        publishSurveyEvent(AuditAction.SURVEY_SETTINGS_CHANGED, currentUsername, survey);
        return saveSettings(survey);
    }

    /**
     * 局部修改問卷開放設定 (PATCH)：僅覆寫請求中非 null 之欄位，例如單獨切換大廳公開
     * @param surveyId 問卷 ID
     * @param request 時限與狀態設定請求 (可只帶部分欄位)
     * @param currentUsername 當前操作者帳號
     * @return 更新後之問卷詳情
     */
    @Transactional(rollbackFor = Exception.class)
    public SurveyResponse patchSurveySettings(Long surveyId, SurveySettingsRequest request, String currentUsername) {
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);
        survey.ensureVersionMatches(request.getVersion());
        survey.patchSettings(request.getStartDate(), request.getEndDate(), request.getIsOpen(), request.getIsPublicToLobby());
        validateDateRange(survey.getStartDate(), survey.getEndDate());
        publishSurveyEvent(AuditAction.SURVEY_SETTINGS_CHANGED, currentUsername, survey);
        return saveSettings(survey);
    }

    /**
     * 軟刪除問卷專案
     * @param surveyId 問卷 ID
     * @param currentUsername 當前操作者帳號
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSurvey(Long surveyId, String currentUsername) {
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);
        survey.softDelete(LocalDateTime.now());
        surveyRepository.saveAndFlush(survey);
        log.info("問卷已被標記軟刪除: id={}", surveyId);
        publishSurveyEvent(AuditAction.SURVEY_DELETED, currentUsername, survey);
    }

    /**
     * 發布問卷相關稽核事件 (交易提交後才寫入稽核日誌，回滾時不留紀錄)
     * @param action 事件類型
     * @param actor 操作者帳號 (建立者或管理員)
     * @param survey 對象問卷 (說明欄記錄標題與開放狀態)
     */
    private void publishSurveyEvent(AuditAction action, String actor, Survey survey) {
        String detail = survey.getTitle() + " (開放填答=" + survey.getIsOpen() + "，公開於大廳=" + survey.getIsPublicToLobby() + ")";
        eventPublisher.publishEvent(new AuditEvent(action, actor, AuditEvent.TARGET_SURVEY, String.valueOf(survey.getId()), detail));
    }

    /**
     * 內部核對問卷擁有者權限 (建立者本人或管理員)
     * @param surveyId 問卷識別碼
     * @param currentUsername 當前操作者帳號
     * @return 通過權限校驗之 Survey 實體
     * @throws IllegalArgumentException 查無問卷或操作者
     * @throws AccessDeniedException 無修改權限
     */
    private Survey findSurveyByIdAndVerifyOwner(Long surveyId, String currentUsername) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("查無此問卷專案"));

        User currentUser = userRepository.findByUsernameNative(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無當前操作者"));

        boolean isOwner = survey.isOwnedBy(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == RoleType.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("您無權限修改或刪除此問卷專案");
        }

        return survey;
    }

    /**
     * 儲存開放設定 (立即寫入以取得新版本號) 並寫入稽核日誌
     * @param survey 已套用新設定之問卷實體
     * @return 更新後之問卷詳情
     */
    private SurveyResponse saveSettings(Survey survey) {
        // [Why]: saveAndFlush 立即送出 UPDATE ... WHERE version = ?，版本衝突在此拋出 (而非交易結束時)，
        //        且回應中之 version 已是 +1 後之新值，前端下次送出才會帶到正確版本
        Survey saved = surveyRepository.saveAndFlush(survey);
        log.info("問卷時限與狀態更新成功: id={}, isOpen={}, isPublicToLobby={}",
                saved.getId(), saved.getIsOpen(), saved.getIsPublicToLobby());
        return convertToDetailResponse(saved, true);
    }

    /**
     * 校驗起訖時間先後順序 (任一為 null 視為不限期，不檢核)
     * @param startDate 開放時間
     * @param endDate 截止時間
     * @throws IllegalArgumentException 開放時間晚於截止時間
     */
    private void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("可填寫起始時間不可晚於截止時間");
        }
    }

    /**
     * 將題目 DTO 轉換為題目實體 (含選項；是非題未帶選項時自動補上「是 / 否」)
     * @param survey 所屬問卷
     * @param questionDto 題目 DTO
     * @return 題目實體
     */
    private SurveyQuestion toQuestionEntity(Survey survey, QuestionDto questionDto) {
        SurveyQuestion question = SurveyQuestion.builder()
                .survey(survey)
                .questionCode(questionDto.getQuestionCode())
                .type(questionDto.getType())
                .questionText(questionDto.getQuestionText())
                .imageUrl(questionDto.getImageUrl())
                .isRequired(questionDto.getIsRequired() != null ? questionDto.getIsRequired() : true)
                .sortOrder(questionDto.getSortOrder() != null ? questionDto.getSortOrder() : 1)
                .minRating(questionDto.getMinRating())
                .maxRating(questionDto.getMaxRating())
                .build();

        List<SurveyOption> options = new ArrayList<>();
        if (questionDto.getOptions() != null && !questionDto.getOptions().isEmpty()) {
            for (OptionDto optionDto : questionDto.getOptions()) {
                options.add(SurveyOption.builder()
                        .question(question)
                        .optionText(optionDto.getOptionText())
                        .sortOrder(optionDto.getSortOrder() != null ? optionDto.getSortOrder() : 1)
                        .triggerQuestionUid(optionDto.getTriggerQuestionUid())
                        .build());
            }
        } else if (question.getType() == QuestionType.BOOLEAN) {
            options.add(SurveyOption.builder().question(question).optionText(BOOLEAN_OPTION_YES).sortOrder(1).build());
            options.add(SurveyOption.builder().question(question).optionText(BOOLEAN_OPTION_NO).sortOrder(2).build());
        }
        question.replaceOptions(options);
        return question;
    }

    /**
     * 將基本資料收集設定 DTO 轉換為實體；未填欄位沿用實體預設值 (姓名 / 性別 / 年齡預設收集，其餘預設不收集)
     * @param dto 基本資料收集設定 DTO
     * @return 尚未綁定問卷之設定實體
     */
    private SurveyBasicInfoConfig toBasicInfoConfigEntity(BasicInfoConfigDto dto) {
        return SurveyBasicInfoConfig.builder()
                .collectBasicInfo(valueOrDefault(dto.getCollectBasicInfo(), true))
                .requireName(valueOrDefault(dto.getRequireName(), true))
                .requireGender(valueOrDefault(dto.getRequireGender(), true))
                .requireAge(valueOrDefault(dto.getRequireAge(), true))
                .requireBirthday(valueOrDefault(dto.getRequireBirthday(), false))
                .requireNationalId(valueOrDefault(dto.getRequireNationalId(), false))
                .requirePhone(valueOrDefault(dto.getRequirePhone(), false))
                .requireHomePhone(valueOrDefault(dto.getRequireHomePhone(), false))
                .requireEmail(valueOrDefault(dto.getRequireEmail(), false))
                .requireIndustry(valueOrDefault(dto.getRequireIndustry(), false))
                .requireJobTitle(valueOrDefault(dto.getRequireJobTitle(), false))
                .requireDepartment(valueOrDefault(dto.getRequireDepartment(), false))
                .requireEmployeeId(valueOrDefault(dto.getRequireEmployeeId(), false))
                .requireAcademicMajor(valueOrDefault(dto.getRequireAcademicMajor(), false))
                .requireClassName(valueOrDefault(dto.getRequireClassName(), false))
                .requireStudentId(valueOrDefault(dto.getRequireStudentId(), false))
                .requireHeight(valueOrDefault(dto.getRequireHeight(), false))
                .requireWeight(valueOrDefault(dto.getRequireWeight(), false))
                .requireResidence(valueOrDefault(dto.getRequireResidence(), false))
                .requireRegisteredAddress(valueOrDefault(dto.getRequireRegisteredAddress(), false))
                .build();
    }

    /**
     * 將基本資料收集設定實體轉換為 DTO
     * @param config 設定實體
     * @return 設定 DTO
     */
    private BasicInfoConfigDto toBasicInfoConfigDto(SurveyBasicInfoConfig config) {
        return BasicInfoConfigDto.builder()
                .collectBasicInfo(config.getCollectBasicInfo())
                .requireName(config.getRequireName())
                .requireGender(config.getRequireGender())
                .requireAge(config.getRequireAge())
                .requireBirthday(config.getRequireBirthday())
                .requireNationalId(config.getRequireNationalId())
                .requirePhone(config.getRequirePhone())
                .requireHomePhone(config.getRequireHomePhone())
                .requireEmail(config.getRequireEmail())
                .requireIndustry(config.getRequireIndustry())
                .requireJobTitle(config.getRequireJobTitle())
                .requireDepartment(config.getRequireDepartment())
                .requireEmployeeId(config.getRequireEmployeeId())
                .requireAcademicMajor(config.getRequireAcademicMajor())
                .requireClassName(config.getRequireClassName())
                .requireStudentId(config.getRequireStudentId())
                .requireHeight(config.getRequireHeight())
                .requireWeight(config.getRequireWeight())
                .requireResidence(config.getRequireResidence())
                .requireRegisteredAddress(config.getRequireRegisteredAddress())
                .build();
    }

    /**
     * 布林值空值降級
     * @param value 原始值
     * @param defaultValue value 為 null 時採用之值
     * @return 非 null 布林值
     */
    private static Boolean valueOrDefault(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * 受訪者正式進入填答頁之共用流程：檢核開放狀態與時窗、累加瀏覽次數，回傳不含分享代碼之填答結構
     * @param survey 已通過存取檢核之問卷
     * @return 填答結構 DTO (瀏覽次數為累加後之值)
     * @throws IllegalArgumentException 問卷關閉、尚未開始或已截止
     */
    private SurveyResponse openForRespondent(Survey survey) {
        survey.ensureAcceptingSubmissions(LocalDateTime.now());
        // [Why]: 僅真實受訪者進入填答頁才累加瀏覽次數 (預覽不列入)；以原子 UPDATE 遞增，高併發下不會遺失次數
        surveyRepository.incrementViewCountNative(survey.getId());
        Survey refreshed = surveyRepository.findByIdAndDeletedAtIsNullNative(survey.getId()).orElse(survey);
        return convertToDetailResponse(refreshed, false);
    }

    /**
     * 轉換為問卷完整結構詳情 Response (包含題目、選項與基本個資收集設定)
     * @param survey 問卷實體物件
     * @param exposeShareToken 是否附上分享代碼 (僅建立者 / 管理員可見；受訪者回應一律不附)
     * @return 完整結構 SurveyResponse DTO
     */
    private SurveyResponse convertToDetailResponse(Survey survey, boolean exposeShareToken) {
        long submissionCount = submissionRepository.countBySurveyIdNative(survey.getId());

        BasicInfoConfigDto configDto = survey.getBasicInfoConfig() != null
                ? toBasicInfoConfigDto(survey.getBasicInfoConfig())
                : null;

        List<QuestionDto> questionDtos = survey.getQuestions().stream().map(q -> {
            List<OptionDto> optionDtos = q.getOptions().stream().map(o -> OptionDto.builder()
                    .id(o.getId())
                    .optionText(o.getOptionText())
                    .sortOrder(o.getSortOrder())
                    .triggerQuestionUid(o.getTriggerQuestionUid())
                    .build()).collect(Collectors.toList());

            if (q.getType() == QuestionType.BOOLEAN && optionDtos.isEmpty()) {
                optionDtos.add(OptionDto.builder().optionText(BOOLEAN_OPTION_YES).sortOrder(1).build());
                optionDtos.add(OptionDto.builder().optionText(BOOLEAN_OPTION_NO).sortOrder(2).build());
            }

            return QuestionDto.builder()
                    .id(q.getId())
                    .questionCode(q.getQuestionCode())
                    .type(q.getType())
                    .questionText(q.getQuestionText())
                    .imageUrl(q.getImageUrl())
                    .isRequired(q.getIsRequired())
                    .sortOrder(q.getSortOrder())
                    .minRating(q.getMinRating())
                    .maxRating(q.getMaxRating())
                    .options(optionDtos)
                    .build();
        }).collect(Collectors.toList());

        return SurveyResponse.builder()
                .id(survey.getId())
                .userId(survey.getCreator().getId())
                .authorName(survey.getCreator().getFullName())
                .title(survey.getTitle())
                .description(survey.getDescription())
                .coverImageUrl(survey.getCoverImageUrl())
                .category(survey.getCategory())
                .isPublicToLobby(survey.getIsPublicToLobby())
                .isOpen(survey.getIsOpen())
                .startDate(survey.getStartDate())
                .endDate(survey.getEndDate())
                .viewCount(survey.getViewCount())
                .shareToken(exposeShareToken ? survey.getShareToken() : null)
                .version(survey.getVersion())
                .submissionCount(submissionCount)
                .basicInfoConfig(configDto)
                .questions(questionDtos)
                .createdAt(survey.getCreatedAt())
                .updatedAt(survey.getUpdatedAt())
                .build();
    }

    /**
     * 轉換為問卷概要清單 Response (供大廳或個人專案列表卡片檢視)
     * @param survey 問卷實體物件
     * @param userSurveyNumber 會員個人問卷序號 (僅個人專案清單使用，其餘傳 null)
     * @param exposeShareToken 是否附上分享代碼 (個人與管理員清單附上供複製連結；大廳公開清單不附)
     * @return 概要 SurveySummaryResponse DTO
     */
    private SurveySummaryResponse convertToSummaryResponse(Survey survey, Integer userSurveyNumber, boolean exposeShareToken) {
        long submissionCount = submissionRepository.countBySurveyIdNative(survey.getId());
        return SurveySummaryResponse.builder()
                .id(survey.getId())
                .title(survey.getTitle())
                .description(survey.getDescription())
                .coverImageUrl(survey.getCoverImageUrl())
                .category(survey.getCategory())
                .isPublicToLobby(survey.getIsPublicToLobby())
                .isOpen(survey.getIsOpen())
                .authorName(survey.getCreator().getFullName())
                .startDate(survey.getStartDate())
                .endDate(survey.getEndDate())
                .viewCount(survey.getViewCount())
                .shareToken(exposeShareToken ? survey.getShareToken() : null)
                .version(survey.getVersion())
                .submissionCount(submissionCount)
                .questionCount(survey.getQuestions().size())
                .createdAt(survey.getCreatedAt())
                .userSurveyNumber(userSurveyNumber)
                .build();
    }
}
