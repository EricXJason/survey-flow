/**
 * ============================================================================
 * 檔案名稱: Survey.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL surveys 資料表，代表問卷專案核心領域模型
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRepository, User
 *   - 下游消費: SurveyService, SurveyController, SurveyDetailDto
 * ============================================================================
 */

package com.surveyflow.entity;

import com.surveyflow.common.enums.SurveyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "surveys")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Survey {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SurveyStatus status = SurveyStatus.PUBLISHED;

    @Column(name = "cover_image_url", columnDefinition = "LONGTEXT")
    private String coverImageUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

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
}
