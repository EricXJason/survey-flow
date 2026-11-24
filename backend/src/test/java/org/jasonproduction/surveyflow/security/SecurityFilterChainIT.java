/**
 * ============================================================================
 * 檔案名稱: SecurityFilterChainIT.java
 * 測試對象: 完整 Security 過濾鏈 (CORS → JwtAuthenticationFilter → 授權規則 → Controller)，以真實 HTTP 流程驗證
 * 測試方式: 整合測試；MockMvc 經過 SecurityConfig 設定之所有過濾器，Token 由真實登入流程取得
 * 學習重點: 單元測試已分別驗證各元件；本類別驗證「組在一起」時，401 / 403 / 429、Cookie 換發、
 *           登出、停權、重放偵測、CORS 是否如設計運作。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.entity.Survey;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.integration.IntegrationTestSupport;
import org.jasonproduction.surveyflow.integration.TestUserFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFilterChainIT extends IntegrationTestSupport {

    /** 解析 JSON 回應用 (測試內自行建立即可) */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mockMvc;
    private final TestUserFactory factory;

    /**
     * 建構子注入
     * @param mockMvc 經過完整過濾鏈之 MockMvc
     * @param factory 建立測試會員與問卷
     */
    SecurityFilterChainIT(MockMvc mockMvc, TestUserFactory factory) {
        this.mockMvc = mockMvc;
        this.factory = factory;
    }

    /**
     * 以帳密呼叫登入 API
     * @param username 帳號
     * @param password 密碼
     * @return 完整回應 (含 Set-Cookie 與 Body)
     * @throws Exception 請求失敗
     */
    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/public/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    /**
     * 從登入 / 換發回應中取出 Access Token
     * @param result 回應
     * @return Access Token
     * @throws Exception JSON 解析失敗
     */
    private String accessTokenOf(MvcResult result) throws Exception {
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    /**
     * 從回應之 Set-Cookie 取出 Refresh Token Cookie
     * @param result 回應
     * @return refreshToken Cookie
     */
    private Cookie refreshCookieOf(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String value = setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));
        return new Cookie("refreshToken", value);
    }

    /**
     * 以 Bearer Token 呼叫個人資料 API 並回傳狀態碼
     * @param accessToken Access Token
     * @return HTTP 狀態碼
     * @throws Exception 請求失敗
     */
    private int profileStatus(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();
    }

    @Nested
    @DisplayName("授權規則")
    class Authorization {

        @Test
        @DisplayName("未帶 Token 存取會員 API：401 Problem Details (application/problem+json、code=UNAUTHENTICATED、traceId 與回應標頭一致)")
        void anonymous_Gets401ProblemDetails() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/user/profile"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.type").value("urn:surveyflow:problem:unauthenticated"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.instance").value("/api/user/profile"))
                    .andReturn();
            String traceId = result.getResponse().getHeader("X-Trace-Id");
            assertEquals(traceId, JSON.readTree(result.getResponse().getContentAsString()).path("traceId").asText());
        }

        @Test
        @DisplayName("一般會員存取管理員 API：URL 規則擋下回 403 Problem Details (code=ACCESS_DENIED)")
        void user_GetsAdmin403ProblemDetails() throws Exception {
            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + accessTokenOf(login("testuser", "User1234!"))))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("公開端點與健康檢查：不需 Token")
        void publicEndpoints_AreOpen() throws Exception {
            mockMvc.perform(get("/api/public/surveys/lobby")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("種子帳號登入後可存取會員 API；一般會員存取管理員 API 得到 403；管理員可存取")
        void roleBasedAccess() throws Exception {
            String userToken = accessTokenOf(login("testuser", "User1234!"));
            String adminToken = accessTokenOf(login("admin", "Admin123!"));

            mockMvc.perform(get("/api/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("testuser"));
            mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("竄改過之 Token：驗簽失敗，視同未登入 (401)")
        void tamperedToken_Gets401() throws Exception {
            String token = accessTokenOf(login("testuser", "User1234!"));
            String tampered = token.substring(0, token.length() - 4) + "abcd";

            mockMvc.perform(get("/api/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("預覽問卷：匿名 403；建立者帶 Token 可預覽")
        void preview_RequiresOwner() throws Exception {
            User owner = factory.createUser(uniqueName("it_preview"), RoleType.ROLE_USER);
            Survey survey = factory.createOpenSurvey(owner);
            String token = accessTokenOf(login(owner.getUsername(), TestUserFactory.PASSWORD));

            mockMvc.perform(get("/api/public/surveys/" + survey.getId()).param("preview", "true"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/public/surveys/" + survey.getId()).param("preview", "true")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Token 生命週期")
    class TokenLifecycle {

        @Test
        @DisplayName("以 Refresh Cookie 換發：取得新 Access Token 可正常使用")
        void refreshWithCookie() throws Exception {
            User user = factory.createUser(uniqueName("it_refresh"), RoleType.ROLE_USER);
            MvcResult loginResult = login(user.getUsername(), TestUserFactory.PASSWORD);

            MvcResult refreshed = mockMvc.perform(post("/api/public/auth/refresh").cookie(refreshCookieOf(loginResult)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andReturn();

            assertEquals(200, profileStatus(accessTokenOf(refreshed)));
        }

        @Test
        @DisplayName("重放偵測：同一顆 Refresh Token 用第二次 → 401，且該使用者先前取得之 Token 全部失效")
        void replayedRefreshToken_RevokesEverything() throws Exception {
            // Given：登入取得 Token，並正常換發一次
            User user = factory.createUser(uniqueName("it_replay"), RoleType.ROLE_USER);
            MvcResult loginResult = login(user.getUsername(), TestUserFactory.PASSWORD);
            Cookie original = refreshCookieOf(loginResult);
            MvcResult firstRefresh = mockMvc.perform(post("/api/public/auth/refresh").cookie(original)).andReturn();
            String newAccessToken = accessTokenOf(firstRefresh);
            Thread.sleep(1100); // [Why]: 讓作廢時間點晚於已簽發 Token 之 iat (JWT 時間精確到秒)

            // When：攻擊者拿已用過的舊 Refresh Token 再換一次
            mockMvc.perform(post("/api/public/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());

            // Then：受害者手上剛換到的新 Access Token 也被一併作廢
            assertEquals(401, profileStatus(newAccessToken));
        }

        @Test
        @DisplayName("登出後：原 Access Token 立即失效 (401)，並以 Max-Age=0 清除 Cookie")
        void logout_RevokesToken() throws Exception {
            User user = factory.createUser(uniqueName("it_logout"), RoleType.ROLE_USER);
            MvcResult loginResult = login(user.getUsername(), TestUserFactory.PASSWORD);
            String token = accessTokenOf(loginResult);

            mockMvc.perform(post("/api/user/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .cookie(refreshCookieOf(loginResult)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            assertEquals(401, profileStatus(token));
        }

        @Test
        @DisplayName("管理員停權後：該會員手上未過期之 Token 立即失效 (下一個請求 401)")
        void suspendedUser_TokenStopsWorking() throws Exception {
            User user = factory.createUser(uniqueName("it_suspend"), RoleType.ROLE_USER);
            String userToken = accessTokenOf(login(user.getUsername(), TestUserFactory.PASSWORD));
            String adminToken = accessTokenOf(login("admin", "Admin123!"));
            assertEquals(200, profileStatus(userToken));

            mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/status").param("status", "SUSPENDED")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());

            assertEquals(401, profileStatus(userToken));
        }
    }

    @Nested
    @DisplayName("暴力破解防護與 CORS")
    class BruteForceAndCors {

        @Test
        @DisplayName("連續 5 次密碼錯誤後，即使輸入正確密碼也回 429")
        void lockoutAfterFiveFailures() throws Exception {
            User user = factory.createUser(uniqueName("it_lock"), RoleType.ROLE_USER);
            for (int i = 0; i < 5; i++) {
                assertEquals(401, login(user.getUsername(), "wrong-password").getResponse().getStatus());
            }

            assertEquals(429, login(user.getUsername(), TestUserFactory.PASSWORD).getResponse().getStatus());
        }

        @Test
        @DisplayName("CORS 預檢：白名單來源允許並回傳 Allow-Origin 與 Allow-Credentials；其他來源 403")
        void corsPreflight() throws Exception {
            mockMvc.perform(options("/api/public/auth/login")
                            .header(HttpHeaders.ORIGIN, "https://survey-flow.pages.dev")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://survey-flow.pages.dev"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

            mockMvc.perform(options("/api/public/auth/login")
                            .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }
    }
}
