/**
 * ============================================================================
 * 檔案名稱: environment.ts
 * 所屬模組: 環境設定模組 - 本地開發環境 (Local Development Environment)
 * 責任描述: 定義本地開發時期之全域環境變數，鎖定本機 Spring Boot API 端點 (http://localhost:8080)
 * 架構分層: Configuration Layer
 * 聯動關係:
 *   - 上游依賴: 無
 *   - 下游消費: ApiClientService, angular.json (開發時期默認引用)
 * ============================================================================
 */

export const environment = {
  production: false,
  /** 本地開發後端 API 基礎根路徑 */
  apiUrl: 'http://localhost:8080',
};
