/**
 * ============================================================================
 * 檔案名稱: api-client.service.spec.ts
 * 測試對象: ApiClientService (Bearer 標頭、401 自動換發並重送、多請求共用同一次換發、換發失敗轉訪客、錯誤訊息繁中轉譯)
 * 測試方式: HttpTestingController 攔截所有 HTTP 請求，不會真的連線後端
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiClientService } from './api-client.service';
import { UserStateService } from './user-state.service';

describe('ApiClientService', () => {
  let service: ApiClientService;
  let http: HttpTestingController;
  let userState: UserStateService;

  /**
   * 組出完整請求網址
   * @param endpoint 端點路徑
   * @returns baseUrl + endpoint
   */
  const url = (endpoint: string): string => `${service.baseUrl}${endpoint}`;

  /** 換發端點完整網址 */
  const refreshUrl = (): string => url('/api/public/auth/refresh');

  /** 回應 401 時使用的 HTTP 狀態設定 */
  const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiClientService);
    http = TestBed.inject(HttpTestingController);
    userState = TestBed.inject(UserStateService);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  describe('一般請求', () => {
    it('已登入時附上 Authorization: Bearer 與 withCredentials', () => {
      userState.storeAccessToken('token-a');

      service.get('/api/user/surveys').subscribe();

      const req = http.expectOne(url('/api/user/surveys'));
      expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toBe('Bearer token-a');
      expect(req.request.withCredentials).toBeTrue();
      req.flush({ success: true });
    });

    it('訪客不附 Authorization 標頭', () => {
      service.get('/api/public/surveys').subscribe();

      const req = http.expectOne(url('/api/public/surveys'));
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush({});
    });

    it('post / put / patch / delete 使用對應的 HTTP 方法並帶入 body', () => {
      service.post('/p', { a: 1 }).subscribe();
      service.put('/u', { b: 2 }).subscribe();
      service.patch('/pa').subscribe();
      service.delete('/d').subscribe();

      const post = http.expectOne(url('/p'));
      const put = http.expectOne(url('/u'));
      const patch = http.expectOne(url('/pa'));
      const del = http.expectOne(url('/d'));
      expect([post.request.method, put.request.method, patch.request.method, del.request.method])
        .toEqual(['POST', 'PUT', 'PATCH', 'DELETE']);
      expect(post.request.body).toEqual({ a: 1 });
      expect(patch.request.body).toEqual({});
      [post, put, patch, del].forEach((req) => req.flush({}));
    });
  });

  describe('401 自動換發', () => {
    it('換發成功：保存新 Token 並以新 Token 重送原請求', () => {
      userState.storeAccessToken('old');
      let result: unknown;

      service.get('/api/user/me').subscribe((res) => (result = res.data));
      http.expectOne(url('/api/user/me')).flush(null, UNAUTHORIZED);
      http.expectOne(refreshUrl()).flush({ data: { accessToken: 'new' } });
      const retry = http.expectOne(url('/api/user/me'));

      expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
      retry.flush({ data: 'ok' });
      expect(result).toBe('ok');
      expect(userState.getAccessToken()).toBe('new');
    });

    it('兩個請求同時 401：只發出一次換發 (避免 Refresh Token 輪替後互相作廢)', () => {
      userState.storeAccessToken('old');

      service.get('/a').subscribe();
      service.get('/b').subscribe();
      http.expectOne(url('/a')).flush(null, UNAUTHORIZED);
      http.expectOne(url('/b')).flush(null, UNAUTHORIZED);
      http.expectOne(refreshUrl()).flush({ data: { token: 'new' } });

      http.expectOne(url('/a')).flush({});
      http.expectOne(url('/b')).flush({});
      expect(userState.getAccessToken()).toBe('new');
    });

    it('換發失敗：重設為訪客，呼叫端收到原本的 401', () => {
      userState.applyAuthResponse({ accessToken: 'old', userId: 1, username: 'u', fullName: 'U', email: 'e', role: 'ROLE_USER' } as never);
      let error: HttpErrorResponse | undefined;

      service.get('/api/user/me').subscribe({ error: (err) => (error = err) });
      http.expectOne(url('/api/user/me')).flush(null, UNAUTHORIZED);
      http.expectOne(refreshUrl()).flush(null, UNAUTHORIZED);

      expect(error?.status).toBe(401);
      expect(error?.url).toContain('/api/user/me');
      expect(userState.isLoggedIn()).toBeFalse();
      expect(userState.getAccessToken()).toBeNull();
    });

    it('換發回應缺少 Access Token 視為換發失敗', () => {
      userState.storeAccessToken('old');
      let error: HttpErrorResponse | undefined;

      service.get('/x').subscribe({ error: (err) => (error = err) });
      http.expectOne(url('/x')).flush(null, UNAUTHORIZED);
      http.expectOne(refreshUrl()).flush({ data: {} });

      expect(error?.status).toBe(401);
      expect(userState.getAccessToken()).toBeNull();
    });

    it('訪客收到 401 不換發', () => {
      let status = 0;

      service.get('/api/user/me').subscribe({ error: (err) => (status = err.status) });
      http.expectOne(url('/api/user/me')).flush(null, UNAUTHORIZED);

      http.expectNone(refreshUrl());
      expect(status).toBe(401);
    });

    it('登入等 /api/public/auth/ 端點收到 401 不換發 (避免迴圈)', () => {
      userState.storeAccessToken('old');

      service.post('/api/public/auth/login', {}).subscribe({ error: () => undefined });
      http.expectOne(url('/api/public/auth/login')).flush(null, UNAUTHORIZED);

      http.expectNone(refreshUrl());
    });

    it('非 401 錯誤直接拋給呼叫端', () => {
      userState.storeAccessToken('old');
      let status = 0;

      service.get('/x').subscribe({ error: (err) => (status = err.status) });
      http.expectOne(url('/x')).flush(null, { status: 403, statusText: 'Forbidden' });

      http.expectNone(refreshUrl());
      expect(status).toBe(403);
    });
  });

  describe('resolveErrorMessage', () => {
    const FALLBACK = '預設訊息';

    /**
     * 以指定狀態碼與後端訊息呼叫 resolveErrorMessage
     * @param status HTTP 狀態碼
     * @param detail 後端 Problem Details 之 detail
     * @returns 轉譯後訊息
     */
    const resolve = (status: number, detail?: unknown): string =>
      service.resolveErrorMessage({ status, error: detail === undefined ? null : { detail } }, FALLBACK);

    it('沒有錯誤物件時回傳預設訊息', () => {
      expect(service.resolveErrorMessage(null, FALLBACK)).toBe(FALLBACK);
    });

    it('後端有訊息時一律優先顯示 (例如登入失敗 401 顯示「帳號或密碼錯誤」，而不是「登入已過期」)', () => {
      expect(resolve(401, '帳號或密碼錯誤')).toBe('帳號或密碼錯誤');
      expect(resolve(403, '該帳號尚未完成電子郵件驗證')).toBe('該帳號尚未完成電子郵件驗證');
      expect(resolve(409, '此問卷已被其他人修改')).toBe('此問卷已被其他人修改');
      expect(resolve(429, '請 15 分鐘後再試')).toBe('請 15 分鐘後再試');
      expect(resolve(503, '驗證信寄送失敗')).toBe('驗證信寄送失敗');
      expect(resolve(429, '請求過於頻繁，請於 30 秒後再試')).toBe('請求過於頻繁，請於 30 秒後再試');
      expect(resolve(418, '我是茶壺')).toBe('我是茶壺');
    });

    it('沒有後端訊息時依狀態碼顯示預設說明', () => {
      expect(resolve(0)).toContain('無法連線');
      expect(resolve(400)).toContain('格式有誤');
      expect(resolve(401)).toContain('重新登入');
      expect(resolve(403)).toContain('權限不足');
      expect(resolve(404)).toContain('找不到');
      expect(resolve(409)).toContain('其他人修改');
      expect(resolve(429)).toContain('過於頻繁');
      expect(resolve(500)).toContain('伺服器內部');
      [502, 503, 504].forEach((status) => expect(resolve(status)).toContain('暫時無回應'));
    });

    it('伺服器錯誤 (5xx) 附上追蹤碼：優先取 Problem Details 之 traceId，否則取 X-Trace-Id 回應標頭；4xx 不附', () => {
      const headers = new HttpHeaders({ 'X-Trace-Id': 'header-trace' });
      expect(service.resolveErrorMessage({ status: 500, error: { detail: '伺服器內部發生未預期錯誤', traceId: 'body-trace' } }, FALLBACK))
        .toBe('伺服器內部發生未預期錯誤（追蹤碼：body-trace）');
      expect(service.resolveErrorMessage({ status: 502, error: null, headers }, FALLBACK)).toContain('（追蹤碼：header-trace）');
      expect(service.resolveErrorMessage({ status: 400, error: { detail: '格式錯誤', traceId: 'x' }, headers }, FALLBACK)).toBe('格式錯誤');
    });

    it('Angular 原始英文訊息、空白或非字串訊息不顯示；未知狀態碼回傳預設訊息', () => {
      expect(resolve(400, 'Http failure response for x: 400')).toContain('格式有誤');
      expect(resolve(400, '  ')).toContain('格式有誤');
      expect(resolve(418, 123)).toBe(FALLBACK);
      expect(resolve(418)).toBe(FALLBACK);
    });
  });
});
