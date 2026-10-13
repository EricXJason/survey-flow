/**
 * ============================================================================
 * 檔案名稱: SubmissionAnswer.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL submission_answers 資料表，精細正規化儲存每一題的單選、多選、評分或文字回答
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: Submission, SurveyQuestion, SurveyOption
 *   - 下游消費: AnalyticsService (題型作答百分比、NPS 計算、開放式文字分析)
 * ============================================================================
 */

package com.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "submission_answers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SurveyQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private SurveyOption option;

    @Column(name = "text_answer", columnDefinition = "TEXT")
    private String textAnswer;

    @Column(name = "rating_value")
    private Integer ratingValue;
}
