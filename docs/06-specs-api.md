# 06. API 與通訊協定規格書 (API & Communication Specifications)

> **通訊協定**: RESTful HTTPS API / JSON Payload (Spring Boot Web MVC)  
> **SSOT**: 本文件所列路由與動詞 100% 對齊 `backend/.../controller/*.java` 與前端 `ApiClientService` 實際呼叫。

## 0. HTTP 通訊方法與語意標準 (HTTP Method & RESTful Semantics)

| HTTP 動詞 | 語意責任 (RFC 7231 / RFC 5789) | 本專案端點 |
| :--- | :--- | :--- |
| **`GET`** | 資源讀取與檢索 (含稽核鏈驗證，唯讀) | `GET /api/public/surveys/lobby`、`GET /api/analytics/surveys/{surveyId}`、`GET /api/admin/audit-logs/verification` |
| **`POST`** | 資源新建與非等冪流程 | `POST /api/public/auth/login`、`POST /api/user/surveys`、`POST /api/public/submissions` |
| **`PUT`** | 資源或子資源整體覆蓋 | `PUT /api/user/surveys/{id}`、`PUT /api/user/surveys/{id}/settings`、`PUT /api/user/profile`、`PUT /api/user/password` |
| **`PATCH`** | 局部狀態修改 | `PATCH /api/user/surveys/{id}/settings`、`PATCH /api/admin/users/{userId}/status` |
| **`DELETE`** | 資源刪除 (本專案皆為軟刪除) | `DELETE /api/user/surveys/{id}`、`DELETE /api/admin/users/{userId}`、`DELETE /api/admin/surveys/{surveyId}` |

### 0.1 路由前綴與授權邊界

| 前綴 | 授權規則 (`SecurityConfig`) |
| :--- | :--- |
| `/api/public/**` | 免認證 |
| `/api/user/**`、`/api/analytics/**` | 需有效 Bearer Access Token，且帳號狀態為 `ACTIVE` |
| `/api/admin/**` | 需 `ROLE_ADMIN` (URL 規則 + 類別層級 `@PreAuthorize`) |
| `/actuator/health` | 免認證 (保活探針) |

### 0.2 成功回應封裝 (`ApiResponse<T>`)

成功 (2xx) 回應一律為下列結構：

```json
{
  "status": 200,
  "success": true,
  "message": "繁中提示訊息",
  "data": {},
  "timestamp": "2026-11-13T12:00:00"
}
```

### 0.3 錯誤回應：RFC 9457 Problem Details (`GlobalExceptionHandler`)

所有錯誤 (4xx / 5xx，含 Spring Security 過濾鏈內產生之 401、403 與速率限制之 429) 一律回傳 `Content-Type: application/problem+json`：

```json
{
  "type": "urn:surveyflow:problem:account-not-verified",
  "title": "帳號尚未開通",
  "status": 403,
  "detail": "該帳號尚未完成電子郵件驗證，請至信箱點擊開通連結或輸入 6 位數啟用碼",
  "instance": "/api/public/auth/login",
  "code": "ACCOUNT_NOT_VERIFIED",
  "traceId": "65d2c9f3995a497cbbd94b0fb28a4132",
  "timestamp": "2026-11-21T15:19:03.926",
  "email": "user@example.com"
}
```

| 欄位 | 來源 | 用途 |
| :--- | :--- | :--- |
| `type` | RFC 9457 | 錯誤類型 URI (`urn:surveyflow:problem:<代碼小寫連字號>`)，穩定識別碼，不可解析 |
| `title` | RFC 9457 | 同一類型固定之繁中標題 |
| `status` | RFC 9457 | HTTP 狀態碼 |
| `detail` | RFC 9457 | 給使用者看的繁中原因；前端直接顯示 |
| `instance` | RFC 9457 | 請求路徑 |
| `code` | 擴充 | 機器可讀代碼 (`ProblemType`)；前端判斷特殊流程只看此欄 |
| `traceId` | 擴充 | 對應結構化日誌與稽核紀錄之追蹤碼 |
| `timestamp` | 擴充 | 錯誤發生時間 |
| `errors` | 擴充 (僅 `VALIDATION_FAILED`) | 「欄位 → 錯誤訊息」對照表 |
| `email` | 擴充 (僅 `ACCOUNT_NOT_VERIFIED`) | 前端導向開通頁並預填 |
| `retryAfterSeconds` | 擴充 (僅 `RATE_LIMITED`) | 需等待秒數，與 `Retry-After` 標頭相同 |

