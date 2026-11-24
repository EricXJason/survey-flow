/**
 * ============================================================================
 * 檔案名稱: ClientIpResolverTest.java
 * 測試對象: ClientIpResolver (依可信任代理層數由 X-Forwarded-For 右側取用戶端 IP)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    /**
     * 建立指定連線來源與 X-Forwarded-For 之請求
     * @param remoteAddr TCP 連線來源
     * @param forwardedFor X-Forwarded-For 標頭，可為 null
     * @return 模擬請求
     */
    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("一層可信任代理：取最右邊一筆，最左邊偽造之值被忽略")
    void oneProxy_UsesRightmost() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertEquals("203.0.113.5", resolver.resolve(request("10.0.0.1", "6.6.6.6, 203.0.113.5")));
        assertEquals("203.0.113.5", resolver.resolve(request("10.0.0.1", "203.0.113.5")));
    }

    @Test
    @DisplayName("兩層可信任代理：由右往左數第 2 筆")
    void twoProxies_UsesSecondFromRight() {
        assertEquals("203.0.113.5", new ClientIpResolver(2).resolve(request("10.0.0.1", "6.6.6.6, 203.0.113.5, 172.16.0.9")));
    }

    @Test
    @DisplayName("標頭項目少於代理層數：取最左邊一筆")
    void fewerHopsThanProxies_UsesLeftmost() {
        assertEquals("203.0.113.5", new ClientIpResolver(3).resolve(request("10.0.0.1", "203.0.113.5, 172.16.0.9")));
    }

    @Test
    @DisplayName("代理層數為 0、沒有標頭或標頭只有空白與逗號：使用連線來源；負數視為 0")
    void noProxyOrNoHeader_UsesRemoteAddr() {
        assertEquals("10.0.0.1", new ClientIpResolver(0).resolve(request("10.0.0.1", "203.0.113.5")));
        assertEquals("10.0.0.1", new ClientIpResolver(-3).resolve(request("10.0.0.1", "203.0.113.5")));
        assertEquals("10.0.0.1", new ClientIpResolver(1).resolve(request("10.0.0.1", null)));
        assertEquals("10.0.0.1", new ClientIpResolver(1).resolve(request("10.0.0.1", " , ")));
    }

    @Test
    @DisplayName("超過 45 字元之偽造值會被截斷 (與資料庫欄位長度一致)")
    void longValue_IsTruncated() {
        assertEquals(45, new ClientIpResolver(1).resolve(request("10.0.0.1", "x".repeat(80))).length());
    }
}
