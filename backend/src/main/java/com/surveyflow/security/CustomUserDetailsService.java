/**
 * ============================================================================
 * 檔案名稱: CustomUserDetailsService.java
 * 所屬模組: Security 安全認證模組
 * 責任描述: 實作 Spring Security 原生 UserDetailsService 介面，依使用者名稱載入帳號權限
 * 架構分層: Security / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository, User
 *   - 下游消費: SecurityFilterChain, DaoAuthenticationProvider
 * ============================================================================
 */

package com.surveyflow.security;

import com.surveyflow.entity.User;
import com.surveyflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new UsernameNotFoundException("查無此使用者帳號: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}