| `code` | HTTP | 觸發來源 | 說明 |
| :--- | :---: | :--- | :--- |
| `VALIDATION_FAILED` | 400 | `MethodArgumentNotValidException` | Bean Validation 欄位驗證失敗 |
| `BAD_REQUEST` | 400 | `IllegalArgumentException` | 業務規則不符 (如問卷已截止)，`detail` 為原因 |
| `MALFORMED_REQUEST` | 400 | `HttpMessageNotReadableException` | JSON 或日期格式錯誤 |
| `UNAUTHENTICATED` | 401 | `BadCredentialsException`、過濾鏈 entry point | 帳密錯誤、Refresh Token 無效或已作廢；未帶 Token、Token 過期或帳號非 `ACTIVE` (前端據此自動換發) |
| `ACCESS_DENIED` | 403 | `AccessDeniedException`、過濾鏈 accessDeniedHandler | 非問卷擁有者、非管理員 |
| `ACCOUNT_NOT_VERIFIED` | 403 | `AccountNotVerifiedException` | 帳號尚未完成信箱驗證 |
| `NOT_FOUND` | 404 | `NoResourceFoundException` | 路徑不存在 |
| `METHOD_NOT_ALLOWED` | 405 | `HttpRequestMethodNotSupportedException` | 動詞不支援 |
| `VERSION_CONFLICT` | 409 | `OptimisticLockException` / `ObjectOptimisticLockingFailureException` | 問卷已被他人修改；使用者重新整理後再改 |
| `DUPLICATE_DATA` | 409 | `DataIntegrityViolationException` | 違反唯一索引 (例如同時註冊同一帳號)；不回傳資料庫原始錯誤 |
| `LOGIN_LOCKED` | 429 | `LockedException` | 登入連續失敗達門檻，`detail` 含剩餘鎖定分鐘數 |
| `RATE_LIMITED` | 429 | `RateLimitExceededException` (`RateLimitFilter`) | 同一 IP 請求過於頻繁 (§0.4)，附 `Retry-After` |
| `MAIL_UNAVAILABLE` | 503 | `MailSendException` | 所有郵件管道皆寄送失敗 (交易已回滾，可稍後重試) |
| `INTERNAL_ERROR` | 500 | 其他 `Exception` | `detail` 固定，不外洩堆疊；以 `traceId` 回查日誌 |

### 0.4 共通標頭與速率限制

| 標頭 | 方向 | 說明 |
| :--- | :--- | :--- |
| `X-Request-Id` | 請求 (選填) | 上游已產生之請求編號；符合 8~64 字元英數與連字號時沿用為 traceId |
| `X-Trace-Id` | 回應 (每個請求) | 本次請求之追蹤碼；CORS 已開放前端讀取 |
| `Retry-After` | 回應 (429 速率限制) | 需等待秒數；CORS 已開放前端讀取 |

速率限制依「端點等級 + 用戶端 IP」計算，預設每 60 秒：`/api/public/auth/**` 20 次、`POST /api/public/submissions` 60 次、其餘 300 次；`/actuator/**` 與預檢請求不計數 (細節見 `09-security.md` §5.1)。

---

## 1. 身分驗證端點 (`AuthController`)

