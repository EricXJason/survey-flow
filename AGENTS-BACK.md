# AGENTS-BACK.md | 全域 AI Agent 後端與資料庫特化規範庫

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **更新日期**: 2026-09-30  
> **適用領域**: 後端伺服器 (Java/Spring Boot, Node.js/NestJS, Go, Python)、資料庫 (SQL, Redis) 與雲端微服務。純靜態規範庫，完全繼承 `AGENTS.md`。

---

## 1. 後端分層架構與命名規範

- **分層架構隔離**:
  - `controller/`: 負責路由派發、DTO 驗證與 HTTP 狀態碼映射，嚴禁商業邏輯。
  - `service/`: 負責核心商務運算、交易邊界控制與領域規則。
  - `repository/` 或 `dao/`: 負責持久化資料存取與快取交互。
  - `entity/` 與 `dto/`: 資料庫實體與通訊物件強制物理隔離，嚴禁對外直接暴露 Entity。
  - **領域模型單一真實來源 (SSOT)**: 實體定義、驗證規則與通訊契約 (DTO/OpenAPI/Protobuf) 必須維持單向真實源頭，嚴禁手動散落重複定義不同型別結構。
- **命名慣例 (業界主流標準)**:
  - **Java (Spring Boot)**: 檔案與類別 `PascalCase.java`；介面不加 `I` 前綴，實作類別結尾加 `Impl`；成員與變數採標準 **`camelCase`**（無底線）。
  - **Node.js (NestJS)**: 檔案採 `kebab-case.service.ts`；私有成員採用標準 `private camelCase`。
  - **Python (FastAPI / Django)**: 檔案與模組採 `snake_case.py`；類別 `PascalCase`；函式與變數採標準 `snake_case`；私有成員採原生 `_snake_case`。
  - **Go**: 檔案採 `snake_case.go`；導出成員 `PascalCase`，未導出私有成員 `camelCase`。
- **後端頂部與方法註解 (默認落實)**:
  - Controller、Service、Repository 頂部必備繁中 Header，說明「職責邊界」與「上游調用者 / 下游資料表 / 外部 API 聯動關係」。
  - 核心業務方法、交易管理方法上方強制附帶 JSDoc / JavaDoc / Docstring 說明業務目的、參數與回傳值。

---

## 2. 資料庫設計、版本遷移、交易邊界與快取防禦

- **資料庫設計與版本遷移**:
  - 強制具備明確主鍵；高頻查詢欄位建立複合索引；核心實體必備 `created_at`, `updated_at`, `deleted_at` 審計欄位。
  - **ERD 完整性與 NoSQL 投影要求**:
    - 資料庫設計與文檔中之 ERD 必須展開真實業務之子實體關聯、主外鍵、欄位名稱與型別，並附帶臺灣繁體中文註解，嚴禁空殼或僅標註 `array`。
    - **NoSQL 文件庫約定**: 若底層為 NoSQL 文件資料庫（如 Firestore、MongoDB），將嵌入式物件或反正規化結構投影為 ERD 表格時，實體註解強制標註存儲型態（例如 `【Firestore 聚合根內嵌結構】`），防止誤導為傳統關聯表外鍵約束。
  - DDL 結構異動強制透過版本化遷移腳本 (如 Flyway, Liquibase, Prisma Migrate, Alembic) 管理，嚴禁依賴 ORM 自動建表異動生產環境。
- **交易與冪等性**:
  - 涉及資料庫跨表異動強制包裹於交易中（如 `@Transactional`），失敗強制 Rollback。
  - 關鍵寫入端點校驗 Header 之 `Idempotency-Key`，配合 Redis 分散式鎖防範重複操作。
- **快取防禦策略 (Cache-Aside Pattern)**:
  - **防穿透**: 查無資料時快取空值（短 TTL）或前端配置 Bloom Filter。
  - **防擊穿**: 熱點 Key 失效瞬間使用互斥鎖 (Mutex) 保障僅單一執行緒穿透至資料庫重構。
  - **防雪崩**: 快取失效時間 (TTL) 強制加入 10%~20% 隨機擾動因子 (Jitter)。

---

## 3. 零信任資安架構與金鑰隔離

- **雙 Token 機制**: Access Token 短效期 (15 分鐘)；Refresh Token (7 天) 採旋轉機制 (RTR)，換發時舊 Token 立即作廢。
- **RBAC 權限與安全防護**:
  - 權限判定強制於中介層 (Guard / Interceptor / Middleware) 完成。
  - 資料庫密碼、API Key **強制由 `.env` 注入**，嚴禁硬編碼，專案隨附 `.env.example`。
  - API 錯誤回應統一遵循 RFC 7807 結構化格式，嚴禁向外暴露 Stack Trace 或 SQL 語法。

---

## 4. 容器化構建與可觀測性

- **Docker 多階段構建**: 分離編譯與執行環境，使用最小化 Base Image，強制以非 root 使用者執行。
- **結構化日誌**: 輸出 JSON 格式日誌，必備 `timestamp`, `level`, `traceId`, `service`, `message`。

---

## 5. 後端與資料庫服務 MCP 賦能建議

當人類使用者提及或要求對接後端資料庫或雲端服務（如 PostgreSQL, MySQL, Redis, Firebase, Supabase, Cloudflare 等）且環境具備對應 MCP 支援時，Agent **必須主動建議啟用對應 MCP 工具**，並說明其加速能力（例如：直連執行唯讀結構檢查、自動校驗資料表 Schema、無縫執行 Migration 測試，避免在終端與 GUI 工具間反覆手動切換複製）。