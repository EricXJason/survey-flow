# 04. API 與通訊協定規格書 (API & Communication Specifications)

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **通訊協定**: RESTful API / JSON Payload (目前前端以 Service Layer 封裝本地模擬與持久化)  
> **更新日期**: 2026-10-06  

## 0. HTTP 通訊方法鐵律 (HTTP Method Invariants)

全系統 API 交互永遠僅限使用以下四種標準 RESTful HTTP 動詞，**全專案嚴格禁止使用 `PATCH`、`HEAD` 等非標準或部分更新動詞**：

| HTTP 動詞 | 業務責任與使用時機 | 典型範例 |
| :--- | :--- | :--- |
| **`GET`** | 資源讀取與查詢（冪等且安全） | 查詢問卷清單、調閱個人資料、取得報表統計 |
| **`POST`** | 資源新建、身分驗證與高階流程觸發 | 會員註冊、使用者登入、提交問卷填答、換發 Token |
| **`PUT`** | 資源完整或狀態屬性更新（全量/設定覆蓋） | 修改個人資料、更新問卷設定時限、變更會員狀態 |
| **`DELETE`** | 資源刪除或邏輯軟刪除（冪等） | 刪除自有名下問卷、管理員刪除帳號 |

> ⚠️ **統一更新原則**: 所有局部更新（如會員啟用/凍結狀態切換、問卷發布時限修改）**一律統一採用 `PUT`**，徹底杜絕前端與後端對動詞語意之歧異。

---

## 1. 身分驗證與帳號管理端點規格 (Auth & Account Endpoints)

### 1.1 使用者登入 (User Login)
- **端點路徑**: `POST /api/auth/login`
- **請求載荷 (Request Body)**:
  ```typescript
  interface AccountLogInPayload {
    username: string; // 6-12 碼英數帳號
    password: string; // 8-16 碼高強度密碼
  }
  ```
- **成功回應 (Response 200)**:
  ```json
  {
    "code": 200,
    "message": "登入成功",
    "data": {
      "id": "11111",
      "access": "admin",
      "userName": "User46972A",
      "name": "系統管理員",
      "nationalId": "B234567890",
      "email": "admin@survey.com",
      "phone": "0987654321",
      "gender": "male",
      "birthday": "1990-01-01",
      "role": "researcher"
    }
  }
  ```

### 1.2 新使用者註冊 (User Registration)
- **端點路徑**: `POST /api/auth/register`
- **請求載荷 (Request Body)**:
  ```typescript
  interface AccountRegisterPayload {
    username: string;
    password: string;
    name: string;
    nationalId: string;
    email: string;
    phone: string;
    gender: string;
    birthday: string;
    role: string;
  }
  ```

### 1.3 會員個人資料修改 (Update Profile)
- **端點路徑**: `PUT /api/account/profile`
- **請求載荷 (Request Body)**:
  ```typescript
  interface AccountModifyProfilePayload {
    password?: string;
    name: string;
    nationalId: string;
    phone: string;
    gender: string;
    birthday: string;
    role: string;
  }
  ```

---

## 2. 問卷設計與發布端點規格 (Survey Endpoints)

### 2.1 建立與發布問卷 (Publish Survey)
- **端點路徑**: `POST /api/surveys`
- **請求載荷 (Request Body)**:
  ```typescript
  interface SurveyPayload {
    title: string;
    category: string;
    isPublished: boolean;
    hasTimeLimit: boolean;
    timeLimit?: {
      startDate: string;
      startTime: string;
      endDate: string;
      endTime: string;
    };
    hasDescription: boolean;
    description?: string;
    hasDescriptionImage: boolean;
    descriptionImageBase64?: string;
    basicInfoRequirements: SurveyBasicInfoRequirements;
    questions: SurveyQuestion[];
  }
  ```

### 2.2 查詢大廳公開問卷清單 (Get Lobby Surveys)
- **端點路徑**: `GET /api/surveys?published=true`
- **回應陣列項目 (SurveySummaryItem)**:
  ```typescript
  interface SurveySummaryItem {
    id: string;
    title: string;
    category: string;
    startDate: string;
    endDate: string;
    isPublished?: boolean;
  }
  ```

### 2.3 提交問卷作答成果 (Submit Survey Response)
- **端點路徑**: `POST /api/submissions`
- **請求載荷 (Request Body)**:
  ```typescript
  interface SurveySubmissionPayload {
    surveyTitle: string;
    surveyCategory: string;
    submittedAt: string;
    basicInfo: SurveyBasicInfoAnswers;
    answers: SurveyAnswerItem[];
  }
  ```

### 2.4 問卷填答數據與統計分析報表 (Get Survey Analytics Report)
- **端點路徑**: `GET /api/surveys/{surveyId}/analytics`
- **安全與權限控制 (Spring Security)**:
  - 角色要求：`@PreAuthorize("hasRole('ADMIN') or @surveySecurity.isAuthor(#surveyId, authentication.name)")`
  - 規則：系統管理員可檢視全站問卷；一般會員僅限擁有者 (Author) 存取；未授權回傳 `403 Forbidden`。
- **Java Spring Boot 控制器契約 (Java Controller Contract)**:
  ```java
  @GetMapping("/api/surveys/{surveyId}/analytics")
  @PreAuthorize("hasRole('ADMIN') or @surveySecurity.isAuthor(#surveyId, authentication.name)")
  public ResponseEntity<ApiResponse<SurveyAnalyticsReportDTO>> getSurveyAnalytics(
      @PathVariable("surveyId") String surveyId
  ) {
      SurveyAnalyticsReportDTO report = surveyAnalyticsService.generateReport(surveyId);
      return ResponseEntity.ok(ApiResponse.success(report));
  }
  ```
- **回應酬載 (Response 200)**:
  ```json
  {
    "code": 200,
    "message": "成功取得問卷統計報表",
    "data": {
      "surveyId": "sv_2026_001",
      "surveyTitle": "2026 數位產品使用滿意度與需求調查",
      "category": "顧客滿意度",
      "authorId": "00001",
      "authorName": "陳小明",
      "isPublished": true,
      "totalResponses": 128,
      "completionRate": 96.8,
      "averageDurationMinutes": 3.6,
      "lastResponseAt": "2026-10-06 10:20",
      "timeline": [
        { "date": "10-01", "submissionsCount": 19 },
        { "date": "10-02", "submissionsCount": 25 }
      ],
      "questionsAnalytics": [
        {
          "questionUid": "q-radio-1002",
          "questionId": 2,
          "type": "radio",
          "description": "您過去一週內，是否有遇到操作卡頓或連線失敗的狀況？",
          "totalAnswerCount": 128,
          "optionsStats": [
            { "text": "否，整體操作非常流暢", "count": 104, "percentage": 81.3 },
            { "text": "是，偶爾遇到連線不順或延遲", "count": 24, "percentage": 18.7 }
          ]
        }
      ]
    }
  }
  ```

