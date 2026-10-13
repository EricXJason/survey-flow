/**
 * ============================================================================
 * 檔案名稱: SurveyBasicInfoConfig.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL survey_basic_info_configs 資料表，紀錄該問卷所需收集之受訪者輪廓欄位
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: Survey
 *   - 下游消費: SurveyDesignComponent, SurveyRunnerComponent
 * ============================================================================
 */

package com.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "survey_basic_info_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyBasicInfoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false, unique = true)
    private Survey survey;

    @Column(name = "require_name", nullable = false)
    @Builder.Default
    private Boolean requireName = true;

    @Column(name = "require_gender", nullable = false)
    @Builder.Default
    private Boolean requireGender = true;

    @Column(name = "require_age", nullable = false)
    @Builder.Default
    private Boolean requireAge = true;

    @Column(name = "require_phone", nullable = false)
    @Builder.Default
    private Boolean requirePhone = false;

    @Column(name = "require_email", nullable = false)
    @Builder.Default
    private Boolean requireEmail = false;

    @Column(name = "require_industry", nullable = false)
    @Builder.Default
    private Boolean requireIndustry = false;

    @Column(name = "require_residence", nullable = false)
    @Builder.Default
    private Boolean requireResidence = false;
}
