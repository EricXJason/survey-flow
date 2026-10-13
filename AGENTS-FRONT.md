# AGENTS-FRONT.md | 全域 AI Agent 前端與 UI/UX 特化規範庫

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **更新日期**: 2026-09-30  
> **適用領域**: Web 前端 (React, Vue, Next.js, Nuxt, TS) 與 UI/UX 設計。純靜態規範庫，完全繼承 `AGENTS.md`。

---

## 1. 套件管理器標準 (pnpm Protocol)

- **強制優先採用 pnpm**: Web 專案一律優先使用 `pnpm`（`pnpm add`, `pnpm install`, `pnpm run build`）。
- **npm 轉換清除 SOP**: 檢測到 `package-lock.json` 時，主動詢問使用者是否切換至 pnpm。獲得同意後刪除 `package-lock.json` 與 `node_modules/`，執行 `pnpm install` 生成 `pnpm-lock.yaml`。

---

## 2. 前端命名慣例 (Naming Standards)

- **檔案命名**:
  - 工具函式、腳本、樣式：`kebab-case`（如 `auth-service.ts`, `main.scss`）。
  - UI 元件檔案：`PascalCase`（如 `UserProfileCard.tsx`, `HeaderMenu.vue`）。
  - 元件樣式模組：`PascalCase.module.scss`。
- **代碼成員命名 (業界主流標準)**:
  - 公開與私有變數、函式：標準 **`camelCase`**。
  - 私有屬性：依專案標準使用 `private camelCase` 或原生 `#camelCase`（嚴禁無差別強制加底線）。
  - 類別、介面、型別：`PascalCase`（介面不加 `I` 前綴）。
  - 常數：`UPPER_SNAKE_CASE`。
  - Custom Hook / Composable：以 `use` 開頭的小駝峰（如 `useDebounce`）。
- **前端頂部與方法註解 (默認落實)**:
  - 所有 UI 元件、Hook、Service 頂部強制附帶繁中 Header，說明「元件做什麼」及「與哪些 Context / Page 聯動」。
  - 生命週期回呼、事件處理函式 (Handlers) 與導出函式上方強制標記繁中方法目的。

---

## 3. 欠缺後端 API 之 TODO 標註規格

當後端端點尚未就緒時，必須使用繁體中文 `TODO:` 註解標註：
```typescript
/**
 * TODO: [端點對接] 取得使用者個人資料
 * 1. HTTP Method: GET
 * 2. 預期端點: /api/v1/users/{userId}
 * 3. 請求參數: Header (Authorization: Bearer <TOKEN>), Path (userId)
 * 4. 預期回應: 200 OK (User Profile JSON), 401 Unauthorized, 404 Not Found
 * 5. 暫代方案: 暫時返回本地 mockData，端點就緒後替換
 */
```

---

## 4. UI/UX 抗排版崩潰、狀態單一可信與 RWD 規範

* **文案真實化**: 嚴禁使用「一站式平臺」、「釋放潛能」等假大空行銷詞與 `Lorem Ipsum`；展示資料必須具備真實情境語意與長度多樣性。
* **狀態單一可信原則 (SSOT)**: 全域狀態與持久化快取嚴禁多頭寫入，快取讀取必須具備防禦性 Schema 驗證與平滑降級。
* **抗排版崩潰防護**:
  * 動態文字容器強制配置文字溢出防護（`truncate`, `line-clamp-2` 或 `break-words`）。
  * 包含彈性或截斷文字的 Flex 子元素，強制宣告 **`min-w-0`**，防止容器撐破。
  * 按鈕與膠囊標籤短文字強制宣告 `whitespace-nowrap`，杜絕孤兒換行。
  * PC 端絕對禁止非預期全域水平滾動條，寬表格必須局部封裝於 `overflow-x-auto`。
* **Header RWD 規範**: 小螢幕斷點禁止直接隱藏導覽列，強制提供觸控區域 **>= 44x44px** 之漢堡選單按鈕，並實作完整的抽屜/折疊選單機制。
* **雙主題與無障礙**:
  * 文字對比度底線：一般文字 **>= 4.5:1**；大字體 **>= 3:1** (WCAG 2.1 AA)。
  * 鍵盤焦點狀態必須具備高對比焦點環，嚴禁裸露 `outline-none`。
* **戰術 HUD 直角美學**: 元件外框與卡片優先貫徹硬朗戰術直角風格（`rounded-none` 或微直角），與圖表規範維持全專案高度統一。

---

## 5. 前端與設計服務 MCP 賦能建議

當人類使用者提及或要求對接設計工具或外部前端資產服務（如 Figma, UI 規格標註庫等）且環境具備對應 MCP 支援時，Agent **必須主動建議啟用對應 MCP 工具**，並說明其加速能力（例如：自動提取 Design Token、直接解析元件圖層與屬性，省去人工切圖與手動量測尺寸的時間）。