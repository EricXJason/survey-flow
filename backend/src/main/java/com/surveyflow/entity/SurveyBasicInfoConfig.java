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

    @Column(name = "collect_basic_info", nullable = false)
    @Builder.Default
    private Boolean collectBasicInfo = true;

    @Column(name = "require_name", nullable = false)
    @Builder.Default
    private Boolean requireName = true;

    @Column(name = "require_gender", nullable = false)
    @Builder.Default
    private Boolean requireGender = true;

    @Column(name = "require_age", nullable = false)
    @Builder.Default
    private Boolean requireAge = true;

    @Column(name = "require_birthday", nullable = false)
    @Builder.Default
    private Boolean requireBirthday = false;

    @Column(name = "require_national_id", nullable = false)
    @Builder.Default
    private Boolean requireNationalId = false;

    @Column(name = "require_phone", nullable = false)
    @Builder.Default
    private Boolean requirePhone = false;

    @Column(name = "require_home_phone", nullable = false)
    @Builder.Default
    private Boolean requireHomePhone = false;

    @Column(name = "require_email", nullable = false)
    @Builder.Default
    private Boolean requireEmail = false;

    @Column(name = "require_industry", nullable = false)
    @Builder.Default
    private Boolean requireIndustry = false;

    @Column(name = "require_job_title", nullable = false)
    @Builder.Default
    private Boolean requireJobTitle = false;

    @Column(name = "require_department", nullable = false)
    @Builder.Default
    private Boolean requireDepartment = false;

    @Column(name = "require_employee_id", nullable = false)
    @Builder.Default
    private Boolean requireEmployeeId = false;

    @Column(name = "require_academic_major", nullable = false)
    @Builder.Default
    private Boolean requireAcademicMajor = false;

    @Column(name = "require_class_name", nullable = false)
    @Builder.Default
    private Boolean requireClassName = false;

    @Column(name = "require_student_id", nullable = false)
    @Builder.Default
    private Boolean requireStudentId = false;

    @Column(name = "require_height", nullable = false)
    @Builder.Default
    private Boolean requireHeight = false;

    @Column(name = "require_weight", nullable = false)
    @Builder.Default
    private Boolean requireWeight = false;

    @Column(name = "require_residence", nullable = false)
    @Builder.Default
    private Boolean requireResidence = false;

    @Column(name = "require_registered_address", nullable = false)
    @Builder.Default
    private Boolean requireRegisteredAddress = false;
}
