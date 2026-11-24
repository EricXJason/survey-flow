/**
 * ============================================================================
 * 檔案名稱: CustomUserDetailsService.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 實作 Spring Security 原生 UserDetailsService 介面，依使用者名稱載入帳號權限
 * 架構分層: Security / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, User
 *   - 下游消費: JwtAuthenticationFilter (每個請求載入登入者並檢查帳號是否啟用)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.security;

import org.jasonproduction.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * 依據使用者名稱檢索使用者詳細資訊
     * @param username 使用者名稱
     * @return Spring Security UserDetails 實例
     * @throws UsernameNotFoundException 查無此帳號時拋出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // [Why]: 實體類別與 Spring Security 之 User 同名，以 var 承接實體避免行內完全限定類名
        var account = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new UsernameNotFoundException("查無此使用者帳號: " + username));

        // [Why]: 非 ACTIVE 帳號 (停權 / 待驗證) 標記為 disabled，JwtAuthenticationFilter 據此拒絕建立認證，
        //        使管理員停權可即時生效，無須等待 Access Token 過期
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority(account.getRole().name())))
                .disabled(!account.isActive())
                .build();
    }
}
