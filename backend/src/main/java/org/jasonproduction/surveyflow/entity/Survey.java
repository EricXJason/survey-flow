/**
 * ============================================================================
 * 檔案名稱: Survey.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL surveys 資料表，代表問卷專案核心領域模型
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: surveys 資料表 (database/schema.sql)、User (建立者)
 *   - 下游消費: SurveyService, SubmissionService, AnalyticsService
 * 邊界處理: 不開放 Setter；開放時窗檢核集中於 ensureAcceptingSubmissions，供瀏覽與提交共用；
 *           分享代碼 (share_token) 於首次寫入時以 SecureRandom 產生，未公開於大廳之問卷只能憑代碼存取 (ensureReachableBy)；
 *           以 @Version 樂觀鎖防止併發編輯互相覆蓋，瀏覽數則由 SurveyRepository 原子 UPDATE 遞增 (不觸發版本號)
 * ============================================================================
 */

package org.jasonproduction.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Entity
@Table(name = "surveys")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Survey {

    /** 對受訪者顯示開放 / 截止時間之格式 */
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    /** 分享代碼之隨機位元組數：16 bytes = 128 bits，Base64URL 編碼後為 22 字元 */
    private static final int SHARE_TOKEN_BYTES = 16;

    /** 產生分享代碼之密碼學安全亂數源 (執行緒安全，全實體共用) */
    private static final SecureRandom SHARE_TOKEN_RANDOM = new SecureRandom();

    /** 對受訪者隱藏問卷是否存在之統一訊息 (不區分「查無此問卷」與「未公開且代碼不符」) */
    private static final String NOT_REACHABLE_MESSAGE = "問卷不存在或已下架";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String category = "綜合調查";

    @Column(name = "is_public_to_lobby", nullable = false)
    @Builder.Default
    private Boolean isPublicToLobby = true;

    @Column(name = "is_open", nullable = false)
    @Builder.Default
    private Boolean isOpen = true;

    @Column(name = "cover_image_url", columnDefinition = "LONGTEXT")
    private String coverImageUrl;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * 分享代碼 (專屬填答連結中的 ?share= 參數)
     * [Why]: 以流水號 id 當連結時，任何人改網址數字就能看到未公開於大廳的問卷；
     *        改用 128 bits 隨機代碼，無法由 id 推算也無法窮舉。
     */
    @Column(name = "share_token", nullable = false, unique = true, updatable = false, length = 22)
    private String shareToken;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    /**
     * 樂觀鎖版本號
     * [Why]: 兩位管理者同時編輯同一份問卷時，Hibernate 會以 UPDATE ... WHERE version = ? 寫入，
     *        後送出者因版本已被改變而更新 0 筆，拋出 OptimisticLockException (回應 409)，避免覆蓋他人修改。
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToOne(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private SurveyBasicInfoConfig basicInfoConfig;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<SurveyQuestion> questions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 首次寫入資料庫前補上分享代碼 (由 JPA 於 INSERT 前自動呼叫；已有代碼者保留不變)
     */
    @PrePersist
    void assignShareTokenIfAbsent() {
        if (shareToken == null) {
            byte[] randomBytes = new byte[SHARE_TOKEN_BYTES];
            SHARE_TOKEN_RANDOM.nextBytes(randomBytes);
            shareToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
    }

    /**
     * 檢核受訪者能否以目前的進入方式存取本問卷：公開於大廳者任何人可存取；
     * 未公開者必須帶上正確的分享代碼，否則一律視為不存在
     * @param providedShareToken 受訪者連結中的分享代碼；由大廳進入時為 null
     * @throws IllegalArgumentException 未公開於大廳且代碼缺漏或不符
     */
    public void ensureReachableBy(String providedShareToken) {
        if (Boolean.TRUE.equals(isPublicToLobby)) {
            return;
        }
        // [Why]: 以 MessageDigest.isEqual 做固定時間比對，避免從回應時間差逐字猜出代碼
        boolean matches = providedShareToken != null && MessageDigest.isEqual(
                providedShareToken.getBytes(StandardCharsets.UTF_8),
                shareToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new IllegalArgumentException(NOT_REACHABLE_MESSAGE);
        }
    }

    /**
     * 判定指定會員是否為本問卷建立者
     * @param userId 會員 ID
     * @return true 表示為建立者
     */
    public boolean isOwnedBy(Long userId) {
        return creator != null && creator.getId().equals(userId);
    }

    /**
     * 檢核問卷於指定時間是否接受填答 (手動開關 + 起訖時窗)，不符合時拋出含原因之例外
     * @param now 以臺灣時區計算之當前時間
     * @throws IllegalArgumentException 問卷關閉、尚未開始或已截止
     */
    public void ensureAcceptingSubmissions(LocalDateTime now) {
        if (Boolean.FALSE.equals(isOpen)) {
            throw new IllegalArgumentException("此問卷目前尚未開放填答，或已由發起人手動關閉");
        }
        if (startDate != null && now.isBefore(startDate)) {
            throw new IllegalArgumentException("此問卷尚未到達開放填答時間，預計於 " + startDate.format(DISPLAY_FORMATTER) + " 開放填寫");
        }
        if (endDate != null && now.isAfter(endDate)) {
            throw new IllegalArgumentException("此問卷已於 " + endDate.format(DISPLAY_FORMATTER) + " 截止填答，已無法再進行填寫");
        }
    }

    /**
     * 綁定受訪者基本資料收集設定 (同步建立雙向關聯)
     * @param config 基本資料收集設定
     */
    public void assignBasicInfoConfig(SurveyBasicInfoConfig config) {
        config.bindSurvey(this);
        this.basicInfoConfig = config;
    }

    /**
     * 以新題目清單取代現有題目 (orphanRemoval 會清除被移除之舊題目)
     * @param newQuestions 新題目清單，每題需已指向本問卷
     */
    public void replaceQuestions(List<SurveyQuestion> newQuestions) {
        this.questions.clear();
        this.questions.addAll(newQuestions);
    }

    /**
     * 核對前端送來之版本號與資料庫目前版本一致 (樂觀鎖之「前端版」)
     * [Why]: @Version 只能擋住「同一瞬間」重疊之兩筆交易；使用者 A 開著編輯畫面 10 分鐘，期間 B 已存檔，
     *        A 送出時必須比對 A 當初讀到的版本，才能發現資料已過期。
     * @param expectedVersion 前端讀取問卷時取得之版本號；null 表示呼叫端未提供，略過比對
     * @throws OptimisticLockException 版本不符 (資料已被他人修改)
     */
    public void ensureVersionMatches(Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(version)) {
            throw new OptimisticLockException("此問卷已被其他人修改，請重新整理後再試");
        }
    }

    /**
     * 局部更新問卷內容，僅覆寫非 null 欄位
     * @param title 標題
     * @param description 說明
     * @param coverImageUrl 封面圖 (Base64 或 URL)
     * @param category 分類
     */
    public void updateContent(String title, String description, String coverImageUrl, String category) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (coverImageUrl != null) this.coverImageUrl = coverImageUrl;
        if (category != null) this.category = category;
    }

    /**
     * 整體覆蓋開放設定 (PUT 語意)：起訖時間允許為 null 表示不限期，開關 null 視為 false
     * @param startDate 開放時間
     * @param endDate 截止時間
     * @param open 是否開放填答
     * @param publicToLobby 是否公開於大廳
     */
    public void replaceSettings(LocalDateTime startDate, LocalDateTime endDate, Boolean open, Boolean publicToLobby) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.isOpen = Boolean.TRUE.equals(open);
        this.isPublicToLobby = Boolean.TRUE.equals(publicToLobby);
    }

    /**
     * 局部修改開放設定 (PATCH 語意)：僅覆寫非 null 欄位
     * @param startDate 開放時間
     * @param endDate 截止時間
     * @param open 是否開放填答
     * @param publicToLobby 是否公開於大廳
     */
    public void patchSettings(LocalDateTime startDate, LocalDateTime endDate, Boolean open, Boolean publicToLobby) {
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (open != null) this.isOpen = open;
        if (publicToLobby != null) this.isPublicToLobby = publicToLobby;
    }

    /**
     * 軟刪除問卷
     * @param deletedTime 刪除時間戳記
     */
    public void softDelete(LocalDateTime deletedTime) {
        this.deletedAt = deletedTime;
    }
}
