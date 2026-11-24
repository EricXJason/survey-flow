/**
 * ============================================================================
 * 檔案名稱: SurveyLifecycleIT.java
 * 測試對象: 端對端業務流程 (所有 API 經過完整過濾鏈、交易與真實 MySQL)
 * 流程: 註冊 → 從信件取得驗證碼開通 → 建立問卷 → 受訪者讀取與填答 → 建立者查看報表
 *       → 以正確版本修改設定 → 以過期版本修改 (409) → 刪除 → 受訪者再也讀不到
 * 學習重點: 這是最接近「真的有人在用」的測試，任何一層的串接錯誤 (JSON 欄位、交易、SQL、權限) 都會在此暴露
 * ============================================================================
 */

package org.jasonproduction.surveyflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurveyLifecycleIT extends IntegrationTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 種子問卷 4 (僅限連結、已關閉) 之分享代碼，與 seed/02_surveys_01_04.sql 一致 */
    private static final String SURVEY_4_SHARE_TOKEN = "F13AaTxNStY76jtM2sUK5w";

    /** 種子問卷 27 (僅限連結、開放中) 之分享代碼，與 seed/05_status_demo_25_27.sql 一致 */
    private static final String SURVEY_27_SHARE_TOKEN = "LPOQP2Z38hvCM9OFiMM84Q";

    private final MockMvc mockMvc;

    /**
     * 建構子注入
     * @param mockMvc 經過完整過濾鏈之 MockMvc
     */
    SurveyLifecycleIT(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * 送出 JSON 請求 (可選擇帶 Bearer Token)
     * @param request 請求建構器
     * @param body 請求內容 (會轉為 JSON)
     * @param token Access Token，null 表示匿名
     * @return 執行結果
     * @throws Exception 請求失敗
     */
    private ResultActions call(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    /**
     * 取出回應 JSON 之 data 節點
     * @param actions 執行結果
     * @return data 節點
     * @throws Exception 解析失敗
     */
    private JsonNode data(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("完整生命週期：註冊 → 開通 → 建立問卷 → 填答 → 報表 → 修改設定 (含版本衝突) → 刪除")
    void fullLifecycle() throws Exception {
        // 1. 註冊：帳號待驗證，寄出啟用信
        String username = uniqueName("it_flow");
        String email = username + "@it.test";
        call(post("/api/public/auth/register"), Map.of("username", username, "email", email,
                "password", "Password1!", "fullName", "流程測試"), null)
                .andExpect(status().isOk());

        // 2. 從信件中取出驗證碼並開通，直接取得 Access Token
        String code = lastSentVerificationCode();
        String token = data(call(post("/api/public/auth/verify-email"), Map.of("email", email, "code", code), null)
                .andExpect(status().isOk())).path("accessToken").asText();

        // 3. 建立問卷：單選題 (2 個選項) + 評分題
        JsonNode created = data(call(post("/api/user/surveys"), Map.of(
                "title", "端對端測試問卷",
                "questions", List.of(
                        Map.of("questionCode", "Q1", "type", "SINGLE", "questionText", "最常使用的功能？", "sortOrder", 1,
                                "options", List.of(Map.of("optionText", "設計器", "sortOrder", 1), Map.of("optionText", "報表", "sortOrder", 2))),
                        Map.of("questionCode", "Q2", "type", "RATING", "questionText", "整體滿意度", "sortOrder", 2,
                                "minRating", 1, "maxRating", 5))), token)
                .andExpect(status().isOk()));
        long surveyId = created.path("id").asLong();
        assertEquals(0, created.path("version").asInt());
        assertTrue(created.path("shareToken").asText().matches("[A-Za-z0-9_-]{22}"), "新建問卷自動產生 22 字元分享代碼並回傳給建立者");

        // 4. 受訪者匿名讀取 (瀏覽數 +1) 並填答
        JsonNode publicSurvey = data(call(get("/api/public/surveys/" + surveyId), null, null).andExpect(status().isOk()));
        assertEquals(1, publicSurvey.path("viewCount").asInt());
        long singleQuestionId = publicSurvey.path("questions").get(0).path("id").asLong();
        long firstOptionId = publicSurvey.path("questions").get(0).path("options").get(0).path("id").asLong();
        long ratingQuestionId = publicSurvey.path("questions").get(1).path("id").asLong();
        call(post("/api/public/submissions"), Map.of("surveyId", surveyId, "durationSeconds", 42, "answers", List.of(
                Map.of("questionId", singleQuestionId, "selectedOptionId", firstOptionId),
                Map.of("questionId", ratingQuestionId, "ratingValue", 5))), null)
                .andExpect(status().isOk());

        // 5. 建立者查看報表：1 份回收、單選第一個選項 100%、評分平均 5
        JsonNode report = data(call(get("/api/analytics/surveys/" + surveyId), null, token).andExpect(status().isOk()));
        assertEquals(1, report.path("kpi").path("totalResponses").asInt());
        assertEquals(100.0, report.path("questionStats").get(0).path("optionsList").get(0).path("percentage").asDouble());
        assertEquals(5.0, report.path("questionStats").get(1).path("averageRating").asDouble());

        // 6. 以正確版本 (0) 修改設定成功，版本變為 1
        JsonNode updated = data(call(put("/api/user/surveys/" + surveyId + "/settings"),
                Map.of("isOpen", true, "isPublicToLobby", false, "version", 0), token).andExpect(status().isOk()));
        assertEquals(1, updated.path("version").asInt());

        // 7. 再以過期版本 (0) 修改 → 409，提示重新整理
        call(put("/api/user/surveys/" + surveyId + "/settings"),
                Map.of("isOpen", false, "isPublicToLobby", false, "version", 0), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("此問卷已被其他人修改，請重新整理後再試"));

        // 8. 刪除後受訪者讀不到
        call(delete("/api/user/surveys/" + surveyId), null, token).andExpect(status().isOk());
        call(get("/api/public/surveys/" + surveyId), null, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("他人問卷：一般會員無法查看報表 (403)、無法刪除 (403)")
    void otherUsersSurvey_IsProtected() throws Exception {
        // Given：testuser 登入，嘗試操作屬於 academic_chen 之種子問卷 5
        String token = data(call(post("/api/public/auth/login"), Map.of("username", "testuser", "password", "User1234!"), null))
                .path("accessToken").asText();

        // Then
        call(get("/api/analytics/surveys/5"), null, token).andExpect(status().isForbidden());
        call(delete("/api/user/surveys/5"), null, token).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("已關閉之種子問卷 4：憑分享代碼讀取與送出皆回 400 並說明關閉原因")
    void closedSurvey_RejectsRespondents() throws Exception {
        call(get("/api/public/surveys/shared/" + SURVEY_4_SHARE_TOKEN), null, null).andExpect(status().isBadRequest());
        call(post("/api/public/submissions"), Map.of("surveyId", 4, "shareToken", SURVEY_4_SHARE_TOKEN, "answers", List.of()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("此問卷目前尚未開放填答，或已由發起人手動關閉"));
    }

    @Test
    @DisplayName("僅限連結之種子問卷 27：以 ID 讀取或送出一律回「問卷不存在或已下架」；憑分享代碼可讀取並送出")
    void linkOnlySurvey_RequiresShareToken() throws Exception {
        // 以流水號 ID 猜測：讀取與送出都被擋下，訊息不透露問卷存在
        call(get("/api/public/surveys/27"), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("問卷不存在或已下架"));
        call(post("/api/public/submissions"), Map.of("surveyId", 27, "answers", List.of()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("問卷不存在或已下架"));
        // 分享代碼區分大小寫
        call(get("/api/public/surveys/shared/" + SURVEY_27_SHARE_TOKEN.toLowerCase()), null, null).andExpect(status().isBadRequest());

        // 憑正確代碼：可讀取 (回應不附代碼) 並送出
        JsonNode shared = data(call(get("/api/public/surveys/shared/" + SURVEY_27_SHARE_TOKEN), null, null).andExpect(status().isOk()));
        assertEquals(27, shared.path("id").asLong());
        assertTrue(shared.path("shareToken").isNull() || shared.path("shareToken").isMissingNode());
        call(post("/api/public/submissions"), Map.of("surveyId", 27, "shareToken", SURVEY_27_SHARE_TOKEN, "answers", List.of()), null)
                .andExpect(status().isOk());
    }
}
