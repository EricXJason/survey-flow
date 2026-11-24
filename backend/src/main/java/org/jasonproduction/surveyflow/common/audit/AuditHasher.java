/**
 * ============================================================================
 * 檔案名稱: AuditHasher.java
 * 所屬模組: Common 稽核模組 (資安 S 級：稽核完整性金鑰)
 * 責任描述: 以 HMAC-SHA256 計算稽核紀錄之雜湊：輸入為前一筆 hash 與本筆各欄位，輸出 64 字元十六進位字串
 * 架構分層: Common / Audit Layer
 * 聯動關係:
 *   - 上游依賴: application.properties (app.audit.hmac-secret ← 環境變數 AUDIT_HMAC_SECRET，必填、無預設值)
 *   - 下游消費: AuditLogService (寫入時計算、驗證時重算比對)
 * 邊界處理: [Why]: 用 HMAC 而非單純 SHA-256：能直接改資料庫的人若沒有金鑰，就算重算整條鏈也算不出正確 hash，竄改必被發現。
 *           金鑰至少 32 bytes，不足時應用程式啟動即失敗；金鑰更換後舊紀錄將無法通過驗證，正式環境不可任意更換。
 *           各欄位以「長度:內容」串接，null 與空字串可區分，也無法藉由移動分隔符號偽造相同輸入。
 * ============================================================================
 */

package org.jasonproduction.surveyflow.common.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class AuditHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    /** null 欄位之代表值 (長度標記為 -1，與空字串之 0 區分) */
    private static final String NULL_FIELD = "-1:";

    private final SecretKeySpec key;

    /**
     * 建立雜湊器並檢查金鑰長度
     * @param secret HMAC 金鑰 (UTF-8 編碼後至少 32 bytes)
     * @throws IllegalStateException 金鑰長度不足
     */
    public AuditHasher(@Value("${app.audit.hmac-secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("AUDIT_HMAC_SECRET 長度不足，至少需要 " + MIN_SECRET_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(secretBytes, ALGORITHM);
    }

    /**
     * 計算 HMAC-SHA256：依序串接前一筆 hash 與各欄位 (每個欄位為「長度:內容」)
     * @param prevHash 前一筆紀錄之 hash
     * @param fields 本筆紀錄各欄位 (順序固定，寫入與驗證必須一致)
     * @return 64 字元小寫十六進位字串
     */
    public String hash(String prevHash, String... fields) {
        StringBuilder canonical = new StringBuilder(prevHash);
        for (String field : fields) {
            canonical.append('|').append(field == null ? NULL_FIELD : field.length() + ":" + field);
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 計算失敗", ex);
        }
    }
}
