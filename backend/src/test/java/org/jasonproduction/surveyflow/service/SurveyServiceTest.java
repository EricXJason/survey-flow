/**
 * ============================================================================
 * 檔案名稱: SurveyServiceTest.java
 * 測試對象: SurveyService (問卷建立、查詢、預覽、更新、開放設定 PUT / PATCH、軟刪除與權限核對)
 * 測試方式: JUnit 5 + Mockito；SurveyRepository、UserRepository、SubmissionRepository 以 Mock 取代
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.persistence.OptimisticLockException;
import org.jasonproduction.surveyflow.common.enums.QuestionType;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.dto.survey.BasicInfoConfigDto;
import org.jasonproduction.surveyflow.dto.survey.OptionDto;
import org.jasonproduction.surveyflow.dto.survey.QuestionDto;
import org.jasonproduction.surveyflow.dto.survey.SurveyCreateRequest;
import org.jasonproduction.surveyflow.dto.survey.SurveyResponse;
import org.jasonproduction.surveyflow.dto.survey.SurveySettingsRequest;
import org.jasonproduction.surveyflow.dto.survey.SurveySummaryResponse;
import org.jasonproduction.surveyflow.dto.survey.SurveyUpdateRequest;
import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.SurveyBasicInfoConfig;
import org.jasonproduction.surveyflow.entity.SurveyQuestion;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.repository.SubmissionRepository;
import org.jasonproduction.surveyflow.repository.SurveyRepository;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// [Why]: 多數測試都會經過 convertToDetailResponse (查詢回收數)，該 Stub 統一寫在 setUp；
//        少數提早拋出例外之測試用不到它，故放寬 Mockito 對「未使用 Stub」的嚴格檢查
@MockitoSettings(strictness = Strictness.LENIENT)
class SurveyServiceTest {

    @Mock private SurveyRepository surveyRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SurveyService surveyService;

    private User owner;
    private User otherUser;
    private User admin;
    private Survey survey;
    private final LocalDateTime now = LocalDateTime.now();

    /**
     * 建立受測物件與三種身分 (建立者、無關會員、管理員) 及一份開放中之問卷 (id 100)
     */
    @BeforeEach
    void setUp() {
        surveyService = new SurveyService(surveyRepository, userRepository, submissionRepository, eventPublisher);

        owner = User.builder().id(1L).username("owner").fullName("建立者").role(RoleType.ROLE_USER).build();
        otherUser = User.builder().id(2L).username("other").fullName("路人").role(RoleType.ROLE_USER).build();
        admin = User.builder().id(3L).username("admin").fullName("管理員").role(RoleType.ROLE_ADMIN).build();

        survey = Survey.builder()
                .id(100L)
                .creator(owner)
                .title("顧客滿意度調查")
                .isOpen(true)
                .isPublicToLobby(true)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(7))
                .viewCount(10)
                .build();

        when(userRepository.findByUsernameNative("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findByUsernameNative("other")).thenReturn(Optional.of(otherUser));
        when(userRepository.findByUsernameNative("admin")).thenReturn(Optional.of(admin));
        when(surveyRepository.findByIdAndDeletedAtIsNullNative(100L)).thenReturn(Optional.of(survey));
        when(surveyRepository.save(any(Survey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(surveyRepository.saveAndFlush(any(Survey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.countBySurveyIdNative(anyLong())).thenReturn(5L);
        // 模擬資料庫目前版本號為 3 (樂觀鎖)
        ReflectionTestUtils.setField(survey, "version", 3L);
        ReflectionTestUtils.setField(survey, "shareToken", "kPL6FRh2idiOuIO-tF3naA");
    }

    @Nested
    @DisplayName("createSurvey 建立問卷")
    class CreateSurvey {

        @Test
        @DisplayName("完整請求：題目、選項與基本資料設定皆建立雙向關聯，未帶之設定欄位沿用預設值")
        void fullRequest_BuildsAggregate() {
            // Given
            SurveyCreateRequest request = SurveyCreateRequest.builder()
                    .title("新問卷")
                    .category(null)
                    .basicInfoConfig(BasicInfoConfigDto.builder().requireEmail(true).build())
                    .questions(List.of(QuestionDto.builder()
                            .questionCode("Q1")
                            .type(QuestionType.SINGLE)
                            .questionText("您的年齡？")
                            .options(List.of(OptionDto.builder().optionText("20-29").triggerQuestionUid("Q2").build()))
                            .build()))
                    .build();
            // 模擬資料庫在 INSERT 後配發主鍵 (Entity 無 Setter，以 ReflectionTestUtils 寫入)
            when(surveyRepository.save(any(Survey.class))).thenAnswer(invocation -> {
                Survey toSave = invocation.getArgument(0);
                ReflectionTestUtils.setField(toSave, "id", 200L);
                return toSave;
            });

            // When
            SurveyResponse response = surveyService.createSurvey(request, "owner");

            // Then
            ArgumentCaptor<Survey> saved = ArgumentCaptor.forClass(Survey.class);
            verify(surveyRepository).save(saved.capture());
            Survey created = saved.getValue();
            ArgumentCaptor<AuditEvent> auditEvent = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(auditEvent.capture());
            assertEquals(AuditAction.SURVEY_CREATED, auditEvent.getValue().action());
            assertEquals("owner", auditEvent.getValue().actor());
            assertEquals(AuditEvent.TARGET_SURVEY, auditEvent.getValue().targetType());
            assertEquals("綜合調查", created.getCategory(), "未指定分類時使用預設分類");
            SurveyBasicInfoConfig config = created.getBasicInfoConfig();
            assertTrue(config.getRequireEmail());
            assertTrue(config.getRequireName(), "未帶之欄位：姓名預設收集");
            assertFalse(config.getRequirePhone(), "未帶之欄位：手機預設不收集");
            assertEquals(created, config.getSurvey());
            SurveyQuestion question = created.getQuestions().get(0);
            assertEquals(created, question.getSurvey());
            assertEquals("Q2", question.getOptions().get(0).getTriggerQuestionUid());
            assertEquals("新問卷", response.getTitle());
            assertEquals(200L, response.getId());
            assertEquals(5L, response.getSubmissionCount());
        }

        @Test
        @DisplayName("是非題未帶選項：自動補上「是 / 否」兩個選項")
        void booleanQuestionWithoutOptions_AddsYesNo() {
            // Given
            SurveyCreateRequest request = SurveyCreateRequest.builder()
                    .title("是非題問卷")
                    .questions(List.of(QuestionDto.builder().type(QuestionType.BOOLEAN).questionText("是否推薦？").options(List.of()).build()))
                    .build();

            // When
            SurveyResponse response = surveyService.createSurvey(request, "owner");

            // Then
            List<OptionDto> options = response.getQuestions().get(0).getOptions();
            assertEquals(List.of("是", "否"), options.stream().map(OptionDto::getOptionText).toList());
        }

        @Test
        @DisplayName("開始時間晚於截止時間：拋出 IllegalArgumentException，不儲存")
        void invalidDateRange_Throws() {
            // Given
            SurveyCreateRequest request = SurveyCreateRequest.builder()
                    .title("錯誤時間").startDate(now.plusDays(2)).endDate(now).build();

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> surveyService.createSurvey(request, "owner"));
            verify(surveyRepository, never()).save(any());
        }

        @Test
        @DisplayName("建立者帳號不存在：拋出 IllegalArgumentException")
        void unknownCreator_Throws() {
            when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> surveyService.createSurvey(SurveyCreateRequest.builder().title("x").build(), "ghost"));
        }
    }

    @Nested
    @DisplayName("getSurveyDetail / getPublicSurvey 查詢與預覽")
    class GetSurvey {

        @Test
        @DisplayName("建立者查詢詳情：回傳完整結構")
        void detail_ByOwner_Returns() {
            assertEquals("顧客滿意度調查", surveyService.getSurveyDetail(100L, "owner").getTitle());
        }

        @Test
        @DisplayName("管理員查詢他人問卷詳情：允許")
        void detail_ByAdmin_Returns() {
            assertNotNull(surveyService.getSurveyDetail(100L, "admin"));
        }

        @Test
        @DisplayName("無關會員查詢詳情：拋出 AccessDeniedException")
        void detail_ByOther_Denied() {
            assertThrows(AccessDeniedException.class, () -> surveyService.getSurveyDetail(100L, "other"));
        }

        @Test
        @DisplayName("受訪者正式進入填答頁：以原子 UPDATE 遞增瀏覽數 (不經過 Entity 儲存，不觸發樂觀鎖)，回傳重新查詢後之最新資料")
        void publicSurvey_IncrementsViewCountAtomically() {
            // Given：第二次查詢 (遞增後) 回傳瀏覽數 11 之問卷
            Survey afterIncrement = Survey.builder().id(100L).creator(owner).title("顧客滿意度調查").viewCount(11).build();
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(100L)).thenReturn(Optional.of(survey), Optional.of(afterIncrement));

            // When
            SurveyResponse response = surveyService.getPublicSurvey(100L, false, null);

            // Then
            verify(surveyRepository).incrementViewCountNative(100L);
            verify(surveyRepository, never()).save(any());
            assertEquals(11, response.getViewCount());
        }

        @Test
        @DisplayName("問卷已截止：拋出 IllegalArgumentException，瀏覽數不變")
        void publicSurvey_Ended_Throws() {
            // Given
            survey.patchSettings(null, now.minusDays(1), null, null);

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> surveyService.getPublicSurvey(100L, false, null));
            verify(surveyRepository, never()).incrementViewCountNative(anyLong());
        }

        @Test
        @DisplayName("問卷不存在或已刪除：拋出 IllegalArgumentException")
        void publicSurvey_NotFound_Throws() {
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> surveyService.getPublicSurvey(999L, false, null));
        }

        @Test
        @DisplayName("受訪者以 ID 進入未公開於大廳之問卷：拋出「問卷不存在或已下架」，瀏覽數不變 (必須改用分享連結)")
        void publicSurvey_LinkOnlyById_Throws() {
            survey.patchSettings(null, null, null, false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> surveyService.getPublicSurvey(100L, false, null));
            assertEquals("問卷不存在或已下架", ex.getMessage());
            verify(surveyRepository, never()).incrementViewCountNative(anyLong());
        }

        @Test
        @DisplayName("受訪者回應不附分享代碼；建立者查詢詳情則附上，供複製連結")
        void shareToken_OnlyExposedToOwner() {
            assertNull(surveyService.getPublicSurvey(100L, false, null).getShareToken());
            assertEquals("kPL6FRh2idiOuIO-tF3naA", surveyService.getSurveyDetail(100L, "owner").getShareToken());
        }

        @Test
        @DisplayName("以分享代碼進入未公開於大廳之問卷：允許並累加瀏覽數，回應不附代碼")
        void sharedSurvey_LinkOnly_Returns() {
            survey.patchSettings(null, null, null, false);
            when(surveyRepository.findByShareTokenAndDeletedAtIsNullNative("kPL6FRh2idiOuIO-tF3naA")).thenReturn(Optional.of(survey));

            SurveyResponse response = surveyService.getSharedSurvey("kPL6FRh2idiOuIO-tF3naA");

            assertEquals(100L, response.getId());
            assertNull(response.getShareToken());
            verify(surveyRepository).incrementViewCountNative(100L);
        }

        @Test
        @DisplayName("分享代碼不存在：拋出「問卷不存在或已下架」")
        void sharedSurvey_UnknownToken_Throws() {
            when(surveyRepository.findByShareTokenAndDeletedAtIsNullNative("nope")).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> surveyService.getSharedSurvey("nope"));
            assertEquals("問卷不存在或已下架", ex.getMessage());
        }

        @Test
        @DisplayName("以分享代碼進入已關閉之問卷：仍檢核開放狀態，拋出 IllegalArgumentException")
        void sharedSurvey_Closed_Throws() {
            survey.patchSettings(null, null, false, null);
            when(surveyRepository.findByShareTokenAndDeletedAtIsNullNative("kPL6FRh2idiOuIO-tF3naA")).thenReturn(Optional.of(survey));

            assertThrows(IllegalArgumentException.class, () -> surveyService.getSharedSurvey("kPL6FRh2idiOuIO-tF3naA"));
            verify(surveyRepository, never()).incrementViewCountNative(anyLong());
        }

        @Test
        @DisplayName("建立者預覽已關閉之問卷：允許，且不檢核時窗、不累加瀏覽數")
        void preview_ByOwner_SkipsWindowAndViewCount() {
            // Given
            survey.patchSettings(null, null, false, null);

            // When
            SurveyResponse response = surveyService.getPublicSurvey(100L, true, "owner");

            // Then
            assertNotNull(response);
            verify(surveyRepository, never()).incrementViewCountNative(anyLong());
        }

        @Test
        @DisplayName("未登入者預覽：拋出 AccessDeniedException")
        void preview_Anonymous_Denied() {
            assertThrows(AccessDeniedException.class, () -> surveyService.getPublicSurvey(100L, true, null));
        }

        @Test
        @DisplayName("無關會員預覽：拋出 AccessDeniedException")
        void preview_ByOther_Denied() {
            assertThrows(AccessDeniedException.class, () -> surveyService.getPublicSurvey(100L, true, "other"));
        }
    }

    @Nested
    @DisplayName("清單查詢")
    class ListSurveys {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("大廳檢索：以當下時間查詢並轉為摘要 (不帶個人序號)")
        void lobby_ReturnsSummaries() {
            // Given
            when(surveyRepository.searchPublishedSurveysNative(eq("教育"), eq("滿意"), any(LocalDateTime.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(survey)));

            // When
            Page<SurveySummaryResponse> page = surveyService.searchLobbySurveys("教育", "滿意", pageable);

            // Then
            SurveySummaryResponse summary = page.getContent().get(0);
            assertEquals("建立者", summary.getAuthorName());
            assertEquals(5L, summary.getSubmissionCount());
            assertNull(summary.getUserSurveyNumber());
            assertNull(summary.getShareToken(), "大廳公開清單不得附分享代碼");
        }

        @Test
        @DisplayName("個人問卷清單：依建立順序給予個人序號 (#1, #2 ...)")
        void userSurveys_AssignsPersonalNumbers() {
            // Given：該會員共有 3 份問卷，本問卷 (100) 為第 2 份
            when(surveyRepository.findSurveyIdsByUserIdOrderByCreatedAtAscNative(1L)).thenReturn(List.of(50L, 100L, 150L));
            when(surveyRepository.findByUserIdAndDeletedAtIsNullNative(1L, pageable)).thenReturn(new PageImpl<>(List.of(survey)));

            // When
            Page<SurveySummaryResponse> page = surveyService.getUserSurveys("owner", pageable);

            // Then
            assertEquals(2, page.getContent().get(0).getUserSurveyNumber());
            assertEquals("kPL6FRh2idiOuIO-tF3naA", page.getContent().get(0).getShareToken(), "個人清單附分享代碼供複製連結");
        }

        @Test
        @DisplayName("管理員全站清單：直接轉為摘要")
        void adminSurveys_ReturnsAll() {
            when(surveyRepository.findAllSurveysForAdminNative(pageable)).thenReturn(new PageImpl<>(List.of(survey)));

            assertEquals(1, surveyService.getAllSurveysForAdmin(pageable).getTotalElements());
        }
    }

    @Nested
    @DisplayName("updateSurvey / 開放設定")
    class UpdateSurvey {

        @Test
        @DisplayName("更新內容：只覆寫有帶的欄位")
        void update_PartialFields() {
            // When
            surveyService.updateSurvey(100L, SurveyUpdateRequest.builder().title("新標題").build(), "owner");

            // Then
            assertEquals("新標題", survey.getTitle());
            assertTrue(survey.getIsOpen());
        }

        @Test
        @DisplayName("回應帶出目前版本號，前端下次修改時須原樣送回")
        void update_ResponseCarriesVersion() {
            SurveyResponse response = surveyService.updateSurvey(100L, SurveyUpdateRequest.builder().title("新標題").version(3L).build(), "owner");

            assertEquals(3L, response.getVersion());
            verify(surveyRepository).saveAndFlush(survey);
        }

        @Test
        @DisplayName("樂觀鎖：前端送來之版本 (2) 與資料庫 (3) 不符 → 拋出 OptimisticLockException，不寫入")
        void update_StaleVersion_Throws() {
            SurveyUpdateRequest request = SurveyUpdateRequest.builder().title("過期的修改").version(2L).build();

            assertThrows(OptimisticLockException.class, () -> surveyService.updateSurvey(100L, request, "owner"));
            assertEquals("顧客滿意度調查", survey.getTitle());
            verify(surveyRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("樂觀鎖：PUT / PATCH 開放設定同樣比對版本")
        void settings_StaleVersion_Throws() {
            SurveySettingsRequest stale = SurveySettingsRequest.builder().isOpen(true).isPublicToLobby(true).version(1L).build();

            assertThrows(OptimisticLockException.class, () -> surveyService.replaceSurveySettings(100L, stale, "owner"));
            assertThrows(OptimisticLockException.class, () -> surveyService.patchSurveySettings(100L, stale, "owner"));
        }

        @Test
        @DisplayName("更新後開始時間晚於截止時間：拋出 IllegalArgumentException")
        void update_InvalidRange_Throws() {
            SurveyUpdateRequest request = SurveyUpdateRequest.builder().startDate(now.plusDays(30)).build();

            assertThrows(IllegalArgumentException.class, () -> surveyService.updateSurvey(100L, request, "owner"));
        }

        @Test
        @DisplayName("無關會員更新：拋出 AccessDeniedException")
        void update_ByOther_Denied() {
            assertThrows(AccessDeniedException.class,
                    () -> surveyService.updateSurvey(100L, SurveyUpdateRequest.builder().title("x").build(), "other"));
        }

        @Test
        @DisplayName("PUT 整體覆蓋：起訖時間清為 null (不限期)，開關依請求設定")
        void replaceSettings_ClearsDates() {
            // When
            surveyService.replaceSurveySettings(100L, SurveySettingsRequest.builder()
                    .startDate(null).endDate(null).isOpen(false).isPublicToLobby(true).build(), "owner");

            // Then
            assertNull(survey.getStartDate());
            assertNull(survey.getEndDate());
            assertFalse(survey.getIsOpen());
        }

        @Test
        @DisplayName("PUT 起訖順序錯誤：拋出 IllegalArgumentException")
        void replaceSettings_InvalidRange_Throws() {
            SurveySettingsRequest request = SurveySettingsRequest.builder()
                    .startDate(now.plusDays(2)).endDate(now).isOpen(true).isPublicToLobby(true).build();

            assertThrows(IllegalArgumentException.class, () -> surveyService.replaceSurveySettings(100L, request, "owner"));
        }

        @Test
        @DisplayName("PATCH 只切換大廳公開：起訖時間與開放狀態保持原值 (修正前曾被清空)")
        void patchSettings_OnlyLobbyFlag() {
            // Given
            LocalDateTime originalStart = survey.getStartDate();

            // When
            surveyService.patchSurveySettings(100L, SurveySettingsRequest.builder().isPublicToLobby(false).build(), "owner");

            // Then
            assertFalse(survey.getIsPublicToLobby());
            assertTrue(survey.getIsOpen());
            assertEquals(originalStart, survey.getStartDate());
        }
    }

    @Nested
    @DisplayName("deleteSurvey 軟刪除")
    class DeleteSurvey {

        @Test
        @DisplayName("建立者刪除：寫入 deletedAt")
        void delete_ByOwner() {
            surveyService.deleteSurvey(100L, "owner");

            assertNotNull(survey.getDeletedAt());
        }

        @Test
        @DisplayName("管理員刪除他人問卷：允許")
        void delete_ByAdmin() {
            surveyService.deleteSurvey(100L, "admin");

            assertNotNull(survey.getDeletedAt());
        }

        @Test
        @DisplayName("無關會員刪除：拋出 AccessDeniedException 且不寫入")
        void delete_ByOther_Denied() {
            assertThrows(AccessDeniedException.class, () -> surveyService.deleteSurvey(100L, "other"));
            assertNull(survey.getDeletedAt());
        }

        @Test
        @DisplayName("操作者帳號不存在：拋出 IllegalArgumentException")
        void delete_UnknownOperator_Throws() {
            when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> surveyService.deleteSurvey(100L, "ghost"));
        }
    }

    @Nested
    @DisplayName("預設值與邊界")
    class DefaultsAndEdges {

        @Test
        @DisplayName("建立時明確指定關閉、不公開，以及題目必填 / 排序 / 選項排序：以請求值為準")
        void create_ExplicitValuesOverrideDefaults() {
            SurveyCreateRequest request = SurveyCreateRequest.builder().title("指定值").isOpen(false).isPublicToLobby(false)
                    .questions(List.of(QuestionDto.builder().type(QuestionType.BOOLEAN).questionText("Q").isRequired(false).sortOrder(3)
                            .options(List.of(OptionDto.builder().optionText("對").sortOrder(2).build())).build()))
                    .build();

            surveyService.createSurvey(request, "owner");

            ArgumentCaptor<Survey> saved = ArgumentCaptor.forClass(Survey.class);
            verify(surveyRepository).save(saved.capture());
            assertFalse(saved.getValue().getIsOpen());
            assertFalse(saved.getValue().getIsPublicToLobby());
            SurveyQuestion question = saved.getValue().getQuestions().get(0);
            assertFalse(question.getIsRequired());
            assertEquals(3, question.getSortOrder());
            assertEquals(1, question.getOptions().size(), "是非題已帶自訂選項時不自動補「是 / 否」");
            assertEquals(2, question.getOptions().get(0).getSortOrder());
        }

        @Test
        @DisplayName("建立時題目為 null 或空清單：建立沒有題目之問卷；文字題沒有選項")
        void create_WithoutQuestions() {
            surveyService.createSurvey(SurveyCreateRequest.builder().title("空問卷").questions(null).build(), "owner");
            surveyService.createSurvey(SurveyCreateRequest.builder().title("文字題").questions(List.of(
                    QuestionDto.builder().type(QuestionType.TEXT).questionText("意見").options(null).build())).build(), "owner");

            ArgumentCaptor<Survey> saved = ArgumentCaptor.forClass(Survey.class);
            verify(surveyRepository, times(2)).save(saved.capture());
            assertTrue(saved.getAllValues().get(0).getQuestions().isEmpty());
            assertTrue(saved.getAllValues().get(1).getQuestions().get(0).getOptions().isEmpty());
        }

        @Test
        @DisplayName("舊資料中沒有選項之是非題：回應時自動補上「是 / 否」，讓前端能正常顯示")
        void detail_LegacyBooleanQuestion() {
            survey.replaceQuestions(List.of(SurveyQuestion.builder().id(1L).type(QuestionType.BOOLEAN).questionText("Q").build()));

            List<OptionDto> options = surveyService.getSurveyDetail(100L, "owner").getQuestions().get(0).getOptions();

            assertEquals(List.of("是", "否"), options.stream().map(OptionDto::getOptionText).toList());
        }

        @Test
        @DisplayName("只設定開始或只設定截止時間：不檢核先後順序")
        void dateRange_OnlyOneSide() {
            surveyService.replaceSurveySettings(100L, SurveySettingsRequest.builder().startDate(now).isOpen(true).isPublicToLobby(true).build(), "owner");
            surveyService.replaceSurveySettings(100L, SurveySettingsRequest.builder().endDate(now).isOpen(true).isPublicToLobby(true).build(), "owner");

            assertEquals(now, survey.getEndDate());
        }

        @Test
        @DisplayName("查詢個人清單但帳號不存在、查詢詳情但問卷不存在：拋出 IllegalArgumentException")
        void notFoundCases() {
            when(userRepository.findByUsernameNative("ghost")).thenReturn(Optional.empty());
            when(surveyRepository.findByIdAndDeletedAtIsNullNative(404L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> surveyService.getUserSurveys("ghost", PageRequest.of(0, 10)));
            assertThrows(IllegalArgumentException.class, () -> surveyService.getSurveyDetail(404L, "owner"));
        }
    }
}
