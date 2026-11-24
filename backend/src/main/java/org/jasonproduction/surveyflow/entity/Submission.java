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

package org.jasonproduction.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "submissions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    /**
     * 綁定受訪者基本輪廓 (同步建立雙向關聯)
     * @param info 受訪者基本資料
     */
    public void assignBasicInfo(SubmissionBasicInfo info) {
        info.bindSubmission(this);
        this.basicInfo = info;
    }

    /**
     * 以新作答明細取代現有明細
     * @param newAnswers 作答明細清單，每筆需已指向本次填答
     */
    public void replaceAnswers(List<SubmissionAnswer> newAnswers) {
        this.answers.clear();
        this.answers.addAll(newAnswers);
    }
}
