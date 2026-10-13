/**
 * ============================================================================
 * 檔案名稱: Submission.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL submissions 資料表，紀錄受訪者填答主事件與填答耗時
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: Survey
 *   - 下游消費: SubmissionBasicInfo, SubmissionAnswer, AnalyticsService
 * ============================================================================
 */

package com.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    @OneToOne(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private SubmissionBasicInfo basicInfo;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubmissionAnswer> answers = new ArrayList<>();
}
