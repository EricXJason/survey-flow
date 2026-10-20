/**
 * ============================================================================
 * 檔案名稱: SubmissionBasicInfo.java
 * 所屬模組: Domain Entity 實體層
 * 責任描述: 對映 MySQL submission_basic_infos 資料表，記錄填答者個人資料輪廓
 * 架構分層: Persistence / Entity Layer
 * 聯動關係:
 *   - 上游依賴: Submission
 *   - 下游消費: AnalyticsService 受訪者畫像統計 (年齡層金字塔、性別比、產業分佈)
 * ============================================================================
 */

package com.surveyflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "submission_basic_infos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionBasicInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    @Column(length = 50)
    private String name;

    @Column(length = 10)
    private String gender;

    @Column(name = "age_group", length = 30)
    private String ageGroup;

    @Column(length = 20)
    private String birthday;

    @Column(name = "national_id", length = 20)
    private String nationalId;

    @Column(length = 20)
    private String phone;

    @Column(name = "home_phone", length = 30)
    private String homePhone;

    @Column(length = 100)
    private String email;

    @Column(length = 50)
    private String industry;

    @Column(name = "job_title", length = 50)
    private String jobTitle;

    @Column(length = 50)
    private String department;

    @Column(name = "employee_id", length = 50)
    private String employeeId;

    @Column(name = "academic_major", length = 50)
    private String academicMajor;

    @Column(name = "class_name", length = 50)
    private String className;

    @Column(name = "student_id", length = 50)
    private String studentId;

    @Column
    private Double height;

    @Column
    private Double weight;

    @Column(length = 50)
    private String residence;

    @Column(name = "registered_address", length = 200)
    private String registeredAddress;
}
