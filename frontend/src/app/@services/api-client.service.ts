/**
 * ============================================================================
 * 檔案名稱: api-client.service.ts
 * 所屬模組: 網路通訊服務模組 (HTTP API Client Module)
 * 責任描述: 負責前端與 Spring Boot 後端進行 RESTful 通訊，封裝雙 Token 認證 (Bearer Access Token + HttpOnly Refresh Token Cookie)、
 *           Access Token 過期 (401) 時自動以 Refresh Cookie 換發並重送原請求，以及錯誤訊息繁中轉譯 (讀取 RFC 9457 Problem Details)。
 * 架構分層: Service Layer / Network Infrastructure Layer
 * 聯動關係:
 *   - 上游依賴: @angular/common/http (HttpClient)、UserStateService (Access Token 存取與換發失敗時重設為訪客)
 *   - 下游消費: SurveyGetterService, SurveyAnalyticsService, UserEmitterService
 * 邊界處理: 同一時間僅發出一個換發請求 (避免 Refresh Token 輪替後彼此作廢)；換發失敗即重設為訪客並拋出原 401；
 *           /api/public/auth/ 端點不觸發換發，避免登入失敗或換發本身形成迴圈
 * ============================================================================
 */

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, switchMap } from 'rxjs/operators';
import { AuthResponseData } from '../@interfaces/account-model';
import { UserStateService } from './user-state.service';

/** 不觸發自動換發之端點前綴 (登入、註冊、換發本身等公開認證端點) */
const AUTH_ENDPOINT_PREFIX = '/api/public/auth/';

/** 後端沒有回傳訊息時，依 HTTP 狀態碼顯示之預設說明 */
const STATUS_FALLBACK_MESSAGES: Record<number, string> = {
  0: '無法連線至伺服器，請確認後端服務是否正常運行或檢查網路連線。',
  400: '請求資料內容不完整或格式有誤，請檢查後再試。',
  401: '尚未登入或會員認證已過期，請重新登入後再試。',
  403: '存取權限不足或登入憑證已失效，請確認帳號權限或重新登入。',
  404: '找不到所請求的資源，可能已被刪除。',
  409: '資料已被其他人修改，請重新整理後再試。',
  429: '請求過於頻繁，請稍後再試。',
  500: '伺服器內部發生異常，請稍後再試或聯繫系統管理員。',
  502: '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。',
  503: '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。',
  504: '後端伺服器暫時無回應或連線逾時，請確認伺服器運作狀態。',
};

/** 後端回應標頭：請求追蹤碼 (與伺服器結構化日誌之 traceId 相同) */
const TRACE_ID_HEADER = 'X-Trace-Id';

