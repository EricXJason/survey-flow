/**
 * ============================================================================
 * 檔案名稱: AuditLogServiceTest.java
 * 測試對象: AuditLogService (寫入雜湊鏈、操作者來源、寫入失敗不拋出、查詢轉換、雜湊鏈驗證之四種結果)
 * 測試方式: Repository 以 Mock 取代、AuditHasher 用真實物件；MDC 與 SecurityContext 於測試中設定並清除。
 *           交易提交 / 回滾時機由 AuditLogIT 以真實 Spring 交易驗證
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.jasonproduction.surveyflow.common.audit.AuditHasher;
import org.jasonproduction.surveyflow.common.web.RequestTraceFilter;
import org.jasonproduction.surveyflow.dto.audit.AuditChainVerificationResponse;
import org.jasonproduction.surveyflow.dto.audit.AuditLogResponse;
import org.jasonproduction.surveyflow.entity.AuditChainHead;
import org.jasonproduction.surveyflow.entity.AuditLog;
import org.jasonproduction.surveyflow.repository.AuditChainHeadRepository;
import org.jasonproduction.surveyflow.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final String SECRET = "unit-test-audit-hmac-secret-at-least-32-bytes";

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditChainHeadRepository auditChainHeadRepository;

    private final AuditHasher hasher = new AuditHasher(SECRET);
    private AuditLogService service;

    /**
     * 建立受測服務
     */
    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository, auditChainHeadRepository, hasher);
    }

    /**
     * 清除 MDC 與登入狀態
     */
    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    /**
     * 建立鏈頂端 (以反射設定欄位，模擬資料庫讀出之狀態)
     * @param lastLogId 最後一筆 ID
     * @param lastHash 最後一筆 hash
     * @return 鏈頂端
     */
    private static AuditChainHead head(long lastLogId, String lastHash) {
        AuditChainHead head = BeanUtils.instantiateClass(AuditChainHead.class);
        ReflectionTestUtils.setField(head, "id", AuditChainHead.SINGLETON_ID);
        ReflectionTestUtils.setField(head, "lastLogId", lastLogId);
        ReflectionTestUtils.setField(head, "lastHash", lastHash);
        return head;
    }

    /**
     * 以正確 HMAC 建立一條指定長度之合法雜湊鏈
     * @param size 筆數
     * @return 依 ID 遞增之紀錄
     */
    private List<AuditLog> validChain(int size) {
        List<AuditLog> chain = new ArrayList<>();
        String prev = AuditLogService.GENESIS_HASH;
        for (int i = 1; i <= size; i++) {
            AuditLog draft = AuditLog.builder().id((long) i).occurredAt(LocalDateTime.of(2026, 11, 21, 10, 0, i))
                    .actor("user" + i).action(AuditAction.LOGIN_SUCCEEDED.name()).prevHash(prev).build();
            AuditLog entry = draft.toBuilder().hash(hasher.hash(prev, draft.hashFields())).build();
            chain.add(entry);
            prev = entry.getHash();
        }
        return chain;
    }

    @Nested
    @DisplayName("寫入")
    class Append {

        @Test
        @DisplayName("第一筆：prev_hash 為 64 個 0，hash 為 HMAC 結果；帶入 MDC 之 IP 與 traceId；鏈頂端前進到新紀錄")
        void firstEntry_LinksToGenesisAndAdvancesHead() {
            // Given
            AuditChainHead head = head(0, AuditLogService.GENESIS_HASH);
            when(auditChainHeadRepository.findHeadForUpdate()).thenReturn(Optional.of(head));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
                AuditLog entry = invocation.getArgument(0);
                ReflectionTestUtils.setField(entry, "id", 7L);
                return entry;
            });
            MDC.put(RequestTraceFilter.MDC_CLIENT_IP, "203.0.113.5");
            MDC.put(RequestTraceFilter.MDC_TRACE_ID, "trace-0001");

            // When
            service.recordCommittedEvent(new AuditEvent(AuditAction.SURVEY_DELETED, "owner", AuditEvent.TARGET_SURVEY, "5", "標題"));

            // Then
            ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(saved.capture());
            AuditLog entry = saved.getValue();
            assertEquals(AuditLogService.GENESIS_HASH, entry.getPrevHash());
            assertEquals(hasher.hash(AuditLogService.GENESIS_HASH, entry.hashFields()), entry.getHash());
            assertEquals("203.0.113.5", entry.getClientIp());
            assertEquals("trace-0001", entry.getTraceId());
            assertEquals("SURVEY_DELETED", entry.getAction());
            assertEquals(7L, head.getLastLogId());
            assertEquals(entry.getHash(), head.getLastHash());
            verify(auditChainHeadRepository).save(head);
        }

        @Test
        @DisplayName("事件未指定操作者：取 SecurityContext 之登入者；過長之說明截斷為 500 字")
        void missingActor_UsesCurrentUser() {
            when(auditChainHeadRepository.findHeadForUpdate()).thenReturn(Optional.of(head(0, AuditLogService.GENESIS_HASH)));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

            service.recordCommittedEvent(new AuditEvent(AuditAction.USER_STATUS_CHANGED, null, AuditEvent.TARGET_USER, "3", "x".repeat(600)));

            ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(saved.capture());
            assertEquals("admin", saved.getValue().getActor());
            assertEquals(500, saved.getValue().getDetail().length());
        }

        @Test
        @DisplayName("匿名且事件未指定操作者：actor 為 null")
        void anonymous_ActorIsNull() {
            when(auditChainHeadRepository.findHeadForUpdate()).thenReturn(Optional.of(head(0, AuditLogService.GENESIS_HASH)));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.recordFailureEvent(new AuditEvent(AuditAction.LOGIN_FAILED, null, null, null, null));

            ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(saved.capture());
            assertNull(saved.getValue().getActor());
        }

        @Test
        @DisplayName("寫入失敗 (缺少鏈頂端)：不拋出例外，避免已提交之業務操作變成錯誤")
        void failure_IsSwallowed() {
            when(auditChainHeadRepository.findHeadForUpdate()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> service.recordCommittedEvent(new AuditEvent(AuditAction.LOGOUT, "u", null, null, null)));
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("失敗事件判定：LOGIN_FAILED、TOKEN_REPLAY_DETECTED 為失敗事件，其餘不是")
        void failureEventFlag() {
            assertTrue(new AuditEvent(AuditAction.LOGIN_FAILED, null, null, null, null).isFailureEvent());
            assertTrue(new AuditEvent(AuditAction.TOKEN_REPLAY_DETECTED, null, null, null, null).isFailureEvent());
            assertFalse(new AuditEvent(AuditAction.LOGIN_SUCCEEDED, null, null, null, null).isFailureEvent());
        }
    }

    @Nested
    @DisplayName("查詢")
    class Search {

        @Test
        @DisplayName("條件去除空白、空字串視為不篩選；事件代碼附上繁中名稱，未知代碼以代碼本身顯示")
        void searchLogs_MapsLabels() {
            AuditLog known = validChain(1).get(0);
            AuditLog unknown = known.toBuilder().id(2L).action("LEGACY_ACTION").build();
            PageRequest pageable = PageRequest.of(0, 20);
            when(auditLogRepository.searchNative(eq("admin"), isNull(), eq(pageable))).thenReturn(new PageImpl<>(List.of(known, unknown)));

            List<AuditLogResponse> rows = service.searchLogs(" admin ", " ", pageable).getContent();

            assertEquals("登入成功", rows.get(0).getActionLabel());
            assertEquals("LEGACY_ACTION", rows.get(1).getActionLabel());
        }

        @Test
        @DisplayName("事件類型清單：涵蓋全部 AuditAction，代碼與名稱對應")
        void listActions_CoversAllActions() {
            assertEquals(AuditAction.values().length, service.listActions().size());
            assertEquals("LOGIN_FAILED", service.listActions().get(3).code());
            assertEquals("登入失敗", service.listActions().get(3).label());
        }
    }

    @Nested
    @DisplayName("完整性驗證")
    class Verify {

        /**
         * 讓 Repository 依 afterId 回傳鏈中之下一批 (每批 2 筆，驗證分批讀取)
         * @param chain 完整鏈
         * @param headEntry 鏈頂端；null 表示驗證會在中途停止、不會讀到鏈頂端 (不設定，避免多餘 Stub)
         */
        private void stubChain(List<AuditLog> chain, AuditChainHead headEntry) {
            when(auditLogRepository.findBatchAfterIdNative(anyLong(), anyInt())).thenAnswer(invocation -> {
                long afterId = invocation.getArgument(0);
                return chain.stream().filter(entry -> entry.getId() > afterId).limit(2).toList();
            });
            if (headEntry != null) {
                when(auditChainHeadRepository.findHeadNative()).thenReturn(Optional.of(headEntry));
            }
        }

        @Test
        @DisplayName("鏈完整且與鏈頂端一致：valid=true，檢查筆數正確 (跨多批)")
        void validChain_Passes() {
            List<AuditLog> chain = validChain(5);
            stubChain(chain, head(5, chain.get(4).getHash()));

            AuditChainVerificationResponse result = service.verifyChain();

            assertTrue(result.isValid());
            assertEquals(5, result.getCheckedCount());
            assertNull(result.getBrokenAtId());
        }

        @Test
        @DisplayName("尚無任何紀錄且鏈頂端為起點：valid=true")
        void emptyChain_Passes() {
            stubChain(List.of(), head(0, AuditLogService.GENESIS_HASH));

            assertTrue(service.verifyChain().isValid());
        }

        @Test
        @DisplayName("某筆內容被修改：在該筆停止並回報其 ID")
        void modifiedEntry_IsDetected() {
            List<AuditLog> chain = new ArrayList<>(validChain(3));
            chain.set(1, chain.get(1).toBuilder().actor("attacker").build());
            stubChain(chain, null);

            AuditChainVerificationResponse result = service.verifyChain();

            assertFalse(result.isValid());
            assertEquals(2L, result.getBrokenAtId());
            assertTrue(result.getMessage().contains("修改"));
        }

        @Test
        @DisplayName("中間一筆被刪除：下一筆之 prev_hash 對不上，回報下一筆 ID")
        void deletedEntry_IsDetected() {
            List<AuditLog> chain = new ArrayList<>(validChain(3));
            chain.remove(1);
            stubChain(chain, null);

            AuditChainVerificationResponse result = service.verifyChain();

            assertFalse(result.isValid());
            assertEquals(3L, result.getBrokenAtId());
        }

        @Test
        @DisplayName("尾端紀錄被刪除：最後一筆與鏈頂端不一致")
        void truncatedTail_IsDetected() {
            List<AuditLog> chain = validChain(3);
            AuditChainHead headAfterThree = head(3, chain.get(2).getHash());
            stubChain(chain.subList(0, 2), headAfterThree);

            AuditChainVerificationResponse result = service.verifyChain();

            assertFalse(result.isValid());
            assertTrue(result.getMessage().contains("尾端"));
        }
    }
}