| 動詞 | 路徑 | 請求 Body | 成功回應 `data` |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/public/auth/register` | `RegisterRequest` | `AuthResponse` (不含 Token、不下發 Cookie)，並寄出 6 碼啟用驗證碼 |
| `POST` | `/api/public/auth/verify-email` | `{ email, code }` | `AuthResponse` (含 Access Token) + Refresh Cookie，帳號轉為 `ACTIVE` |
| `POST` | `/api/public/auth/resend-verification` | `{ email }` | 無 |
| `POST` | `/api/public/auth/login` | `{ username, password }` | `AuthResponse` (含 Access Token) + Refresh Cookie |
| `POST` | `/api/public/auth/refresh` | `{ refreshToken }` (選填，未帶時讀取 Cookie) | `AuthResponse` (新 Access Token) + 新 Refresh Cookie |
| `POST` | `/api/public/auth/forget-password` | `{ email }` | 無，寄出 6 碼重設驗證碼 |
| `POST` | `/api/public/auth/reset-password` | `{ email, code, newPassword }` | 無 |
| `POST` | `/api/user/auth/logout` | 無 (需 Bearer) | 無，吊銷當前 Access Token 與 Cookie 中之 Refresh Token 並清除 Cookie |

### 1.1 請求欄位驗證

| DTO | 欄位與限制 |
| :--- | :--- |
| `RegisterRequest` | `username` 必填 3~50 字；`email` 必填 Email 格式；`password` 必填 6~100 字；`fullName` 必填 2~50 字；`phone` 選填 `^09\d{8}$`；`identityCard` 選填 |
| `VerifyEmailRequest` | `email` 必填 Email；`code` 必填 6 碼 |
| `ResetPasswordRequest` | `email` 必填 Email；`code` 必填 6 碼；`newPassword` 必填 6~100 字 |

### 1.2 `AuthResponse`

| 欄位 | 說明 |
| :--- | :--- |
| `accessToken` / `token` | Access Token (兩欄位同值，`token` 為前端相容欄位) |
| `refreshToken` | 回應 Body 中一律為 `null`；Refresh Token 僅以 HttpOnly Cookie 下發 |
| `tokenType` | 固定 `Bearer` |
| `userId`、`username`、`fullName`、`email`、`phone`、`identityCard`、`role` | 會員概要，`role` 為 `ROLE_USER` / `ROLE_ADMIN` |

### 1.3 Refresh Token Cookie

`Set-Cookie: refreshToken=<JWT>; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=<jwt.refresh-token-expiration-ms / 1000>`。`Secure` 與 `SameSite` 分別由 `REFRESH_COOKIE_SECURE` (預設 `true`)、`REFRESH_COOKIE_SAME_SITE` (預設 `None`，前後端跨站部署必要) 控制；本機 HTTP 開發設為 `false` / `Lax`。

### 1.4 業務規則

- 註冊時若帳號或信箱已存在且狀態為 `PENDING_VERIFICATION`，允許覆蓋更新並重發驗證碼；其餘狀態回傳 400。
- 同一帳號 (不分大小寫) 連續登入失敗 5 次即鎖定 15 分鐘 (回傳 429)，帳號不存在亦計數；成功登入清除計數。門檻與分鐘數由 `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES` 調整。
- 登入時 `PENDING_VERIFICATION` 回傳 403 (`AccountNotVerifiedException`)；非 `ACTIVE` 回傳 400。
- 換發時舊 Refresh Token 之 JTI 進入黑名單，簽發全新雙 Token (RTR)；同一顆 Refresh Token 第二次使用視為重放，回 401 並作廢該使用者全部 Token。
- 透過 `/reset-password` 重設密碼後，該使用者此前簽發之全部 Token 皆失效。
- 驗證碼有效 15 分鐘，核對成功即標記已使用；驗證碼明碼不寫入任何日誌。

---

## 2. 會員端點 (`UserController`)

| 動詞 | 路徑 | 請求 Body | 成功回應 `data` |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/user/profile` | 無 | `UserProfileResponse` |
| `PUT` | `/api/user/profile` | `{ fullName, phone, identityCard }` | `UserProfileResponse` |
| `PUT` | `/api/user/password` | `{ oldPassword, newPassword }` | 無 |

- `UserProfileUpdateRequest`: `fullName` 必填 2~50 字；`phone` 選填 `^(09\d{8}|09\d{2}-\d{3}-\d{3})?$`；`identityCard` 選填 `^([A-Z][1289]\d{8})?$`。`phone` 為 null 時不修改，`identityCard` 空白時不修改。
- `PasswordChangeRequest`: `oldPassword` 必填 (需與現有密碼相符，否則 400)；`newPassword` 必填 6~100 字。前端個人資料頁要求使用者輸入「目前密碼」後才送出。
- `UserProfileResponse`: `id`、`username`、`email`、`fullName`、`phone`、`identityCard`、`role`、`status` (`PENDING_VERIFICATION` / `ACTIVE` / `SUSPENDED` / `DELETED`)、`createdAt`、`updatedAt`。

---

## 3. 問卷端點 (`SurveyController`)

