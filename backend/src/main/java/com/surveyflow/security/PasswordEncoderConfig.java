/**
 * ============================================================================
 * 檔案名稱: PasswordEncoderConfig.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 配置全域密碼雜湊器，採用最高資安標準 Argon2id 加密演算法
 * 架構分層: Security / Configuration Layer
 * 聯動關係:
 *   - 上游依賴: Spring Security Crypto, BouncyCastle
 *   - 下游消費: AuthService (註冊雜湊與登入校驗)
 * 邊界處理: 配置合理記憶體大小與迭代次數，兼顧抗 GPU 暴力破解與伺服器效能
 * ============================================================================
 */

package com.surveyflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * 配置 Argon2id 密碼編碼器
     * 參數說明:
     * - saltLength: 16 bytes
     * - hashLength: 32 bytes
     * - parallelism: 1 thread
     * - memory: 4096 KB (4MB)
     * - iterations: 3 passes
     * @return PasswordEncoder Bean 實例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
    }
}