/** HTTP 請求方法 */
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

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

  /** 進行中之換發請求 (多個請求同時 401 時共用同一次換發) */
  private refreshInFlight$: Observable<void> | null = null;

  /**
   * POST 請求封裝
   * @param endpoint 端點路徑
   * @param body 請求內容
   * @returns 後端標準回應
   */
  public post<T>(endpoint: string, body: unknown): Observable<ApiResponse<T>> {
    return this.request<T>('POST', endpoint, body);
  }

  /**
   * GET 請求封裝
   * @param endpoint 端點路徑 (以 /api 開頭)
   * @returns 後端標準回應
   */
  public get<T>(endpoint: string): Observable<ApiResponse<T>> {
    return this.request<T>('GET', endpoint);
  }

  /**
   * PUT 請求封裝 (完整資源替換)
   * @param endpoint 端點路徑
   * @param body 請求內容
   * @returns 後端標準回應
   */
  public put<T>(endpoint: string, body: unknown): Observable<ApiResponse<T>> {
    return this.request<T>('PUT', endpoint, body);
  }

  /**
   * PATCH 請求封裝 (局部狀態修改)
   * @param endpoint 端點路徑
   * @param body 請求內容，預設為空物件
   * @returns 後端標準回應
   */
  public patch<T>(endpoint: string, body: unknown = {}): Observable<ApiResponse<T>> {
    return this.request<T>('PATCH', endpoint, body);
  }

  /**
   * DELETE 請求封裝
   * @param endpoint 端點路徑
   * @returns 後端標準回應
   */
  public delete<T>(endpoint: string): Observable<ApiResponse<T>> {
    return this.request<T>('DELETE', endpoint);
  }

  /**
   * 將後端錯誤轉為臺灣繁體中文訊息 (全站唯一的錯誤訊息來源)：
   *   1. 後端 RFC 9457 Problem Details 之 detail 有內容時優先顯示 (後端訊息皆為繁中且不含內部細節，例如「帳號或密碼錯誤」)
   *   2. 沒有後端訊息時 (斷線、閘道逾時、非 JSON 回應) 依狀態碼顯示預設說明
   *   3. 都對不上時顯示呼叫端提供之 fallbackMessage
   *   伺服器錯誤 (5xx) 另附上追蹤碼 (Problem Details 之 traceId，或回應標頭 X-Trace-Id)，使用者回報時可直接查到對應日誌
   * [Why]: Angular 原始的 "Http failure response for ..." 英文訊息一律不顯示給使用者
   * @param err HTTP 錯誤物件
   * @param fallbackMessage 查無精確對應時的備用提示訊息
   * @returns 臺灣繁體中文錯誤提示字串
   */
  public resolveErrorMessage(err: any, fallbackMessage: string): string {
    if (!err) {
      return fallbackMessage;
    }
    const serverDetail = err?.error?.detail;
    const message =
      typeof serverDetail === 'string' && serverDetail.trim() && !serverDetail.includes('Http failure')
        ? serverDetail
        : (STATUS_FALLBACK_MESSAGES[err.status] ?? fallbackMessage);
    const traceId = err.status >= 500 ? (err?.error?.traceId ?? err?.headers?.get?.(TRACE_ID_HEADER)) : null;
    return traceId ? `${message}（追蹤碼：${traceId}）` : message;
  }

  /**
   * 發送請求；已登入且收到 401 時自動換發 Access Token 後重送一次
   * @param method HTTP 方法
   * @param endpoint 端點路徑
   * @param body 請求內容 (GET / DELETE 不帶)
   * @returns 後端標準回應
   */
  private request<T>(method: HttpMethod, endpoint: string, body?: unknown): Observable<ApiResponse<T>> {
    return this.send<T>(method, endpoint, body).pipe(
      catchError((err: HttpErrorResponse) => {
        const canRefresh =
          err.status === 401 && !endpoint.startsWith(AUTH_ENDPOINT_PREFIX) && !!this.userState.getAccessToken();
        if (!canRefresh) {
          return throwError(() => err);
        }
        return this.refreshAccessToken().pipe(
          switchMap(() => this.send<T>(method, endpoint, body)),
          catchError(() => throwError(() => err)),
        );
      }),
    );
  }

  /**
   * 實際發送 HTTP 請求 (每次呼叫皆重新讀取最新 Access Token)
   * @param method HTTP 方法
   * @param endpoint 端點路徑
   * @param body 請求內容
   * @returns 後端標準回應
   */
  private send<T>(method: HttpMethod, endpoint: string, body?: unknown): Observable<ApiResponse<T>> {
    return this.http.request<ApiResponse<T>>(method, `${this.baseUrl}${endpoint}`, {
      body,
      headers: this.getHeaders(),
      withCredentials: true,
    });
  }

  /**
   * 以 HttpOnly Refresh Cookie 換發 Access Token；換發失敗時重設為訪客
   * @returns 換發完成之通知串流 (多個呼叫端共用同一次請求)
   */
  private refreshAccessToken(): Observable<void> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http
        .post<ApiResponse<AuthResponseData>>(`${this.baseUrl}${AUTH_ENDPOINT_PREFIX}refresh`, {}, { withCredentials: true })
        .pipe(
          map((res) => {
            const accessToken = res?.data?.accessToken || res?.data?.token;
            if (!accessToken) {
              throw new Error('換發回應缺少 Access Token');
            }
            this.userState.storeAccessToken(accessToken);
          }),
          catchError((err) => {
            this.userState.resetToGuest();
            return throwError(() => err);
          }),
          finalize(() => {
            this.refreshInFlight$ = null;
          }),
          shareReplay(1),
        );
    }
    return this.refreshInFlight$;
  }

  /**
   * 取得附加 JWT Bearer Token 之通訊標頭
   * @returns 含 Content-Type 與 (已登入時) Authorization 之標頭
   */
  private getHeaders(): HttpHeaders {
    let headers = new HttpHeaders({
      'Content-Type': 'application/json',
    });
    const token = this.userState.getAccessToken();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }
}