| 動詞 | 路徑 | 授權 | 說明 |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/user/surveys` | 會員 | 建立問卷 (`SurveyCreateRequest`) |
| `GET` | `/api/public/surveys/lobby?category=&keyword=&page=&size=` | 公開 | 大廳分頁清單，僅含開放中、公開且位於起訖時窗內之問卷 (預設 size=100，依 `created_at` 倒序) |
| `GET` | `/api/public/surveys/{id}` | 公開 | 由大廳進入之填答用完整結構；僅限公開於大廳之問卷 (未公開者一律回 400「問卷不存在或已下架」)；檢核開放狀態與時窗並累加瀏覽數；回應不含 `shareToken` |
| `GET` | `/api/public/surveys/shared/{shareToken}` | 公開 | 由專屬填答連結 (`/survey-runner?share=<代碼>`) 進入之填答用完整結構；公開與未公開於大廳之問卷皆可；代碼區分大小寫，不存在回 400「問卷不存在或已下架」；其餘檢核與上列相同 |
| `GET` | `/api/public/surveys/{id}?preview=true` | 建立者 / 管理員 (需 Bearer，否則 403) | 預覽用完整結構；不檢核時窗、不累加瀏覽數 |
| `GET` | `/api/user/surveys` | 會員 | 自己建立之問卷分頁，含個人序號 `userSurveyNumber` 與分享代碼 `shareToken` |
| `GET` | `/api/user/surveys/{id}` | 建立者 / 管理員 | 問卷完整結構 |
| `PUT` | `/api/user/surveys/{id}` | 擁有者 / 管理員 | 更新問卷內容 (`SurveyUpdateRequest`，僅覆寫非 null 欄位；可帶 `version`，不符回 409) |
| `PUT` | `/api/user/surveys/{id}/settings` | 擁有者 / 管理員 | 整體覆蓋開放設定 (`isOpen`、`isPublicToLobby` 必填；起訖時間可為 null 表示不限期；可帶 `version`，不符回 409) |
| `PATCH` | `/api/user/surveys/{id}/settings` | 擁有者 / 管理員 | 局部修改開放設定，僅覆寫請求中非 null 之欄位 (可帶 `version`，不符回 409) |
| `DELETE` | `/api/user/surveys/{id}` | 擁有者 / 管理員 | 軟刪除 (寫入 `deleted_at`) |

### 3.1 `SurveyCreateRequest`

| 欄位 | 型別 | 限制 / 預設 |
| :--- | :--- | :--- |
| `title` | string | 必填，1~150 字 |
| `description` | string | 最多 5000 字 |
| `coverImageUrl` | string | Base64 或 URL |
| `category` | string | 最多 50 字，預設 `綜合調查` |
| `isPublicToLobby` / `isOpen` | boolean | 預設 `true` |
| `startDate` / `endDate` | datetime | 選填；開始不可晚於截止 |
| `basicInfoConfig` | `BasicInfoConfigDto` | 20 個布林旗標；未帶時姓名 / 性別 / 年齡預設收集，其餘不收集 |
| `questions[]` | `QuestionDto` | `type` 必填 (`SINGLE`/`MULTI`/`BOOLEAN`/`TEXT`/`RATING`/`DATE`/`TIME`)；`questionText` 必填 1~500 字；`options[].optionText` 必填 1~255 字；`options[].triggerQuestionUid` 為條件跳題目標題目代碼；是非題未帶選項時自動補上「是 / 否」 |

日期欄位接受 `yyyy-MM-dd'T'HH:mm[:ss]`、`yyyy/MM/dd HH:mm[:ss]`、`yyyy-MM-dd HH:mm[:ss]`、`yyyy-MM-dd`。

### 3.2 樂觀鎖版本號 `version`

- **分享代碼**：`SurveyResponse`、`SurveySummaryResponse` 的 `shareToken` 只在回應給建立者或管理員時有值 (建立、更新、詳情、個人清單、全站清單)；大廳清單與受訪者填答回應一律為 `null`，避免從公開回應取得私人問卷的連結。
- **回應**：`SurveyResponse`、`SurveySummaryResponse` 都帶 `version` (整數，新問卷為 0，每次經由 Entity 修改並寫入後 +1；瀏覽數遞增不會改變它)。
- **請求**：`SurveyUpdateRequest`、`SurveySettingsRequest` 可帶 `version` (選填)。帶了且與資料庫不同時回 **409**；不帶 (null) 時只靠 JPA `@Version` 在寫入當下比對，仍能擋下兩個請求同時寫入。
- **前端流程**：問卷管理頁開啟設定視窗時記住清單上的 `version`，送出 `PUT …/settings` 時原樣帶回；成功後以回應中的新 `version` 更新清單，下一次修改才不會被誤判為衝突。

```json
// PUT /api/user/surveys/3/settings
{ "startDate": "2026-12-01T08:00:00", "endDate": null, "isPublicToLobby": true, "isOpen": true, "version": 2 }

