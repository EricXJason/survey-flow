/**
 * ============================================================================
 * 檔案名稱: SurveyQuestion.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL survey_questions 資料表，管理單一問卷題目內容、題型與排序
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: Survey
 *   - 下游消費: SurveyOption, QuestionDto
 * ============================================================================
 */

package com.surveyflow.entity;

import com.surveyflow.common.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "survey_questions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "question_code", length = 50)
    private String questionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    @Column(name = "min_rating")
    @Builder.Default
    private Integer minRating = 1;

    @Column(name = "max_rating")
    @Builder.Default
    private Integer maxRating = 5;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<SurveyOption> options = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
