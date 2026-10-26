# 06. API 與通訊協定規格書 (API & Communication Specifications)

> **通訊協定**: RESTful HTTPS API / JSON Payload (Spring Boot 4.1.1 Web API)  
> **更新日期**: 2026-10-22  

## 0. HTTP 通訊方法與語意標準 (HTTP Method & RESTful Semantics)

全系統 API 交互嚴格遵循 RFC 7231 與 RFC 5789 國際標準 RESTful 語意，依據業務意圖精準調度 HTTP 動詞：

| HTTP 動詞 | 語意責任與使用時機 (RFC 標準) | 專案典型實作端點 (SSOT) |
| :--- | :--- | :--- |
| **`GET`** | 資源讀取與檢索（等冪、安全無副作用） | `GET /api/public/surveys/lobby`、`GET /api/analytics/surveys/{id}` |
| **`POST`** | 資源新建、身分驗證核驗與高階流程觸發 | `POST /api/public/auth/login`、`POST /api/user/surveys`、`POST /api/public/submissions` |
| **`PUT`** | 資源完整替換或特定子資源狀態覆蓋更新（等冪） | `PUT /api/user/surveys/{id}` (整份問卷結構替換)、`PUT /api/user/profile` |
| **`PATCH` / 子資源 `PUT`** | 局部狀態修改（部分屬性更新） | `PUT /api/user/surveys/{id}/settings` (開關時限)、`PUT /api/admin/users/{id}/status` (狀態切換) |
| **`DELETE`** | 資源刪除或邏輯軟刪除（等冪） | `DELETE /api/user/surveys/{id}`、`DELETE /api/admin/users/{id}` |

> 📌 **語意邊界規範**: 完整實體更新使用 `PUT`；局部狀態開關（如凍結/解凍、開放/關閉）以特定子資源端點（如 `.../status`、`.../settings`）之 `PUT` 或標準 `PATCH` 承載，前後端維持 100% 契約一致。

---

## 1. 身分驗證與帳號管理端點規格 (Auth & Account Endpoints)

### 1.1 使用者登入 (User Login)
- **端點路徑**: `POST /api/public/auth/login`
- **請求載荷 (Request Body)**:
  ```json
  {
    "username": "user46972",
    "password": "User1234!"
  }
  ```
- **成功回應 (Response 200)**:
  - **HTTP Headers**: `Set-Cookie: survey_refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/api/public/auth`
  - **Body**:
    ```json
    {
      "status": 200,
      "success": true,
      "message": "會員登入成功",
      "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
        "tokenType": "Bearer",
        "expiresIn": 900,
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
        "user": {
          "id": 1,
          "username": "user46972",
          "email": "user46972@gmail.com",
          "fullName": "許哲誠",
          "role": "RESEARCHER"
        }
      },
      "timestamp": "2026-10-22T12:00:00"
    }
    ```
- **錯誤回應**:
  - `403 Forbidden` (`ACCOUNT_NOT_VERIFIED`): 帳號存在但尚未通過電子郵件驗證。

### 1.2 會員註冊 (User Registration)
- **端點路徑**: `POST /api/public/auth/register`
- **業務規則**: 若同帳號或信箱處於 `PENDING_VERIFICATION`，系統自動允許表單覆蓋更新並重發啟用信；若已為 `ACTIVE` 則回傳衝突錯誤。
- **請求載荷 (Request Body)**:
  ```json
  {
    "username": "user46972",
    "password": "User1234!",
    "email": "user46972@gmail.com",
    "fullName": "許哲誠",
    "phone": "0912345678",
    "role": "RESEARCHER"
  }
  ```

### 1.3 會員信箱核驗開通 (Verify Email)
- **端點路徑**: `POST /api/public/auth/verify-email`
- **請求載荷 (Request Body)**:
  ```json
  {
    "email": "user46972@gmail.com",
    "code": "316713"
  }
  ```
- **成功回應 (Response 200)**: 帳號狀態切換為 `ACTIVE`，同時自動簽發並回傳雙 Token 與使用者資料。

### 1.4 補發註冊啟用驗證信 (Resend Verification)
- **端點路徑**: `POST /api/public/auth/resend-verification`
- **請求載荷 (Request Body)**:
  ```json
  {
    "email": "user46972@gmail.com"
  }
  ```

### 1.5 雙 Token 輪替刷新 (Refresh Tokens)
- **端點路徑**: `POST /api/public/auth/refresh`
- **機制**: 支援自動從 `Cookie (survey_refresh_token)` 或 Request Body 讀取；舊 Refresh Token 之 JTI 立即進入吊銷黑名單，簽發全新雙 Token (RTR 防重放保護)。
- **請求載荷 (Request Body，選填，支援純 Cookie)**:
  ```json
  {
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
  ```

### 1.6 忘記密碼發送驗證信 (Forgot Password)
- **端點路徑**: `POST /api/public/auth/forgot-password`
- **請求載荷 (Request Body)**:
  ```json
  {
    "email": "user46972@gmail.com"
  }
  ```

### 1.7 重設會員密碼 (Reset Password)
- **端點路徑**: `POST /api/public/auth/reset-password`
- **請求載荷 (Request Body)**:
  ```json
  {
    "email": "user46972@gmail.com",
    "code": "316713",
    "newPassword": "NewPassword123!"
  }
  ```

### 1.8 會員安全登出 (Logout)
- **端點路徑**: `POST /api/public/auth/logout`
- **機制**: 提取當前 Access Token 與 Refresh Token 之 JTI 標記為吊銷，清除瀏覽器端 HttpOnly Cookie。

### 1.9 會員個人資料修改 (Update Profile)
- **端點路徑**: `PUT /api/account/profile`
- **安全要求**: 需要有效 Bearer Access Token 標頭
- **請求載荷 (Request Body)**:
  ```json
  {
    "name": "許哲誠",
    "phone": "0912345678",
    "gender": "male",
    "birthday": "1995-10-20"
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
- **端點路徑**: `GET /api/analytics/surveys/{surveyId}`
- **安全與權限控制 (Spring Security)**:
  - 角色要求：`@PreAuthorize("hasRole('ADMIN') or @surveySecurity.isAuthor(#surveyId, authentication.name)")`
  - 規則：系統管理員可檢視全站問卷；一般會員僅限擁有者 (Author) 存取；未授權回傳 `403 Forbidden`。
- **Java Spring Boot 控制器契約 (Java Controller Contract)**:
  ```java
  @GetMapping("/surveys/{surveyId}")
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

