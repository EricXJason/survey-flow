/**
 * ============================================================================
 * 檔案名稱: api-client.service.ts
 * 所屬模組: 網路通訊服務模組 (HTTP API Client Module)
 * 責任描述: 負責前端與 Spring Boot 後端 (http://localhost:8080) 進行 RESTful 通訊，封裝零信任雙 Token 認證 (Bearer Access Token + HttpOnly Refresh Token Cookie)、全域請求頭與標準回應轉換。
 * 架構分層: Service Layer / Network Infrastructure Layer
 * 聯動關係:
 *   - 上游依賴: @angular/common/http (HttpClient), ./user-state.service
 *   - 下游消費: SurveyGetterService, SurveyAnalyticsService, UserEmitterService
 * 邊界處理: 內建 withCredentials: true 支援跨域憑證 Cookie 自動傳遞，支援 Refresh Token 換發
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserStateService } from './user-state.service';

export interface ApiResponse<T> {
  status: number;
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * 解析後端 API 基礎根路徑
 * 1. 支援外部動態配置覆寫 (window.__API_URL__)
 * 2. 本地開發與區網測試 (localhost, 127.0.0.1, 192.168.*, 10.*, .local) 自動對接本機或區域後端
 * 3. 雲端生產部署預設對接 Render 正式服務 (https://survey-flow-9gt6.onrender.com)
 */
function resolveApiBaseUrl(): string {
  if (typeof window !== 'undefined') {
    const injected = (window as unknown as { __API_URL__?: string }).__API_URL__;
    if (injected) {
      return injected.replace(/\/+$/, '');
    }

    if (window.location) {
      const { hostname, protocol } = window.location;
      const isLocal =
        hostname === 'localhost' ||
        hostname === '127.0.0.1' ||
        hostname.startsWith('192.168.') ||
        hostname.startsWith('10.') ||
        hostname.endsWith('.local');

      if (isLocal) {
        return `${protocol}//${hostname}:8080`;
      }
    }
  }
  return 'https://survey-flow-9gt6.onrender.com';
}

@Injectable({
  providedIn: 'root',
})
export class ApiClientService {
  private readonly http = inject(HttpClient);
  private readonly userState = inject(UserStateService);

  /** 後端 API 基礎根路徑 */
  public readonly baseUrl = resolveApiBaseUrl();

  /**
   * 取得附加 JWT Bearer Token 之通訊標頭
   */
  private getHeaders(): HttpHeaders {
    let headers = new HttpHeaders({
      'Content-Type': 'application/json',
    });

    const token = sessionStorage.getItem('SURVEY_ACCESS_TOKEN');
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    return headers;
  }

  /**
   * 執行長效 Refresh Token 換發新雙 Token (支援 Cookie 與本地備份)
   */
  public refreshAuthToken(): Observable<ApiResponse<any>> {
    const storedRefreshToken = sessionStorage.getItem('SURVEY_REFRESH_TOKEN') || '';
    return this.http.post<ApiResponse<any>>(
      `${this.baseUrl}/api/public/auth/refresh`,
      { refreshToken: storedRefreshToken },
      {
        headers: new HttpHeaders({ 'Content-Type': 'application/json' }),
        withCredentials: true,
      }
    );
  }

  /**
   * GET 請求封裝
   */
  public get<T>(endpoint: string): Observable<ApiResponse<T>> {
    return this.http.get<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, {
      headers: this.getHeaders(),
      withCredentials: true,
    });
  }

  /**
   * POST 請求封裝
   */
  public post<T>(endpoint: string, body: unknown): Observable<ApiResponse<T>> {
    return this.http.post<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, body, {
      headers: this.getHeaders(),
      withCredentials: true,
    });
  }

  /**
   * PUT 請求封裝 (完整資源替換)
   */
  public put<T>(endpoint: string, body: unknown): Observable<ApiResponse<T>> {
    return this.http.put<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, body, {
      headers: this.getHeaders(),
      withCredentials: true,
    });
  }

  /**
   * PATCH 請求封裝 (局部狀態修改)
   */
  public patch<T>(endpoint: string, body: unknown = {}): Observable<ApiResponse<T>> {
    return this.http.patch<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, body, {
      headers: this.getHeaders(),
      withCredentials: true,
    });
  }

  /**
   * DELETE 請求封裝
   */
  public delete<T>(endpoint: string): Observable<ApiResponse<T>> {
    return this.http.delete<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, {
      headers: this.getHeaders(),
      withCredentials: true,
    });
  }

  /**
   * 將後端錯誤與 HTTP 狀態碼轉譯為 100% 臺灣繁體中文友好訊息 (全域 SSOT)
   * 杜絕向使用者拋出 "Http failure response for ...: 403" 等原始英文
   * @param err HTTP 錯誤物件或字串
   * @param fallbackMessage 查無精確對應時的備用提示訊息
   * @returns 臺灣繁體中文錯誤提示字串
   */
  public resolveErrorMessage(err: any, fallbackMessage: string): string {
    if (!err) {
      return fallbackMessage;
    }
    const status = err.status;
    if (status === 0) {
      return '無法連線至伺服器，請確認後端服務是否正常運行或檢查網路連線。';
    }
    if (status === 401) {
      return '尚未登入或會員認證已過期，請重新登入後再試。';
    }
    if (status === 403) {
      return '存取權限不足或登入憑證已失效，請確認帳號權限或重新登入。';
    }
    if (status === 404) {
      return '找不到所請求的伺服器資源或目標問卷不存在。';
    }
    if (status === 400) {
      const serverMsg = err?.error?.message;
      if (serverMsg && typeof serverMsg === 'string' && !serverMsg.includes('Http failure')) {
        return serverMsg;
      }
      return '請求資料內容不完整或格式有誤，請檢查後再試。';
    }
    if (status === 500) {
      return '伺服器內部發生異常，請稍後再試或聯繫系統管理員。';
    }
    if (status === 502 || status === 503 || status === 504) {
      return '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。';
    }

    const serverMsg = err?.error?.message;
    if (serverMsg && typeof serverMsg === 'string' && !serverMsg.includes('Http failure')) {
      return serverMsg;
    }

    return fallbackMessage;
  }
}
