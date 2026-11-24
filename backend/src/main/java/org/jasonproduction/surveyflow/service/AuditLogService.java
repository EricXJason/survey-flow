/**
 * ============================================================================
 * 檔案名稱: AuditLogService.java
 * 所屬模組: Service 業務邏輯層 - 稽核 (資安 S 級：稽核完整性)
 * 責任描述: 接收各 Service 發布之 AuditEvent 並寫入不可竄改之稽核日誌 (HMAC 雜湊鏈)；提供管理員查詢與完整性驗證
 * 架構分層: Business / Service Layer
 * 聯動關係:
 *   - 上游依賴: AuditLogRepository、AuditChainHeadRepository、AuditHasher；
 *               MDC (traceId、clientIp，由 RequestTraceFilter 寫入)、SecurityContext (事件未指定操作者時取目前登入者)
 *   - 下游消費: AuditLogController (查詢與驗證)；Spring 事件機制 (寫入)
 * 邊界處理: 寫入時機 —
 *   - 成功事件：@TransactionalEventListener(AFTER_COMMIT)，業務交易提交後才寫；業務回滾時不留紀錄。
 *   - 失敗事件 (登入失敗、Token 重放)：AFTER_COMPLETION，不論業務交易提交或回滾都寫。
 *   - 沒有業務交易時 (fallbackExecution) 立即寫入。
 *   [Why]: 交易已結束後才寫入，必須以 REQUIRES_NEW 開新交易；寫入失敗時只記錄 ERROR 日誌而不拋出，
 *          避免已提交的業務操作在回應時變成錯誤。每筆稽核同時以 INFO 寫入結構化日誌 (含 hash)，
 *          日誌平臺保存之副本可作為資料庫外之對照，彌補「同時改資料庫與鏈頂端」之尾端刪除情境。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.jasonproduction.surveyflow.common.audit.AuditHasher;
import org.jasonproduction.surveyflow.common.web.RequestTraceFilter;
import org.jasonproduction.surveyflow.dto.audit.AuditActionOption;
import org.jasonproduction.surveyflow.dto.audit.AuditChainVerificationResponse;
import org.jasonproduction.surveyflow.dto.audit.AuditLogResponse;
import org.jasonproduction.surveyflow.entity.AuditChainHead;
import org.jasonproduction.surveyflow.entity.AuditLog;
import org.jasonproduction.surveyflow.repository.AuditChainHeadRepository;
import org.jasonproduction.surveyflow.repository.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** 雜湊鏈起點 (第一筆紀錄之 prev_hash) */
    static final String GENESIS_HASH = "0".repeat(64);

    /** 驗證時每批讀取筆數 */
    private static final int VERIFY_BATCH_SIZE = 500;

    private static final int MAX_ACTOR_LENGTH = 50;
    private static final int MAX_DETAIL_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;
    private final AuditChainHeadRepository auditChainHeadRepository;
    private final AuditHasher auditHasher;

    /**
     * 寫入成功事件：業務交易提交後 (或沒有交易時立即) 寫入
     * @param event 稽核事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true,
            condition = "!#event.isFailureEvent()")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordCommittedEvent(AuditEvent event) {
        appendSafely(event);
    }

    /**
     * 寫入失敗事件：業務交易結束後 (提交或回滾皆可，或沒有交易時立即) 寫入
     * @param event 稽核事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true,
            condition = "#event.isFailureEvent()")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailureEvent(AuditEvent event) {
        appendSafely(event);
    }

    /**
     * 列出全部事件類型 (代碼與繁中名稱)，供前端篩選選單
     * @return 事件類型清單，順序同 AuditAction 宣告順序
     */
    public List<AuditActionOption> listActions() {
        return Arrays.stream(AuditAction.values()).map(action -> new AuditActionOption(action.name(), action.label())).toList();
    }

    /**
     * 管理員條件分頁查詢稽核紀錄 (由新到舊)
     * @param actor 操作者帳號 (完全比對)，可為 null
     * @param action 事件代碼，可為 null
     * @param pageable 分頁參數
     * @return 稽核紀錄分頁
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchLogs(String actor, String action, Pageable pageable) {
        return auditLogRepository.searchNative(trimToNull(actor), trimToNull(action), pageable).map(AuditLogService::toResponse);
    }

    /**
     * 驗證整條稽核雜湊鏈
     * 第一步：依 id 遞增分批讀取，每筆檢查 prev_hash 是否等於上一筆 hash (偵測刪除、插入、調換順序)。
     * 第二步：以 HMAC 重算本筆 hash 並比對 (偵測欄位被修改)。
     * 第三步：最後一筆必須與 audit_chain_head 一致 (偵測尾端紀錄被刪除)。
     * @return 驗證結果；遇到第一個斷點即停止並回報其 ID
     */
    @Transactional(readOnly = true)
    public AuditChainVerificationResponse verifyChain() {
        String expectedPrevHash = GENESIS_HASH;
        long lastId = 0;
        long checked = 0;
        List<AuditLog> batch = auditLogRepository.findBatchAfterIdNative(0, VERIFY_BATCH_SIZE);
        while (!batch.isEmpty()) {
            for (AuditLog entry : batch) {
                checked++;
                if (!entry.getPrevHash().equals(expectedPrevHash)) {
                    return broken(checked, entry.getId(), "第 " + entry.getId() + " 筆之前一筆雜湊不相符，前方紀錄可能被刪除、插入或調換");
                }
                if (!auditHasher.hash(entry.getPrevHash(), entry.hashFields()).equals(entry.getHash())) {
                    return broken(checked, entry.getId(), "第 " + entry.getId() + " 筆內容與雜湊不相符，紀錄可能被修改");
                }
                expectedPrevHash = entry.getHash();
                lastId = entry.getId();
            }
            batch = auditLogRepository.findBatchAfterIdNative(lastId, VERIFY_BATCH_SIZE);
        }

        AuditChainHead head = auditChainHeadRepository.findHeadNative()
                .orElseThrow(() -> new IllegalStateException("缺少稽核鏈頂端 (audit_chain_head)，請以 schema.sql 重建"));
        if (head.getLastLogId() != lastId || !head.getLastHash().equals(expectedPrevHash)) {
            return broken(checked, lastId == 0 ? null : lastId, "最後一筆紀錄與鏈頂端不一致，尾端紀錄可能被刪除");
        }
        return AuditChainVerificationResponse.builder()
                .valid(true)
                .checkedCount(checked)
                .message("稽核日誌完整，共 " + checked + " 筆紀錄皆通過驗證")
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 寫入稽核紀錄；任何錯誤只記錄 ERROR 日誌 (含事件內容) 而不拋出
     * @param event 稽核事件
     */
    private void appendSafely(AuditEvent event) {
        try {
            append(event);
        } catch (RuntimeException ex) {
            log.error("稽核日誌寫入失敗: action={}, actor={}, targetType={}, targetId={}",
                    event.action(), event.actor(), event.targetType(), event.targetId(), ex);
        }
    }

    /**
     * 寫入一筆稽核紀錄並推進鏈頂端
     * 第一步：鎖定鏈頂端 (FOR UPDATE)，取得前一筆 hash。
     * 第二步：組出紀錄 (操作者、IP、traceId)，以 HMAC 計算本筆 hash 後新增。
     * 第三步：鏈頂端前進到本筆，提交時一起寫入。
     * @param event 稽核事件
     */
    private void append(AuditEvent event) {
        AuditChainHead head = auditChainHeadRepository.findHeadForUpdate()
                .orElseThrow(() -> new IllegalStateException("缺少稽核鏈頂端 (audit_chain_head)，請以 schema.sql 重建"));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        AuditLog draft = AuditLog.builder()
                .occurredAt(now)
                .actor(truncate(event.actor() != null ? event.actor() : currentUsername(), MAX_ACTOR_LENGTH))
                .action(event.action().name())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .detail(truncate(event.detail(), MAX_DETAIL_LENGTH))
                .clientIp(MDC.get(RequestTraceFilter.MDC_CLIENT_IP))
                .traceId(MDC.get(RequestTraceFilter.MDC_TRACE_ID))
                .prevHash(head.getLastHash())
                .build();
        String hash = auditHasher.hash(head.getLastHash(), draft.hashFields());
        AuditLog saved = auditLogRepository.save(draft.toBuilder().hash(hash).build());

        head.advance(saved.getId(), hash, now);
        auditChainHeadRepository.save(head);
        log.info("稽核紀錄: id={}, action={}, actor={}, targetType={}, targetId={}, hash={}",
                saved.getId(), saved.getAction(), saved.getActor(), saved.getTargetType(), saved.getTargetId(), hash);
    }

    /**
     * 取得目前登入者帳號 (匿名或未登入時為 null)
     * @return 帳號或 null
     */
    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    /**
     * 組出驗證失敗結果
     * @param checked 已檢查筆數
     * @param brokenAtId 斷點紀錄 ID，可為 null
     * @param message 原因說明
     * @return 驗證結果
     */
    private static AuditChainVerificationResponse broken(long checked, Long brokenAtId, String message) {
        log.error("稽核日誌完整性驗證失敗: brokenAtId={}, reason={}", brokenAtId, message);
        return AuditChainVerificationResponse.builder()
                .valid(false)
                .checkedCount(checked)
                .brokenAtId(brokenAtId)
                .message(message)
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 轉為回應 DTO (事件代碼附上繁中名稱；未知代碼以代碼本身顯示)
     * @param entry 稽核紀錄
     * @return 回應 DTO
     */
    private static AuditLogResponse toResponse(AuditLog entry) {
        String label = Arrays.stream(AuditAction.values())
                .filter(action -> action.name().equals(entry.getAction()))
                .map(AuditAction::label)
                .findFirst()
                .orElse(entry.getAction());
        return AuditLogResponse.builder()
                .id(entry.getId())
                .occurredAt(entry.getOccurredAt())
                .actor(entry.getActor())
                .action(entry.getAction())
                .actionLabel(label)
                .targetType(entry.getTargetType())
                .targetId(entry.getTargetId())
                .detail(entry.getDetail())
                .clientIp(entry.getClientIp())
                .traceId(entry.getTraceId())
                .hash(entry.getHash())
                .build();
    }

    /**
     * 截斷過長字串 (避免超過欄位長度而寫入失敗)
     * @param value 原始字串，可為 null
     * @param maxLength 最大長度
     * @return 截斷後字串
     */
    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * 去除空白，空字串轉為 null
     * @param value 原始字串
     * @return 去除空白後之字串，或 null
     */
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
