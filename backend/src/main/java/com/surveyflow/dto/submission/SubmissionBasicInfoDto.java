/**
 * ============================================================================
 * 檔案名稱: SubmissionBasicInfoDto.java
 * 所屬模組: DTO 資料傳輸層 - 填答模組
 * 責任描述: 封裝受訪者填答時填寫之個人基本輪廓資料
 * 架構分層: Presentation / DTO Layer
 * 聯動關係:
 *   - 上游依賴: SurveyRunnerComponent
 *   - 下游消費: SubmissionRequest, SubmissionService
 * ============================================================================
 */

package com.surveyflow.dto.submission;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionBasicInfoDto {

    @Size(max = 50, message = "姓名長度不得超過 50 字元")
    private String name;

    @Size(max = 10, message = "性別長度不得超過 10 字元")
    private String gender;

    @Size(max = 30, message = "年齡區間長度不得超過 30 字元")
    private String ageGroup;

    @Size(max = 20, message = "生日長度不得超過 20 字元")
    private String birthday;

    @Size(max = 20, message = "身分證字號長度不得超過 20 字元")
    private String nationalId;

    @Size(max = 20, message = "電話長度不得超過 20 字元")
    private String phone;

    @Size(max = 30, message = "市話長度不得超過 30 字元")
    private String homePhone;

    @Size(max = 100, message = "電子信箱長度不得超過 100 字元")
    private String email;

    @Size(max = 50, message = "產業別長度不得超過 50 字元")
    private String industry;

    @Size(max = 50, message = "職稱長度不得超過 50 字元")
    private String jobTitle;

    @Size(max = 50, message = "所屬單位長度不得超過 50 字元")
    private String department;

    @Size(max = 50, message = "員工編號長度不得超過 50 字元")
    private String employeeId;

    @Size(max = 50, message = "就讀科系長度不得超過 50 字元")
    private String academicMajor;

    @Size(max = 50, message = "班級名稱長度不得超過 50 字元")
    private String className;

    @Size(max = 50, message = "學號長度不得超過 50 字元")
    private String studentId;

    private Double height;

    private Double weight;

    @Size(max = 50, message = "居住地區長度不得超過 50 字元")
    private String residence;

    @Size(max = 200, message = "戶籍地址長度不得超過 200 字元")
    private String registeredAddress;
}
