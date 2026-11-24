/**
 * ============================================================================
 * 檔案名稱: AuthService.java
 * 所屬模組: Service 業務邏輯層 - 認證模組 (資安 S 級)
 * 責任描述: 認證全流程 (各方法上方以「第一步、第二步…」寫出細節)：
 *   - 註冊：建立待驗證帳號 → 寫入 15 分鐘 6 碼驗證碼 → 寄信 (寄信失敗整筆交易回滾)
 *   - 信箱驗證：核對驗證碼 → 帳號轉 ACTIVE → 核銷驗證碼 → 簽發雙 Token
 *   - 登入：檢查鎖定 → 比對 Argon2id 密碼 (失敗計次) → 檢查帳號狀態 → 簽發雙 Token
 *   - 換發：驗證 Refresh Token → 輪替 (舊的進黑名單；偵測重放則作廢該使用者全部 Token) → 簽發新雙 Token
 *   - 登出：Access 與 Refresh Token 皆加入黑名單
 *   - 忘記 / 重設密碼：寄送驗證碼 → 核對後更新密碼並作廢該使用者全部 Token
 * 架構分層: Business Logic / Service Layer
 * 聯動關係:
 *   - 上游依賴: UserRepository、VerificationTokenRepository、MailService、PasswordEncoder、
 *               TokenProvider、TokenRevocationService、LoginAttemptLimiter
 *   - 下游消費: AuthController
 * 邊界處理: 帳號不存在與密碼錯誤回傳相同訊息 (不洩漏帳號是否存在)；驗證碼明碼不寫入日誌
 * ============================================================================
 */

package org.jasonproduction.surveyflow.service;

