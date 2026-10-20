/**
 * ============================================================================
 * 檔案名稱: header.component.ts
 * 所屬模組: 全域頁首導航模組 (Navigation Header Module)
 * 責任描述: 負責頂部全域導航列渲染、品牌標誌、各功能路由跳轉、使用者身分狀態切換（訪客/會員/管理員）與行動版漢堡選單抽屜互動。
 * 架構分層: Presentation Layer / Shared Layout Component
 * 聯動關係:
 *   - 上游依賴: ../../@services/user-state.service, ../../@services/user-emitter.service
 *   - 下游消費: AppComponent 宿主直接調用
 * 邊界處理: 監聽視窗 resize 自動關閉展開之手機抽屜；支援 ESC 鍵無障礙即刻關閉
 * ============================================================================
 */

import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { UserStateService } from '../../@services/user-state.service';
import { UserEmitterService } from '../../@services/user-emitter.service';
import { CAREER_LABEL_MAP } from '../../@interfaces/account-model';

/**
 * 頂部全域導航列組件
 * 負責品牌標誌展示、主導航連結導向、登入與登出狀態切換及行動版選單互動控制。
 */
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  /** 使用者身分與狀態管理服務 */
  private readonly _userState = inject(UserStateService);
  /** 使用者跨組件事件廣播服務（如登出通知） */
  private readonly _userEmitter = inject(UserEmitterService);

  /** 手機版抽屜導航選單開關狀態信號 (Signal) */
  public readonly isMobileMenuOpen = signal<boolean>(false);

  /** 計算屬性：當前使用者是否已登入（非訪客 guest 身分） */
  public readonly isLoggedIn = computed(
    () => this._userState.access() !== 'guest',
  );

  /** 計算屬性：當前使用者是否擁有管理員權限 (admin) */
  public readonly isAdmin = computed(
    () => this._userState.access() === 'admin',
  );

  /** 計算屬性：當前登入使用者的顯示名稱（優先採用個人資料之真實姓名） */
  public readonly userName = computed(
    () => this._userState.userProfile().name || this._userState.userName(),
  );

  public readonly userRole = computed(() => {
    const r = this._userState.userProfile().role;
    if (r && CAREER_LABEL_MAP[r]) {
      return CAREER_LABEL_MAP[r];
    }
    return this.isAdmin() ? '系統管理員' : '一般會員';
  });

  /** 計算屬性：使用者頭像縮寫字符（擷取姓名首字） */
  public readonly userInitial = computed(() => {
    const name = this.userName();
    return name ? name.charAt(0) : 'U';
  });

  /**
   * 監聽瀏覽器視窗尺寸變化事件 (window:resize)
   * 當螢幕寬度擴展至桌機尺寸 (> 920px) 時，自動關閉手機版展開的導航抽屜。
   */
  @HostListener('window:resize')
  protected onResize(): void {
    if (window.innerWidth > 920 && this.isMobileMenuOpen()) {
      this.closeMobileMenu();
    }
  }

  /**
   * 監聽鍵盤 ESC 鍵按下事件 (window:keydown)
   * 符合 WCAG 無障礙鍵盤規範，當手機選單開啟時，按下 ESC 鍵即刻關閉選單。
   * @param event 鍵盤按鍵事件
   */
  @HostListener('window:keydown', ['$event'])
  protected onEscape(event: KeyboardEvent): void {
    if ((event.key === 'Escape' || event.key === 'Esc') && this.isMobileMenuOpen()) {
      event.preventDefault();
      this.closeMobileMenu();
    }
  }

  /**
   * 切換手機版漢堡選單的開關狀態
   * 觸發 signal 反轉，展開或收合導航選單。
   */
  public toggleMobileMenu(): void {
    this.isMobileMenuOpen.update((v) => !v);
  }

  /**
   * 明確關閉手機版選單
   * 常用於路由點擊跳轉或背景遮罩點擊時重設狀態為 false。
   */
  public closeMobileMenu(): void {
    this.isMobileMenuOpen.set(false);
  }

  /**
   * 處理使用者登出操作
   * 關閉手機版選單並廣播登出信號，觸發全域清除 session 及導向首頁。
   */
  public onLogout(): void {
    this.closeMobileMenu();
    this._userEmitter.emitLogout();
  }
}
