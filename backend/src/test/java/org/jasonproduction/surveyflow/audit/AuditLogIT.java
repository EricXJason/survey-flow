/**
 * ============================================================================
 * 檔案名稱: AuditLogIT.java
 * 測試對象: 稽核日誌端到端行為 (真實 MySQL、真實 Spring 交易與事件)：
 *           寫入時機 (成功事件提交後才寫、失敗事件回滾也寫)、IP 與 traceId 記錄、雜湊鏈驗證與竄改偵測、管理員端點權限
 * 測試方式: 整合測試；竄改測試以 JdbcTemplate 直接改資料庫，並於 finally 還原，避免影響其他測試
 * ============================================================================
 */

package org.jasonproduction.surveyflow.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIT extends IntegrationTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 建構子注入
     * @param mockMvc 經過完整過濾鏈之 MockMvc
     * @param jdbcTemplate 直接查詢 / 竄改資料庫
     * @param transactionTemplate 以程式控制交易提交或回滾
     * @param eventPublisher 直接發布稽核事件
     */
    AuditLogIT(MockMvc mockMvc, JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
               ApplicationEventPublisher eventPublisher) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 以帳密登入
     * @param username 帳號
     * @param password 密碼
     * @return 完整回應
     * @throws Exception 請求失敗
     */
    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/public/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    /**
     * 以管理員身分呼叫稽核驗證端點
     * @return 驗證結果 data 節點
     * @throws Exception 請求失敗
     */
    private JsonNode verifyAsAdmin() throws Exception {
        String token = JSON.readTree(login("admin", "Admin123!").getResponse().getContentAsString()).path("data").path("accessToken").asText();
        MvcResult result = mockMvc.perform(get("/api/admin/audit-logs/verification").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).path("data");
    }

    /**
     * 計算指定事件與對象之稽核筆數
     * @param action 事件代碼
     * @param targetId 對象 ID
     * @return 筆數
     */
    private int countLogs(AuditAction action, String targetId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = ? AND target_id = ?", Integer.class,
                action.name(), targetId);
    }

    @Test
    @DisplayName("登入成功：寫入 LOGIN_SUCCEEDED，trace_id 與回應標頭 X-Trace-Id 相同並記錄來源 IP")
    void loginSuccess_IsAuditedWithTraceId() throws Exception {
        MvcResult result = login("testuser", "User1234!");
        String traceId = result.getResponse().getHeader("X-Trace-Id");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor, action, client_ip FROM audit_logs WHERE trace_id = ?", traceId);
        assertEquals("testuser", row.get("actor"));
        assertEquals("LOGIN_SUCCEEDED", row.get("action"));
        assertEquals("127.0.0.1", row.get("client_ip"));
    }

    @Test
    @DisplayName("登入失敗：業務交易回滾，LOGIN_FAILED 仍被寫入 (失敗事件於交易結束後寫入)")
    void loginFailure_IsAuditedDespiteRollback() throws Exception {
        String traceId = login("testuser", "wrong-password").getResponse().getHeader("X-Trace-Id");

        assertEquals("LOGIN_FAILED", jdbcTemplate.queryForObject("SELECT action FROM audit_logs WHERE trace_id = ?", String.class, traceId));
    }

    @Test
    @DisplayName("成功事件所在交易回滾：不留稽核紀錄；提交時才寫入")
    void committedEvent_OnlyAfterCommit() {
        String rolledBack = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AuditEvent(AuditAction.SURVEY_DELETED, "owner", AuditEvent.TARGET_SURVEY, rolledBack, null));
            throw new IllegalStateException("模擬業務失敗");
        }));
        assertEquals(0, countLogs(AuditAction.SURVEY_DELETED, rolledBack));

        String committed = UUID.randomUUID().toString();
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new AuditEvent(AuditAction.SURVEY_DELETED, "owner", AuditEvent.TARGET_SURVEY, committed, null)));
        assertEquals(1, countLogs(AuditAction.SURVEY_DELETED, committed));
    }

    @Test
    @DisplayName("雜湊鏈：未竄改時驗證通過；直接改資料庫某筆內容後驗證失敗並指出該筆 ID")
    void tampering_IsDetected() throws Exception {
        login("testuser", "User1234!");
        JsonNode before = verifyAsAdmin();
        assertTrue(before.path("valid").asBoolean(), before.toString());

        Long targetId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM audit_logs", Long.class);
        String originalActor = jdbcTemplate.queryForObject("SELECT actor FROM audit_logs WHERE id = ?", String.class, targetId);
        try {
            jdbcTemplate.update("UPDATE audit_logs SET actor = 'attacker' WHERE id = ?", targetId);
            JsonNode after = verifyAsAdmin();
            assertFalse(after.path("valid").asBoolean());
            assertEquals(targetId.longValue(), after.path("brokenAtId").asLong());
        } finally {
            jdbcTemplate.update("UPDATE audit_logs SET actor = ? WHERE id = ?", originalActor, targetId);
        }
        assertTrue(verifyAsAdmin().path("valid").asBoolean(), "還原後應再次通過驗證");
    }

    @Test
    @DisplayName("稽核端點：一般會員 403；管理員可查詢、依事件篩選，並取得事件類型清單")
    void endpoints_AreAdminOnly() throws Exception {
        String userToken = JSON.readTree(login("testuser", "User1234!").getResponse().getContentAsString()).path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/admin/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String adminToken = JSON.readTree(login("admin", "Admin123!").getResponse().getContentAsString()).path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/admin/audit-logs").param("action", "LOGIN_SUCCEEDED").param("actor", "admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.data.content[0].actionLabel").value("登入成功"));
        mockMvc.perform(get("/api/admin/audit-logs/actions").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(AuditAction.values().length));
    }
}