import org.jasonproduction.surveyflow.common.audit.AuditAction;
import org.jasonproduction.surveyflow.common.audit.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.jasonproduction.surveyflow.common.exception.AccountNotVerifiedException;
import org.jasonproduction.surveyflow.common.enums.RoleType;
import org.jasonproduction.surveyflow.common.enums.UserStatus;
import org.jasonproduction.surveyflow.common.enums.VerificationTokenType;
import org.jasonproduction.surveyflow.dto.auth.*;
import org.jasonproduction.surveyflow.entity.User;
import org.jasonproduction.surveyflow.entity.VerificationToken;
import org.jasonproduction.surveyflow.repository.UserRepository;
import org.jasonproduction.surveyflow.repository.VerificationTokenRepository;
import org.jasonproduction.surveyflow.security.LoginAttemptLimiter;
import org.jasonproduction.surveyflow.security.TokenProvider;
import org.jasonproduction.surveyflow.security.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final TokenRevocationService tokenRevocationService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final ApplicationEventPublisher eventPublisher;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 信箱驗證碼 (OTP) 有效分鐘數 */
    private static final long VERIFICATION_CODE_TTL_MINUTES = 15;
    /** Authorization 標頭之 Bearer 前綴與回應中之 Token 類型 */
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * 會員註冊
     * 第一步：帳號、信箱去除前後空白後查詢是否已存在。
     * 第二步：已存在且為「待驗證」→ 以新資料覆寫 (避免舊驗證碼過期後帳號卡死)；已存在且已啟用 → 400。
     * 第三步：全新帳號 → 以 Argon2id 雜湊密碼，建立 PENDING_VERIFICATION 帳號。
     * 第四步：寫入 15 分鐘有效之 6 碼驗證碼，寄出啟用信 (寄信失敗拋出 MailSendException，整筆交易回滾)。
     * @param request 註冊資料
     * @return 會員概要 (尚未簽發 Token)
     * @throws IllegalArgumentException 帳號或信箱已被啟用中會員使用
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        String trimmedUsername = request.getUsername().trim();
        String trimmedEmail = request.getEmail().trim();

        Optional<User> existingUserByUsername = userRepository.findByUsernameNative(trimmedUsername);
        Optional<User> existingUserByEmail = userRepository.findByEmailNative(trimmedEmail);

        User user;
        if (existingUserByUsername.isPresent()) {
            User existing = existingUserByUsername.get();
            if (existing.isPendingVerification()) {
                // [Why]: 先前註冊尚未開通時允許重新提交覆蓋並重發驗證信，避免舊驗證碼過期後帳號卡死
                user = existing;
                overwritePendingRegistration(user, request, trimmedUsername, trimmedEmail);
            } else {
                throw new IllegalArgumentException("此帳號已被註冊，請更換其他帳號");
            }
        } else if (existingUserByEmail.isPresent()) {
            User existing = existingUserByEmail.get();
            if (existing.isPendingVerification()) {
                // [Why]: 先前註冊尚未開通時允許重新提交覆蓋並重發驗證信，避免舊驗證碼過期後帳號卡死
                user = existing;
                overwritePendingRegistration(user, request, trimmedUsername, trimmedEmail);
            } else {
                throw new IllegalArgumentException("此電子信箱已被使用，請更換其他信箱");
            }
        } else {
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            user = User.builder()
                    .username(trimmedUsername)
                    .email(trimmedEmail)
                    .passwordHash(encodedPassword)
                    .fullName(request.getFullName().trim())
                    .phone(trimOrNull(request.getPhone()))
                    .identityCard(trimOrNull(request.getIdentityCard()))
                    .role(RoleType.ROLE_USER)
                    .status(UserStatus.PENDING_VERIFICATION)
                    .build();
        }

        user = userRepository.save(user);

        String code = issueVerificationCode(user.getEmail(), VerificationTokenType.REGISTRATION);

        // 寄送註冊啟用信
        mailService.sendRegistrationVerificationMail(user.getEmail(), user.getFullName(), code);
        log.info("新會員註冊成功並發送啟用驗證信: username={}, email={}", user.getUsername(), user.getEmail());
        eventPublisher.publishEvent(new AuditEvent(AuditAction.REGISTERED, user.getUsername(), AuditEvent.TARGET_USER, String.valueOf(user.getId()), null));

        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 信箱驗證開通
     * 第一步：以信箱 + 驗證碼 + 用途 REGISTRATION 查詢最新未使用之驗證碼，查無 → 400。
     * 第二步：驗證碼已過期或已使用 → 400。
     * 第三步：帳號轉為 ACTIVE、驗證碼核銷 (同一碼不能用第二次)。
     * 第四步：直接簽發雙 Token，使用者不必再登入一次。
     * @param request 信箱與 6 碼驗證碼
     * @return 雙 Token 與會員概要
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse verifyRegistrationEmail(VerifyEmailRequest request) {
        VerificationToken token = verificationTokenRepository.findValidTokenForUpdate(
                request.getEmail().trim(), request.getCode().trim(), VerificationTokenType.REGISTRATION.name()
        ).orElseThrow(() -> new IllegalArgumentException("驗證碼錯誤或不存在，請重新核對"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("啟用驗證碼已過期或已使用，請重新取得驗證碼");
        }

        User user = userRepository.findByEmailNative(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱註冊之會員帳號"));

        user.activate();
        userRepository.save(user);

        token.markUsed();
        verificationTokenRepository.save(token);

        log.info("會員信箱驗證成功並正式開通帳號: username={}, email={}", user.getUsername(), user.getEmail());
        eventPublisher.publishEvent(new AuditEvent(AuditAction.ACCOUNT_ACTIVATED, user.getUsername(), AuditEvent.TARGET_USER, String.valueOf(user.getId()), null));
        return createDualTokenAuthResponse(user);
    }

    /**
     * 重新寄送註冊啟用驗證信
     * @param email 目標信箱
     */
    @Transactional(rollbackFor = Exception.class)
    public void resendRegistrationCode(String email) {
        User user = userRepository.findByEmailNative(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱註冊之會員帳號"));

        if (user.isActive()) {
            throw new IllegalArgumentException("此帳號已通過驗證開通，請直接登入");
        }

        String code = issueVerificationCode(user.getEmail(), VerificationTokenType.REGISTRATION);

        mailService.sendRegistrationVerificationMail(user.getEmail(), user.getFullName(), code);
        log.info("已重新發送註冊啟用驗證信: email={}", user.getEmail());
    }

    /**
     * 帳密登入
     * 第一步：帳號鎖定中 (連續失敗達門檻) → 直接拒絕 (429)，不查資料庫。
     * 第二步：查無帳號或 Argon2id 比對失敗 → 失敗次數 +1，回傳相同的「帳號或密碼錯誤」(401)。
     * 第三步：密碼正確即清除失敗次數。
     * 第四步：帳號待驗證 → AccountNotVerifiedException (403，帶回信箱)；停權或刪除 → 400。
     * 第五步：簽發 Access Token 與 Refresh Token。
     * @param request 帳號與密碼
     * @return 雙 Token 與會員概要
     * @throws LockedException 帳號鎖定中
     * @throws BadCredentialsException 帳號或密碼錯誤
     * @throws AccountNotVerifiedException 帳號尚未完成信箱驗證
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername();
        loginAttemptLimiter.ensureNotLocked(username);

        Optional<User> account = userRepository.findByUsernameNative(username);
        if (account.isEmpty() || !passwordEncoder.matches(request.getPassword(), account.get().getPasswordHash())) {
            // [Why]: 帳號不存在與密碼錯誤一律計數並回傳相同訊息，避免洩漏帳號是否存在
            loginAttemptLimiter.recordFailure(username);
            eventPublisher.publishEvent(new AuditEvent(AuditAction.LOGIN_FAILED, username, null, null,
                    account.isEmpty() ? "帳號不存在" : "密碼錯誤"));
            throw new BadCredentialsException("帳號或密碼錯誤");
        }
        User user = account.get();
        loginAttemptLimiter.recordSuccess(username);

        if (user.isPendingVerification()) {
            throw new AccountNotVerifiedException("該帳號尚未完成電子郵件驗證，請至信箱點擊開通連結或輸入 6 位數啟用碼", user.getEmail());
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("該帳號目前處於非啟用狀態，請聯繫系統管理員");
        }

        log.info("會員登入成功: username={}", user.getUsername());
        eventPublisher.publishEvent(new AuditEvent(AuditAction.LOGIN_SUCCEEDED, user.getUsername(), AuditEvent.TARGET_USER, String.valueOf(user.getId()), null));
        return createDualTokenAuthResponse(user);
    }

    /**
     * 以 Refresh Token 換發新雙 Token (Refresh Token Rotation)
     * 第一步：Refresh Token 優先取自請求 Body，未帶時使用 HttpOnly Cookie；兩者皆無 → 401。
     * 第二步：驗簽與效期、確認類型為 refresh (防止拿 Access Token 冒充) → 失敗 401。
     * 第三步：確認帳號仍存在且為 ACTIVE。
     * 第四步：交由 TokenRevocationService 輪替：舊 Token 已被用過 (重放) 或已被整批作廢 → 401，
     *         重放時一併作廢該使用者全部 Token。
     * 第五步：輪替成功後才簽發全新 Access Token 與 Refresh Token。
     * @param request Body 中之 Refresh Token (選填)
     * @param cookieRefreshToken Cookie 中之 Refresh Token (選填)
     * @return 新雙 Token 與會員概要
     * @throws BadCredentialsException Refresh Token 缺少、無效、類型錯誤或已失效
     * @throws IllegalArgumentException 帳號不存在或非啟用狀態
     */
    // [Why]: 輪替會寫入黑名單；偵測到重放時會先整批作廢再拋出 BadCredentialsException (401)，
    //        若此例外觸發回滾，「整批作廢」會一起被撤銷，攻擊者手上的 Token 就仍然有效，因此對它不回滾
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BadCredentialsException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request, String cookieRefreshToken) {
        String tokenStr = (request != null && StringUtils.hasText(request.getRefreshToken()))
                ? request.getRefreshToken().trim()
                : cookieRefreshToken;

        if (!StringUtils.hasText(tokenStr)) {
            throw new BadCredentialsException("缺少 Refresh Token 憑證，請重新登入");
        }
        if (!tokenProvider.validateToken(tokenStr)) {
            throw new BadCredentialsException("Refresh Token 憑證無效或已過期，請重新登入");
        }
        if (!tokenProvider.isRefreshToken(tokenStr)) {
            throw new BadCredentialsException("憑證類型錯誤，非有效之 Refresh Token");
        }

        String username = tokenProvider.getUsernameFromToken(tokenStr);
        String oldJti = tokenProvider.getJtiFromToken(tokenStr);

        User user = userRepository.findByUsernameNative(username)
                .orElseThrow(() -> new IllegalArgumentException("查無所屬會員資料"));
        if (!user.isActive()) {
            throw new IllegalArgumentException("該帳號已被凍結或停用");
        }

        boolean rotated = tokenRevocationService.rotateRefreshToken(
                oldJti, username, tokenProvider.getIssuedAt(tokenStr), tokenProvider.getRemainingTtlMs(tokenStr));
        if (!rotated) {
            throw new BadCredentialsException("Refresh Token 已失效，請重新登入");
        }
        log.info("Refresh Token 輪替成功: username={}, oldJti={}", username, oldJti);
        return createDualTokenAuthResponse(user);
    }

    /**
     * 會員登出：吊銷當前 Access Token 與 Refresh Token (皆以剩餘效期作為黑名單 TTL)
     * @param username 會員帳號 (僅供稽核日誌)
     * @param bearerToken Authorization 標頭 (可含 Bearer 前綴)，可為 null
     * @param refreshToken Refresh Token Cookie 值，可為 null；無效或過期時略過
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(String username, String bearerToken, String refreshToken) {
        if (StringUtils.hasText(bearerToken)) {
            String token = bearerToken.startsWith(BEARER_PREFIX) ? bearerToken.substring(BEARER_PREFIX.length()).trim() : bearerToken.trim();
            revokeIfValid(token);
        }
        if (StringUtils.hasText(refreshToken)) {
            revokeIfValid(refreshToken.trim());
        }
        log.info("會員已主動安全登出: username={}", username);
        eventPublisher.publishEvent(new AuditEvent(AuditAction.LOGOUT, username, null, null, null));
    }


    /**
     * 忘記密碼申請：查到信箱後寫入 FORGOT_PASSWORD 用途之 6 碼驗證碼並寄出重設信
     * @param request 包含信箱之請求
     * @throws IllegalArgumentException 查無此信箱
     */
    @Transactional(rollbackFor = Exception.class)
    public void processForgotPassword(ForgetPasswordRequest request) {
        User user = userRepository.findByEmailNative(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱綁定之會員帳號，請確認輸入是否正確"));

        String code = issueVerificationCode(user.getEmail(), VerificationTokenType.FORGOT_PASSWORD);

        mailService.sendPasswordResetMail(user.getEmail(), code);
        log.info("忘記密碼驗證信已成功發送: email={}", user.getEmail());
    }

    /**
     * 處理重設新密碼：核對信箱 + 6 碼驗證碼後更新密碼雜湊、核銷驗證碼，並作廢該使用者此前簽發之全部 Token
     * @param request 包含信箱、驗證碼與新密碼之請求
     */
    @Transactional(rollbackFor = Exception.class)
    public void processResetPassword(ResetPasswordRequest request) {
        VerificationToken token = verificationTokenRepository.findValidTokenForUpdate(
                request.getEmail().trim(), request.getCode().trim(), VerificationTokenType.FORGOT_PASSWORD.name()
        ).orElseThrow(() -> new IllegalArgumentException("驗證碼錯誤或不存在，請重新核對"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("此驗證碼已過期或已使用，請重新申請忘記密碼");
        }

        User user = userRepository.findByEmailNative(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("查無此電子信箱對應之會員帳號"));

        user.changePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // [Why]: 透過忘記密碼重設代表舊密碼可能外洩，作廢此前簽發之全部 Token，強制所有裝置重新登入
        tokenRevocationService.revokeAllTokensOf(user.getUsername());

        token.markUsed();
        verificationTokenRepository.save(token);

        log.info("會員密碼已透過信箱驗證碼成功重設: username={}, email={}", user.getUsername(), user.getEmail());
        eventPublisher.publishEvent(new AuditEvent(AuditAction.PASSWORD_RESET, user.getUsername(), AuditEvent.TARGET_USER, String.valueOf(user.getId()),
                "已作廢該會員此前簽發之全部 Token"));
    }

    /**
     * 簽發 Access Token 與 Refresh Token 並組成回應 (Controller 會把 Refresh Token 移到 Cookie)
     * @param user 已通過驗證之會員
     * @return 雙 Token 與會員概要
     */
    private AuthResponse createDualTokenAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE_BEARER)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .identityCard(user.getIdentityCard())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 驗簽通過之 Token 才加入黑名單 (避免以偽造字串灌爆黑名單)
     * @param token JWT 字串
     */
    private void revokeIfValid(String token) {
        if (tokenProvider.validateToken(token)) {
            tokenRevocationService.revokeToken(tokenProvider.getJtiFromToken(token), tokenProvider.getRemainingTtlMs(token));
        }
    }

    /**
     * 產生 6 碼 OTP、寫入驗證碼資料表並回傳明碼供寄信使用 (明碼禁止寫入日誌)
     * @param email 收件信箱
     * @param tokenType 驗證碼用途
     * @return 6 碼數字驗證碼
     */
    private String issueVerificationCode(String email, VerificationTokenType tokenType) {
        String code = generateSixDigitCode();
        verificationTokenRepository.save(VerificationToken.builder()
                .email(email)
                .code(code)
                .tokenType(tokenType)
                .expiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES))
                .isUsed(false)
                .build());
        return code;
    }

    /**
     * 以新註冊表單內容覆寫尚未驗證之帳號
     * @param user 待驗證之既有帳號
     * @param request 新註冊表單
     * @param trimmedUsername 已 trim 之帳號
     * @param trimmedEmail 已 trim 之信箱
     */
    private void overwritePendingRegistration(User user, RegisterRequest request, String trimmedUsername, String trimmedEmail) {
        user.overwritePendingRegistration(
                trimmedUsername,
                trimmedEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getFullName().trim(),
                trimOrNull(request.getPhone()),
                trimOrNull(request.getIdentityCard()));
    }

    /**
     * 字串去除前後空白，null 安全
     * @param value 原始字串
     * @return 去除空白後之字串，或 null
     */
    private static String trimOrNull(String value) {
        return value != null ? value.trim() : null;
    }

    /**
     * 生成安全 6 碼數字 OTP 驗證碼
     * @return 6 碼純數字隨機字串 (000000 ~ 999999)
     */
    private String generateSixDigitCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }
}
