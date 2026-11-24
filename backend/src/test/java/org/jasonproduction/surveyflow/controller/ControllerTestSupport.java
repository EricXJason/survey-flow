/**
 * ============================================================================
 * 檔案名稱: ControllerTestSupport.java
 * 測試對象: (共用工具) 供各 Controller 測試建立 MockMvc 與模擬登入者
 * 說明: 使用 MockMvc standalone 模式，只載入受測 Controller 與 GlobalExceptionHandler，不啟動 Spring 容器、
 *       不經過 Security 過濾鏈。因此這裡驗證的是「HTTP 路由、參數驗證、回應格式、Cookie」，
 *       URL 層級之授權規則 (哪些路徑要登入) 由 SecurityConfig 負責，不在 Controller 測試範圍。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jasonproduction.surveyflow.common.exception.GlobalExceptionHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

final class ControllerTestSupport {

    /** 與正式環境相同：支援 LocalDateTime，並以 ISO 字串輸出日期 */
    // [Why]: 註冊 Spring 提供之 ProblemDetailJacksonMixin，錯誤回應之擴充欄位 (code、errors、traceId) 才會攤平在最外層，與正式環境一致
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 工具類別，禁止實體化
     */
    private ControllerTestSupport() {
    }

    /**
     * 建立只包含指定 Controller 之 MockMvc
     * @param controller 受測 Controller
     * @return MockMvc (掛載全域例外處理器、分頁參數解析、@AuthenticationPrincipal 解析)
     */
    static MockMvc mockMvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(), new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /**
     * 模擬已通過 JwtAuthenticationFilter 之登入者 (寫入 SecurityContext)
     * @param username 帳號
     * @param role 角色 (如 ROLE_USER、ROLE_ADMIN)
     */
    static void loginAs(String username, String role) {
        UserDetails user = User.withUsername(username).password("n/a")
                .authorities(List.of(new SimpleGrantedAuthority(role))).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    /**
     * 清除模擬登入狀態 (每個測試後呼叫，避免影響下一個測試)
     */
    static void logout() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 將物件序列化為 JSON 請求內容
     * @param body 任意物件
     * @return JSON 字串
     * @throws JsonProcessingException 序列化失敗
     */
    static String json(Object body) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    /**
     * 建立帶分頁資訊之 Page (模擬 Repository 回傳結果)
     * [Why]: 不帶 Pageable 之 PageImpl 內含 Unpaged，Jackson 序列化時會拋出例外；真實 Repository 一律帶分頁資訊
     * @param content 本頁資料
     * @param <T> 資料型別
     * @return 第 0 頁、每頁 10 筆之 Page
     */
    static <T> Page<T> pageOf(List<T> content) {
        return new PageImpl<>(content, PageRequest.of(0, 10), content.size());
    }
}
