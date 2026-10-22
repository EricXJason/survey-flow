/**
 * ============================================================================
 * 檔案名稱: SurveyService.java
 * 所屬模組: Service 業務邏輯層 - 問卷核心模組
 * 責任描述: 負責問卷專案之建立、修改、軟刪除、樹狀結構組裝與大廳公開檢索
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRepository, UserRepository, SubmissionRepository
 *   - 下游消費: SurveyController
 * 邊界處理: 嚴格核對問卷發布者權限，非建立者或管理員嚴禁修改或刪除
 * ============================================================================
 */

package com.surveyflow.service;

import com.surveyflow.common.enums.RoleType;
import com.surveyflow.dto.survey.*;
import com.surveyflow.entity.*;
import com.surveyflow.repository.SubmissionRepository;
import com.surveyflow.repository.SurveyRepository;
import com.surveyflow.repository.UserRepository;
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

    /**
     * 建立新問卷 (支援草稿或直接發布)
     * @param request 建立參數
     * @param currentUsername 當前登入會員帳號
     * @return 建立完成後之問卷詳情
     */
    @Transactional
    public SurveyResponse createSurvey(SurveyCreateRequest request, String currentUsername) {
        User creator = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無此建立者帳號"));

        Survey survey = Survey.builder()
                .creator(creator)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .category(request.getCategory() != null ? request.getCategory() : "綜合調查")
                .isOpen(request.getIsOpen() != null ? request.getIsOpen() : true)
                .isPublicToLobby(request.getIsPublicToLobby() != null ? request.getIsPublicToLobby() : true)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .viewCount(0)
                .build();

        // 綁定基本資料收集設定
        BasicInfoConfigDto configDto = request.getBasicInfoConfig();
        if (configDto != null) {
            SurveyBasicInfoConfig config = SurveyBasicInfoConfig.builder()
                    .survey(survey)
                    .collectBasicInfo(configDto.getCollectBasicInfo() != null ? configDto.getCollectBasicInfo() : true)
                    .requireName(Boolean.TRUE.equals(configDto.getRequireName()))
                    .requireGender(Boolean.TRUE.equals(configDto.getRequireGender()))
                    .requireAge(Boolean.TRUE.equals(configDto.getRequireAge()))
                    .requireBirthday(Boolean.TRUE.equals(configDto.getRequireBirthday()))
                    .requireNationalId(Boolean.TRUE.equals(configDto.getRequireNationalId()))
                    .requirePhone(Boolean.TRUE.equals(configDto.getRequirePhone()))
                    .requireHomePhone(Boolean.TRUE.equals(configDto.getRequireHomePhone()))
                    .requireEmail(Boolean.TRUE.equals(configDto.getRequireEmail()))
                    .requireIndustry(Boolean.TRUE.equals(configDto.getRequireIndustry()))
                    .requireJobTitle(Boolean.TRUE.equals(configDto.getRequireJobTitle()))
                    .requireDepartment(Boolean.TRUE.equals(configDto.getRequireDepartment()))
                    .requireEmployeeId(Boolean.TRUE.equals(configDto.getRequireEmployeeId()))
                    .requireAcademicMajor(Boolean.TRUE.equals(configDto.getRequireAcademicMajor()))
                    .requireClassName(Boolean.TRUE.equals(configDto.getRequireClassName()))
                    .requireStudentId(Boolean.TRUE.equals(configDto.getRequireStudentId()))
                    .requireHeight(Boolean.TRUE.equals(configDto.getRequireHeight()))
                    .requireWeight(Boolean.TRUE.equals(configDto.getRequireWeight()))
                    .requireResidence(Boolean.TRUE.equals(configDto.getRequireResidence()))
                    .requireRegisteredAddress(Boolean.TRUE.equals(configDto.getRequireRegisteredAddress()))
                    .build();
            survey.setBasicInfoConfig(config);
        }

        // 綁定題目與選項
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            List<SurveyQuestion> questions = new ArrayList<>();
            for (QuestionDto qDto : request.getQuestions()) {
                SurveyQuestion question = SurveyQuestion.builder()
                        .survey(survey)
                        .questionCode(qDto.getQuestionCode())
                        .type(qDto.getType())
                        .questionText(qDto.getQuestionText())
                        .imageUrl(qDto.getImageUrl())
                        .isRequired(qDto.getIsRequired() != null ? qDto.getIsRequired() : true)
                        .sortOrder(qDto.getSortOrder() != null ? qDto.getSortOrder() : 1)
                        .minRating(qDto.getMinRating())
                        .maxRating(qDto.getMaxRating())
                        .build();

                if (qDto.getOptions() != null && !qDto.getOptions().isEmpty()) {
                    List<SurveyOption> options = new ArrayList<>();
                    for (OptionDto oDto : qDto.getOptions()) {
                        SurveyOption option = SurveyOption.builder()
                                .question(question)
                                .optionText(oDto.getOptionText())
                                .sortOrder(oDto.getSortOrder() != null ? oDto.getSortOrder() : 1)
                                .triggerQuestionUid(oDto.getTriggerQuestionUid())
                                .build();
                        options.add(option);
                    }
                    question.setOptions(options);
                }
                questions.add(question);
            }
            survey.setQuestions(questions);
        }

        survey = surveyRepository.save(survey);
        log.info("問卷專案建立成功: id={}, title={}, creator={}", survey.getId(), survey.getTitle(), currentUsername);
        return convertToDetailResponse(survey);
    }

    /**
     * 更新問卷專案內容
     * @param surveyId 問卷 ID
     * @param request 更新請求
     * @param currentUsername 當前操作者帳號
     * @return 更新後之問卷詳情
     */
    @Transactional
    public SurveyResponse updateSurvey(Long surveyId, SurveyUpdateRequest request, String currentUsername) {
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);

        if (request.getTitle() != null) survey.setTitle(request.getTitle());
        if (request.getDescription() != null) survey.setDescription(request.getDescription());
        if (request.getCoverImageUrl() != null) survey.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getCategory() != null) survey.setCategory(request.getCategory());
        if (request.getIsPublicToLobby() != null) survey.setIsPublicToLobby(request.getIsPublicToLobby());
        if (request.getIsOpen() != null) survey.setIsOpen(request.getIsOpen());
        if (request.getStartDate() != null) survey.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) survey.setEndDate(request.getEndDate());

        survey = surveyRepository.save(survey);
        log.info("問卷專案更新成功: id={}", survey.getId());
        return convertToDetailResponse(survey);
    }

    /**
     * 更新問卷可填寫日期時間區間、大廳公開性與即時填寫開放狀態
     * @param surveyId 問卷 ID
     * @param request 時限與狀態設定請求
     * @param currentUsername 當前操作者帳號
     * @return 更新後之問卷詳情
     */
    @Transactional
    public SurveyResponse updateSurveySettings(Long surveyId, SurveySettingsRequest request, String currentUsername) {
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new IllegalArgumentException("可填寫起始時間不可晚於截止時間");
            }
        }

        survey.setStartDate(request.getStartDate());
        survey.setEndDate(request.getEndDate());
        boolean isOpen = Boolean.TRUE.equals(request.getIsOpen());
        boolean isPublic = Boolean.TRUE.equals(request.getIsPublicToLobby());

        survey.setIsPublicToLobby(isPublic);
        survey.setIsOpen(isOpen);

        survey = surveyRepository.save(survey);
        log.info("問卷時限與狀態更新成功: id={}, isOpen={}, isPublicToLobby={}",
                survey.getId(), survey.getIsOpen(), survey.getIsPublicToLobby());
        return convertToDetailResponse(survey);
    }

    /**
     * 查詢單一問卷完整結構
     * @param surveyId 問卷 ID
     * @return 完整結構 DTO
     */
    @Transactional(readOnly = true)
    public SurveyResponse getSurveyDetail(Long surveyId) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("查無此問卷專案"));
        return convertToDetailResponse(survey);
    }

    /**
     * 受訪者進入填答前公開獲取問卷 (自動累加瀏覽次數)
     * @param surveyId 問卷 ID
     * @return 完整填答結構
     */
    @Transactional
    public SurveyResponse getPublicSurvey(Long surveyId) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("問卷不存在或已下架"));

        if (Boolean.FALSE.equals(survey.getIsOpen())) {
            throw new IllegalArgumentException("此問卷目前尚未開放填答，或已由發起人手動關閉");
        }

        LocalDateTime now = LocalDateTime.now();
        if (survey.getStartDate() != null && now.isBefore(survey.getStartDate())) {
            throw new IllegalArgumentException("此問卷尚未到達開放填答時間，請於 " + survey.getStartDate() + " 後再次嘗試");
        }
        if (survey.getEndDate() != null && now.isAfter(survey.getEndDate())) {
            throw new IllegalArgumentException("此問卷已過截止期限，無法再進行填答");
        }

        // 累加瀏覽次數
        survey.setViewCount(survey.getViewCount() + 1);
        surveyRepository.save(survey);

        return convertToDetailResponse(survey);
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
        Page<Survey> page = surveyRepository.searchPublishedSurveysNative(category, keyword, pageable);
        return page.map(this::convertToSummaryResponse);
    }

    /**
     * 查詢會員自己建立之問卷清單 (管理專區)
     * @param currentUsername 當前登入會員
     * @param pageable 分頁參數
     * @return 該會員之問卷分頁結果
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> getUserSurveys(String currentUsername, Pageable pageable) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無此使用者"));

        // [Why]: 依建立時間由舊至新為每份問卷編定該會員之個人問卷編號 (#1, #2, #3...)，使會員在個人專案中看到的是自己的問卷序號，而非系統全域 ID
        List<Long> userSurveyIds = surveyRepository.findSurveyIdsByUserIdOrderByCreatedAtAscNative(user.getId());
        Map<Long, Integer> numberMap = new HashMap<>();
        for (int i = 0; i < userSurveyIds.size(); i++) {
            numberMap.put(userSurveyIds.get(i), i + 1);
        }

        Page<Survey> page = surveyRepository.findByUserIdAndDeletedAtIsNullNative(user.getId(), pageable);
        return page.map(survey -> {
            SurveySummaryResponse dto = convertToSummaryResponse(survey);
            dto.setUserSurveyNumber(numberMap.getOrDefault(survey.getId(), 1));
            return dto;
        });
    }

    /**
     * 系統管理員查詢全站所有問卷清單 (包含公開與非公開/私人專案)
     * @param pageable 分頁參數
     * @return 全站所有問卷之概要分頁清單
     */
    @Transactional(readOnly = true)
    public Page<SurveySummaryResponse> getAllSurveysForAdmin(Pageable pageable) {
        Page<Survey> page = surveyRepository.findAllSurveysForAdminNative(pageable);
        return page.map(this::convertToSummaryResponse);
    }

    /**
     * 軟刪除問卷專案
     * @param surveyId 問卷 ID
     * @param currentUsername 當前操作者帳號
     */
    @Transactional
    public void deleteSurvey(Long surveyId, String currentUsername) {
        Survey survey = findSurveyByIdAndVerifyOwner(surveyId, currentUsername);
        survey.setDeletedAt(LocalDateTime.now());
        surveyRepository.save(survey);
        log.info("問卷已被標記軟刪除: id={}", surveyId);
    }

    /**
     * 內部核對問卷擁有者權限 (建立者本人或管理員)
     */
    private Survey findSurveyByIdAndVerifyOwner(Long surveyId, String currentUsername) {
        Survey survey = surveyRepository.findByIdAndDeletedAtIsNullNative(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("查無此問卷專案"));

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalArgumentException("查無當前操作者"));

        boolean isOwner = survey.getCreator().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == RoleType.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("您無權限修改或刪除此問卷專案");
        }

        return survey;
    }

    /**
     * 轉換為詳情 Response
     */
    private SurveyResponse convertToDetailResponse(Survey survey) {
        long submissionCount = submissionRepository.countBySurveyIdNative(survey.getId());

        BasicInfoConfigDto configDto = null;
        if (survey.getBasicInfoConfig() != null) {
            SurveyBasicInfoConfig c = survey.getBasicInfoConfig();
            configDto = BasicInfoConfigDto.builder()
                    .collectBasicInfo(c.getCollectBasicInfo())
                    .requireName(c.getRequireName())
                    .requireGender(c.getRequireGender())
                    .requireAge(c.getRequireAge())
                    .requireBirthday(c.getRequireBirthday())
                    .requireNationalId(c.getRequireNationalId())
                    .requirePhone(c.getRequirePhone())
                    .requireHomePhone(c.getRequireHomePhone())
                    .requireEmail(c.getRequireEmail())
                    .requireIndustry(c.getRequireIndustry())
                    .requireJobTitle(c.getRequireJobTitle())
                    .requireDepartment(c.getRequireDepartment())
                    .requireEmployeeId(c.getRequireEmployeeId())
                    .requireAcademicMajor(c.getRequireAcademicMajor())
                    .requireClassName(c.getRequireClassName())
                    .requireStudentId(c.getRequireStudentId())
                    .requireHeight(c.getRequireHeight())
                    .requireWeight(c.getRequireWeight())
                    .requireResidence(c.getRequireResidence())
                    .requireRegisteredAddress(c.getRequireRegisteredAddress())
                    .build();
        }

        List<QuestionDto> questionDtos = survey.getQuestions().stream().map(q -> {
            List<OptionDto> optionDtos = q.getOptions().stream().map(o -> OptionDto.builder()
                    .id(o.getId())
                    .optionText(o.getOptionText())
                    .sortOrder(o.getSortOrder())
                    .triggerQuestionUid(o.getTriggerQuestionUid())
                    .build()).collect(Collectors.toList());

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
                .userId(survey.getCreator() != null ? survey.getCreator().getId() : null)
                .authorName(survey.getCreator() != null ? survey.getCreator().getFullName() : "未知")
                .title(survey.getTitle())
                .description(survey.getDescription())
                .coverImageUrl(survey.getCoverImageUrl())
                .category(survey.getCategory())
                .isPublicToLobby(survey.getIsPublicToLobby())
                .isOpen(survey.getIsOpen())
                .startDate(survey.getStartDate())
                .endDate(survey.getEndDate())
                .viewCount(survey.getViewCount())
                .submissionCount(submissionCount)
                .basicInfoConfig(configDto)
                .questions(questionDtos)
                .createdAt(survey.getCreatedAt())
                .updatedAt(survey.getUpdatedAt())
                .build();
    }

    /**
     * 轉換為概要 Response
     */
    private SurveySummaryResponse convertToSummaryResponse(Survey survey) {
        long submissionCount = submissionRepository.countBySurveyIdNative(survey.getId());
        return SurveySummaryResponse.builder()
                .id(survey.getId())
                .title(survey.getTitle())
                .description(survey.getDescription())
                .coverImageUrl(survey.getCoverImageUrl())
                .category(survey.getCategory())
                .isPublicToLobby(survey.getIsPublicToLobby())
                .isOpen(survey.getIsOpen())
                .authorName(survey.getCreator() != null ? survey.getCreator().getFullName() : "未知")
                .startDate(survey.getStartDate())
                .endDate(survey.getEndDate())
                .viewCount(survey.getViewCount())
                .submissionCount(submissionCount)
                .questionCount(survey.getQuestions() != null ? survey.getQuestions().size() : 0)
                .createdAt(survey.getCreatedAt())
                .build();
    }
}
