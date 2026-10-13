/**
 * ============================================================================
 * 檔案名稱: SurveyOption.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL survey_options 資料表，管理單選/多選題目之可選項目與其排序
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: SurveyQuestion
 *   - 下游消費: OptionDto, SubmissionAnswer
 * ============================================================================
 */

package com.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "survey_options")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SurveyQuestion question;

    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;
}
