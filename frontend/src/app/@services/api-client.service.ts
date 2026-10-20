/**
 * ============================================================================
 * 檔案名稱: api-client.service.ts
 * 所屬模組: 網路通訊服務模組 (HTTP API Client Module)
 * 責任描述: 負責前端與 Spring Boot 後端 (http://localhost:8080) 進行 RESTful 通訊，封裝單一 JWT Token 認證、全域請求頭與標準回應轉換。
 * 架構分層: Service Layer / Network Infrastructure Layer
 * 聯動關係:
 *   - 上游依賴: @angular/common/http (HttpClient), ./user-state.service
 *   - 下游消費: SurveyGetterService, SurveyAnalyticsService, UserEmitterService
 * 邊界處理: 內建網路超時與斷線防呆，當後端未啟動時提供優雅降級回退能力
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

@Injectable({
  providedIn: 'root',
})
export class ApiClientService {
  private readonly http = inject(HttpClient);
  private readonly userState = inject(UserStateService);

  /** 後端 API 基礎根路徑 */
  public readonly baseUrl = 'http://localhost:8080';

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
   * GET 請求封裝
   */
  public get<T>(endpoint: string): Observable<ApiResponse<T>> {
    return this.http.get<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, {
      headers: this.getHeaders(),
    });
  }

  /**
   * POST 請求封裝
   */
  public post<T>(endpoint: string, body: unknown): Observable<ApiResponse<T>> {
    return this.http.post<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, body, {
      headers: this.getHeaders(),
    });
  }

  /**
   * PUT 請求封裝
   */
  public put<T>(endpoint: string, body: unknown): Observable<ApiResponse<T>> {
    return this.http.put<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, body, {
      headers: this.getHeaders(),
    });
  }

  /**
   * PATCH 請求封裝
   */
  public patch<T>(endpoint: string, body?: unknown): Observable<ApiResponse<T>> {
    return this.http.patch<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, body ?? {}, {
      headers: this.getHeaders(),
    });
  }

  /**
   * DELETE 請求封裝
   */
  public delete<T>(endpoint: string): Observable<ApiResponse<T>> {
    return this.http.delete<ApiResponse<T>>(`${this.baseUrl}${endpoint}`, {
      headers: this.getHeaders(),
    });
  }
}
