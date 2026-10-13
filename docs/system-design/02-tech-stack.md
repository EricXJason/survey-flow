# 02. 技術選型矩陣與版本理由 (Technology Stack & Decisions)

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **系統環境**: Node.js 24.x, TypeScript 5.6.x, Angular 19.x  
> **更新日期**: 2026-10-06  

---

## 1. 核心技術選型矩陣

| 類別 / 領域 | 選定技術 / 工具 | 版本號 | 選型權衡與架構優勢 |
| :--- | :--- | :--- | :--- |
| **前端核心框架** | Angular | `^19.0.0` | 採用現代 Standalone Components 獨立架構，徹底廢除 NgModule 繁瑣宣告；整合 Angular Signals 實現細粒度高性能響應式更新。 |
| **語言與型別系統** | TypeScript | `~5.6.2` | 提供最高等級強型別合約保障，徹底杜絕執行期 Undefined 存取異常，支援進階型別推導與條件介面。 |
| **狀態管理機制** | Angular Signals & RxJS | Signals + `~7.8.0` | 區域與共享狀態採用 Signals 原生追蹤，複雜事件串流與非同步彈窗交互採用 RxJS Observable，兼顧效能與開發彈性。 |
| **UI 元件與佈局庫** | Angular Material & CDK | `^19.2.19` | 採用 Material Table、Paginator、Sort 提供具備在地化語言支援的後台資料表；導入 `@angular/cdk/drag-drop` 實現問卷題型流暢拖曳排序。 |
| **表單核心驅動** | Angular Reactive Forms | `^19.0.0` | 支援高階動態 FormArray 與 FormGroup 巢狀陣列動態擴充，精準監聽 valueChanges 達成條件跳題與即時防呆。 |
| **樣式與設計系統** | SCSS + CSS 自訂變數 | 原生 SCSS | 導入模組化 SCSS 體系 (`common.scss`, `variables.scss`, `styles.scss`)，建立高對比深色賽博 HUD 視覺規範與流動微動態。 |
| **建置與打包工具** | Angular CLI (Vite + esbuild) | `^19.0.5` | 底層基於 esbuild 與 Vite 核心，提供極速熱重載 (HMR) 與體積高度壓縮之現代化 Bundle 產物。 |
| **套件管理器** | pnpm / npm | Node 24.x | 支援高相容性依賴鎖定與現代化套件隔離機制。 |
| **後端核心框架 (目標)** | Java / Spring Boot | `Java 21` / `3.3.x` | 業界標準企業級後端，提供高併發處理能力、靜態型別安全、Spring Security 嚴密權限防護與 Spring Data JPA 交易隔離。 |
| **持久化關聯資料庫 (目標)** | MySQL | `8.0+` | 業界主流 ACID 關聯式資料庫，具備優異的二級索引查詢、聚合作答統計效能 (COUNT, AVG, GROUP BY) 與外鍵完整性約束。 |

---

## 2. 核心架構決策紀錄 (ADR)

### ADR-01: 全面採用 Standalone Components 與 Signals
- **決策背景**: 傳統 Angular 採用 NgModule 造成模組間依賴難以拆分與樹搖 (Tree-shaking) 不完全。
- **解決方案**: 專案所有 18 個組件 100% 採用 `standalone: true`，狀態採用 `signal()` 與 `computed()`，大幅降低記憶體開銷並消除不必要的全域變更檢測 (Change Detection Cycle)。

### ADR-02: 雙軌狀態儲存 (In-Memory + SessionStorage)
- **決策背景**: SPA 頁面重新整理 (F5) 容易遺失當前登入者身分與工作區脈絡。
- **解決方案**: `UserStateService` 於背景將狀態序列化至瀏覽器 `sessionStorage`，頁面初始化時自動無縫還原；登出時主動清除快取，兼顧安全性與流暢度。

### ADR-03: 題目唯一識別符 (Crypto UUID) 綁定條件跳題
- **決策背景**: 若以陣列索引 (Index) 綁定題目跳題解鎖，使用者執行「拖曳重新排序」或刪除題目時，會導致跳題目標錯位失聯。
- **解決方案**: 題目與選項解鎖目標全面採用 `crypto.randomUUID()` 唯一字串綁定，無論順序如何調動，邏輯依賴永遠精準指向目標題型。

### ADR-04: 嚴禁 Bootstrap，貫徹純原生 CSS / SCSS 模組化設計
- **決策背景**: Bootstrap 包含大量冗餘樣式、全域類別污染且難以達成客製化的深色 HUD 霓虹高科技風格。
- **解決方案**: 專案 100% 排除 Bootstrap 依賴，採用原生 SCSS 變數體系 (`variables.scss`) 與流動網格，保證元件樣式完全自主可控且達到極致效能。

### ADR-05: 面向 Java Spring Boot + MySQL 之全端 RESTful 數據分析架構
- **決策背景**: 前端數據分析必須無縫切換至生產環境後端服務，不可造成重構成本。
- **解決方案**: 前端 DTO 與 API 介面規格嚴格對齊 Spring Boot REST Controller 與 MySQL Schema，提供統一的 `ApiResponse<T>` 封裝，實現平滑無痛接軌。
