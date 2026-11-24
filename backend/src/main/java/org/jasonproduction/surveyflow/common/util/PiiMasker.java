/**
 * ============================================================================
 * 檔案名稱: PiiMasker.java
 * 所屬模組: Common 共用工具
 * 責任描述: 個人可識別資料 (PII) 遮罩：手機號碼與身分證字號只保留頭尾，中間以 * 取代
 * 架構分層: Common / Utility Layer
 * 聯動關係:
 *   - 上游依賴: 無
 *   - 下游消費: UserService.getAllUsersForAdmin (管理員會員清單)
 * 邊界處理: [Why]: 管理員只需辨識「是哪個帳號」，不需要看到完整手機與身分證；遮罩在後端完成，
 *           完整值不會出現在網路回應、瀏覽器開發者工具或前端快取中。會員本人之個人資料頁仍回傳完整值。
 *           null 或空白原樣回傳 (欄位選填)；長度過短時全部遮罩，不洩漏任何字元。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.util;

public final class PiiMasker {

    private static final char MASK = '*';

    /**
     * 工具類別不允許建立實例
     */
    private PiiMasker() {
    }

    /**
     * 遮罩手機號碼：保留前 4 碼與後 3 碼，如 0912345678 → 0912***678
     * @param phone 手機號碼，可為 null
     * @return 遮罩後字串；null 或空白原樣回傳
     */
    public static String maskPhone(String phone) {
        return keepEnds(phone, 4, 3);
    }

    /**
     * 遮罩身分證字號：保留第 1 碼 (縣市字母) 與後 3 碼，如 A123456789 → A******789
     * @param identityCard 身分證字號，可為 null
     * @return 遮罩後字串；null 或空白原樣回傳
     */
    public static String maskIdentityCard(String identityCard) {
        return keepEnds(identityCard, 1, 3);
    }

    /**
     * 保留頭尾指定字元數，中間以 * 取代；字串長度不足頭尾總和 + 1 時全部遮罩
     * @param value 原始字串，可為 null
     * @param head 保留之開頭字元數
     * @param tail 保留之結尾字元數
     * @return 遮罩後字串
     */
    private static String keepEnds(String value, int head, int tail) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int length = value.length();
        if (length <= head + tail) {
            return String.valueOf(MASK).repeat(length);
        }
        return value.substring(0, head) + String.valueOf(MASK).repeat(length - head - tail) + value.substring(length - tail);
    }
}