// 409 Conflict (另一個分頁已先改過，資料庫版本為 3)
{ "status": 409, "success": false, "message": "此問卷已被其他人修改，請重新整理後再試", "data": null, "timestamp": "2026-11-20T10:00:00" }
```

---

## 4. 填答端點 (`SubmissionController`)

| 動詞 | 路徑 | 授權 | 說明 |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/public/submissions` | 公開 | 送出填答，記錄客戶端 IP；未公開於大廳之問卷必須在 `shareToken` 帶上正確分享代碼，否則回 400「問卷不存在或已下架」 |

`SubmissionRequest`: `surveyId` 必填；`shareToken` 由專屬填答連結進入時帶上 (未公開於大廳之問卷必填)；`durationSeconds` 作答秒數；`basicInfo` 受訪者輪廓 (19 個欄位)；`answers[]`：

| 欄位 | 說明 |
| :--- | :--- |
| `questionId` | 必填；依序以「題目 ID → 排序序號 → 作答索引」對應題目 |
| `selectedOptionId` | 單選 / 是非題選項 ID |
| `selectedOptionIds` | 多選題選項 ID 陣列 |
| `textAnswer` | 文字作答 (最多 5000 字)；多選題可逗號分隔、是非題可為 true/false/1/0/yes/no |
| `ratingValue` | 評分值；未帶時嘗試由 `textAnswer` 解析 |

回應 `SubmissionResponse`: `submissionId`、`surveyId`、`message`、`submittedAt`。問卷關閉、未開始或已截止時回傳 400 與原因。

---

## 5. 統計分析端點

| 動詞 | 路徑 | 授權 | 說明 |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/analytics/surveys/{surveyId}` | 擁有者 / 管理員 (於 `AnalyticsService` 核對，否則 403) | 單一問卷統計報表 |
| `GET` | `/api/admin/analytics/surveys/{surveyId}` | 管理員 | 同上 (管理員入口) |
| `GET` | `/api/admin/analytics/overview` | 管理員 | 全站指標 (`Map`：會員數、問卷數、填答數、伺服器時間等) |

`SurveyAnalyticsResponse`: `surveyId`、`surveyTitle`、`category`、`authorUsername`、`isPublicToLobby`、`isOpen`、`startDate`、`endDate`、`lastResponseAt`、`kpi` (`totalResponses`、`averageDurationSeconds`、`completionRate`、`netPromoterScore`、`todayResponses`)、`trends[]`、`demographics`、`questionStats[]`。前端無資料時一律顯示 0，不以模擬數據填充。

---

## 6. 管理員端點 (`AdminController`，全數需 `ROLE_ADMIN`)

| 動詞 | 路徑 | 說明 |
| :--- | :--- | :--- |
| `GET` | `/api/admin/users?page=&size=` | 全站會員分頁 (排除管理員與已刪除帳號)；`phone`、`identityCard` 為遮罩值 (如 `0912***678`、`A******789`) |
| `PATCH` | `/api/admin/users/{userId}/status?status=ACTIVE\|SUSPENDED` | 變更帳號狀態；非法值回傳 400；停權後該帳號之 Access Token 於下一次請求即失效 |
| `DELETE` | `/api/admin/users/{userId}` | 軟刪除會員 (管理員帳號受保護) |
| `GET` | `/api/admin/surveys?page=&size=` | 全站問卷分頁 |
| `DELETE` | `/api/admin/surveys/{surveyId}` | 強制軟刪除問卷 |
| `GET` | `/api/admin/analytics/overview` | 見第 5 節 |
| `GET` | `/api/admin/analytics/surveys/{surveyId}` | 見第 5 節 |

## 7. 稽核日誌端點 (`AuditLogController`，全數需 `ROLE_ADMIN`，只有讀取)

| 動詞 | 路徑 | 說明 |
| :--- | :--- | :--- |
| `GET` | `/api/admin/audit-logs?actor=&action=&page=&size=` | 稽核紀錄分頁 (由新到舊，預設每頁 20 筆)；`actor` 完全比對帳號、`action` 為事件代碼，皆選填 |
| `GET` | `/api/admin/audit-logs/actions` | 全部事件類型 `[{ code, label }]`，供篩選選單 |
| `GET` | `/api/admin/audit-logs/verification` | 重算整條 HMAC 雜湊鏈，回傳 `{ valid, checkedCount, brokenAtId, message, verifiedAt }`；不改變任何資料 |

`AuditLogResponse`: `id`、`occurredAt`、`actor`、`action`、`actionLabel` (繁中名稱)、`targetType` (`USER` / `SURVEY`)、`targetId`、`detail`、`clientIp`、`traceId`、`hash`。事件清單與防竄改機制見 `09-security.md` §6。
