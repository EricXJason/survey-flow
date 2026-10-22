/**
 * ============================================================================
 * 檔案名稱: environment.prod.ts
 * 所屬模組: 環境設定模組 - 雲端生產環境 (Production Cloud Environment)
 * 責任描述: 定義生產環境編譯打包時之全域變數，指向雲端 Render 後端 Web Service
 * 架構分層: Configuration Layer
 * 聯動關係:
 *   - 上游依賴: angular.json (fileReplacements 生產構建置換)
 *   - 下游消費: ApiClientService
 * ============================================================================
 */

export const environment = {
  production: true,
  /** 雲端生產環境 Render 後端 API 基礎根路徑 (部署 Render 後將自動對接) */
  apiUrl: 'https://survey-flow-backend.onrender.com',
};
