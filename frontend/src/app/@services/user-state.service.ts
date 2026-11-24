/**
 * ============================================================================
 * 檔案名稱: user-state.service.ts
 * 所屬模組: 使用者狀態管理模組 (User State Management Module)
 * 責任描述: 基於 Angular Signals 實現響應式使用者狀態管理（訪客/會員/管理員），並與瀏覽器 SessionStorage 雙向同步持久化。
 * 架構分層: Service Layer / State Management
 * 聯動關係:
 *   - 上游依賴: ../@interfaces/account-model (UserProfileData, AuthResponseData)
 *   - 下游消費: HeaderComponent, UserEmitterService, UserProfileComponent, UserManagementComponent, SurveyManagementComponent
 * 邊界處理: SessionStorage 讀寫包覆 try-catch 防範儲存配額受限或非瀏覽器執行期例外；
 *           Access Token 鍵名 (ACCESS_TOKEN_STORAGE_KEY) 於此集中定義，ApiClientService 共用
 * ============================================================================
 */

import { Injectable, signal } from '@angular/core';
import { AuthResponseData, UserProfileData } from '../@interfaces/account-model';

/** sessionStorage 中 Access Token 之鍵名 (全站唯一定義) */
export const ACCESS_TOKEN_STORAGE_KEY = 'SURVEY_ACCESS_TOKEN';

/**
 * 本地 Session 儲存結構介面
 * 定義寫入 sessionStorage 之使用者狀態資料格式
 */
interface StoredUserState {
  userProfile: UserProfileData;
}

/**
 * 全域使用者狀態管理服務 (UserStateService)
 * 
 * 核心職責：
 * 1. 管理全站使用者身份識別、帳號與角色權限（admin、user、guest）。
 * 2. 採用 Angular Signals 提供高性能響應式狀態追蹤。
 * 3. 負責與瀏覽器 SessionStorage 進行狀態雙向持久化同步，避免重新整理頁面遺失登入資訊。
 */
@Injectable({
  providedIn: 'root',
})
export class UserStateService {
  /** SessionStorage 儲存識別鍵名 */
  private readonly _storageKey = 'SURVEY_USER_STATE_SESSION';

  /** 預設訪客（未登入）基礎資料範本 */
  private readonly _guestProfile: UserProfileData = {
    id: '00000',
    access: 'guest',
    userName: 'guest',
    name: '訪客',
    nationalId: '',
    email: '',
    phone: '',
  };

  /** 當前登入者唯一識別碼 (Signal) */
  public readonly id = signal<string>(this._guestProfile.id);

  /** 當前使用者權限等級 (Signal，例如 'guest' | 'admin' | 'user') */
  public readonly access = signal<string>(this._guestProfile.access);

  /** 當前使用者登入帳號名稱 (Signal) */
  public readonly userName = signal<string>(this._guestProfile.userName);

  /** 當前完整個人資料物件 (Signal) */
  public readonly userProfile = signal<UserProfileData>({
    ...this._guestProfile,
  });

  /**
   * 服務建構子：初始化時自動嘗試從 SessionStorage 還原已保存的登入狀態
   */
  constructor() {
    this._restoreFromSession();
  }

  /**
   * 設定登入使用者身份與個人完整資料
   * 更新內部所有 Signal 狀態，並自動同步持久化至 SessionStorage
   * 
   * @param userData 包含帳號、權限與詳細資料之使用者物件
   */
  public setIdentity(userData: UserProfileData): void {
    // 同步更新各個核心 Signal 狀態
    this.id.set(userData.id);
    this.access.set(userData.access);
    this.userName.set(userData.userName);
    this.userProfile.set({ ...userData });

    // 同步持久化寫入瀏覽器 SessionStorage
    this._syncToSession();
  }

  /**
   * 套用後端認證回應 (登入 / 信箱驗證開通)：保存 Access Token 並設定登入身分
   * @param authData 後端 AuthResponse 資料
   * @returns 套用後之使用者資料
   */
  public applyAuthResponse(authData: AuthResponseData): UserProfileData {
    this.storeAccessToken(authData.accessToken || authData.token || '');
    const profile: UserProfileData = {
      id: String(authData.userId),
      access: authData.role === 'ROLE_ADMIN' ? 'admin' : 'member',
      userName: authData.username,
      name: authData.fullName,
      nationalId: authData.identityCard || '',
      email: authData.email,
      phone: authData.phone || '',
    };
    this.setIdentity(profile);
    return profile;
  }

  /**
   * 保存 Access Token 至 sessionStorage (分頁關閉即清除)
   * @param accessToken JWT Access Token
   */
  public storeAccessToken(accessToken: string): void {
    try {
      sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken);
    } catch {
      // 靜默處理非瀏覽器環境例外
    }
  }

  /**
   * 讀取目前保存之 Access Token
   * @returns Access Token，未登入時為 null
   */
  public getAccessToken(): string | null {
    try {
      return sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    } catch {
      return null;
    }
  }

  /**
   * 登出並將使用者狀態重設為預設訪客 (Guest)
   * 歸零所有 Signal 並清除 SessionStorage 快取
   */
  public resetToGuest(): void {
    this.id.set(this._guestProfile.id);
    this.access.set(this._guestProfile.access);
    this.userName.set(this._guestProfile.userName);
    this.userProfile.set({ ...this._guestProfile });

    // 移除 SessionStorage 中保存之登入狀態與 Access Token
    try {
      sessionStorage.removeItem(this._storageKey);
      sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
    } catch {
      // 靜默處理非瀏覽器環境例外
    }
  }

  /**
   * 檢查當前是否為正式登入會員（非訪客 guest）
   */
  public isLoggedIn(): boolean {
    return this.access() !== 'guest' && this.userName() !== 'guest' && !!this.userName();
  }

  /**
   * 私有方法：將當前 userProfile 狀態序列化寫入 SessionStorage
   */
  private _syncToSession(): void {
    try {
      const stateToSave: StoredUserState = {
        userProfile: this.userProfile(),
      };
      sessionStorage.setItem(this._storageKey, JSON.stringify(stateToSave));
    } catch {
      // 避免本機儲存配額受限或非瀏覽器環境拋出異常
    }
  }

  /**
   * 私有方法：自 SessionStorage 還原先前儲存之使用者狀態
   */
  private _restoreFromSession(): void {
    try {
      const rawSessionData = sessionStorage.getItem(this._storageKey);
      if (!rawSessionData) {
        return;
      }

      const parsedUserState = JSON.parse(rawSessionData) as StoredUserState;
      if (parsedUserState && parsedUserState.userProfile) {
        // 將快取資料同步載入各 Signal
        this.id.set(parsedUserState.userProfile.id);
        this.access.set(parsedUserState.userProfile.access);
        this.userName.set(parsedUserState.userProfile.userName);
        this.userProfile.set(parsedUserState.userProfile);
      }
    } catch {
      // 若 JSON 解析失敗或快取損壞，維持預設訪客狀態
    }
  }
}

