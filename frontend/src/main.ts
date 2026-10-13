/**
 * ============================================================================
 * 檔案名稱: main.ts
 * 所屬模組: 應用程式啟動入口模組 (Bootstrap Module)
 * 責任描述: 負責 Angular 單頁應用程式 (SPA) 的根啟動流程，引導 AppComponent 並載入全域配置。
 * 架構分層: Application Root / Bootstrap Layer
 * 聯動關係:
 *   - 上游依賴: ./app/app.config (全域應用設定檔), ./app/app.component (根組件)
 *   - 下游消費: 瀏覽器執行環境 (Browser Runtime)
 * 邊界處理: 捕獲啟動失敗例外並輸出錯誤日誌至主控臺
 * ============================================================================
 */

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));

