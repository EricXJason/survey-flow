/**
 * ============================================================================
 * 檔案名稱: user-management.component.ts
 * 所屬模組: 會員管理與權限控管模組 (User Administration Module)
 * 責任描述: 供系統管理員（Admin）檢視全站會員清單 (手機與身分證由後端遮罩)、關鍵指標統計、多欄位模糊搜尋、狀態與角色過濾、安全移除帳號，
 *           並可切換至稽核日誌面板 (AuditLogPanelComponent)。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/user-state.service, ../../../@services/dialog.service, ./audit-log-panel/audit-log-panel.component
 *   - 下游消費: app.routes.ts 路由導航調用
 * 邊界處理: 非管理員身分進入時自動重新導向至問卷管理頁面；禁止刪除系統總管理員
 * ============================================================================
 */

import {
  AfterViewInit,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { UserStateService } from '../../../@services/user-state.service';
import { DialogService, DialogData } from '../../../@services/dialog.service';

import { ApiClientService } from '../../../@services/api-client.service';
import { AuditLogPanelComponent } from './audit-log-panel/audit-log-panel.component';

/**
 * 全站會員帳號資料模型
 */
export interface ManagedUser {
  id: string;
  username: string;
  fullName: string;
  name: string;
  email: string;
  phone: string;
  role: 'admin' | 'user' | string;
  status: ManagedUserStatus;
  registeredAt: string;
}

/** 管理員專區分頁：會員帳號、稽核日誌 */
export type AdminView = 'users' | 'audit';

/** 會員帳號狀態：正常、待開通 (尚未完成信箱驗證)、已凍結 */
export type ManagedUserStatus = 'active' | 'pending' | 'suspended';

/** 後端帳號狀態代碼 → 畫面狀態；未知代碼一律視為凍結，避免誤顯示為可登入 */
const STATUS_BY_BACKEND_CODE: Record<string, ManagedUserStatus> = {
  ACTIVE: 'active',
  PENDING_VERIFICATION: 'pending',
  SUSPENDED: 'suspended',
};

/**
 * 會員帳號管理組件 (UserManagementComponent)
 * 供系統管理員（Admin）管理全站註冊會員，包含：
 * - 關鍵指標統計（一般會員總數、正常啟用、待開通、已凍結）；後端清單不含管理員帳號
 * - 即時多條件關鍵字搜尋（姓名、帳號、手機、身分）
 * - 身分角色與帳號狀態篩選切換
 * - 凍結 / 恢復帳號狀態切換 (待開通帳號不可切換)
 * - 刪除帳號確認對話框
 */
@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    AuditLogPanelComponent,
  ],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent implements OnInit, AfterViewInit {
  private readonly _apiClient = inject(ApiClientService);
  private readonly _userState = inject(UserStateService);
  private readonly _dialogService = inject(DialogService);
  private readonly _router = inject(Router);

  /** 載入中狀態信號 */
  public readonly isLoading = signal<boolean>(true);
  /** 伺服器是否連線異常信號 */
  public readonly isServerUnavailable = signal<boolean>(false);
  /** 搜尋關鍵字信號 */
  public readonly searchQuery = signal<string>('');
  /** 狀態過濾信號 ('all' 或指定狀態) */
  public readonly statusFilter = signal<'all' | ManagedUserStatus>('all');

  /** 目前顯示之管理員專區：會員帳號或稽核日誌 */
  public readonly activeView = signal<AdminView>('users');

  /** 會員資料清單信號（真實對接 MySQL 後端 API） */
  public readonly usersList = signal<ManagedUser[]>([]);

  /** 表格欄位清單 */
  public readonly displayedColumns: string[] = [
    'user',
    'role',
    'phone',
    'registeredAt',
    'status',
    'actions',
  ];

  /** Material Table 資料來源 */
  public readonly dataSource = new MatTableDataSource<ManagedUser>([]);

  /** Material 分頁器組件引用 (採用 setter 確保 @if 延遲渲染時自動完成 dataSource 綁定) */
  @ViewChild(MatPaginator)
  public set matPaginator(mp: MatPaginator | undefined) {
    if (mp) {
      this.paginator = mp;
      this.dataSource.paginator = mp;
    }
  }
  public paginator!: MatPaginator;

  /** Material 排序控制組件引用 (採用 setter 確保 @if 延遲渲染時自動完成 dataSource 綁定) */
  @ViewChild(MatSort)
  public set matSort(ms: MatSort | undefined) {
    if (ms) {
      this.sort = ms;
      this.dataSource.sort = ms;
    }
  }
  public sort!: MatSort;

  /** 計算屬性：會員總數 */
  public readonly totalUsers = computed(() => this.usersList().length);
  /** 計算屬性：正常啟用中數量 */
  public readonly activeUsers = computed(
    () => this.usersList().filter((u) => u.status === 'active').length,
  );
  /** 計算屬性：待開通 (尚未完成信箱驗證) 數量 */
  public readonly pendingUsers = computed(
    () => this.usersList().filter((u) => u.status === 'pending').length,
  );
  /** 計算屬性：已凍結數量 */
  public readonly suspendedUsers = computed(
    () => this.usersList().filter((u) => u.status === 'suspended').length,
  );

  /** 計算屬性：依搜尋與篩選後的會員清單 */
  public readonly filteredUsers = computed(() => {
    let list = this.usersList();

    // 狀態篩選
    const sf = this.statusFilter();
    if (sf !== 'all') {
      list = list.filter((u) => u.status === sf);
    }

    // 關鍵字搜尋
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      list = list.filter(
        (u) =>
          u.name.toLowerCase().includes(q) ||
          u.fullName.toLowerCase().includes(q) ||
          u.email.toLowerCase().includes(q) ||
          u.phone.includes(q) ||
          u.id.toLowerCase().includes(q),
      );
    }

    return list;
  });

  constructor() {
    effect(() => {
      const data = this.filteredUsers();
      this.dataSource.data = data;
      if (this.paginator) {
        this.paginator.firstPage();
      }
    });
  }

  /**
   * 初始化：非管理員導回問卷管理頁，管理員則載入會員清單
   */
  public ngOnInit(): void {
    // 驗證權限：若非管理員則導向問卷管理或首頁
    if (this._userState.access() !== 'admin') {
      this._router.navigate(['/survey-management']);
      return;
    }

    this.loadUsers();
  }

  /**
   * 向後端 API 載入全站一般會員清單 (GET /api/admin/users，後端查詢已排除管理員帳號)；
   * 帳號狀態依 STATUS_BY_BACKEND_CODE 轉換，缺少的欄位顯示「未填寫」。
   * [Why]: 畫面上仍保留「管理員不可凍結 / 刪除」的判斷作為防護，避免後端清單規則調整後誤操作管理員
   */
  public loadUsers(): void {
    this.isLoading.set(true);
    this.isServerUnavailable.set(false);
    this._apiClient.get<any>('/api/admin/users?page=0&size=100').subscribe({
      next: (res) => {
        if (res && res.data && res.data.content) {
          const mapped: ManagedUser[] = res.data.content
            .map((item: any) => {
              const uName = item.username || 'user';
              const fName = item.fullName || uName;
              return {
                id: String(item.id),
                username: uName,
                fullName: fName,
                name: uName,
                email: item.email || '未填寫',
                phone: item.phone || '未填寫',
                role: item.role === 'ROLE_ADMIN' ? 'admin' : 'user',
                status: STATUS_BY_BACKEND_CODE[item.status] ?? 'suspended',
                registeredAt: item.createdAt ? item.createdAt.substring(0, 10) : '-',
              };
            });
          this.usersList.set(mapped);
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.isServerUnavailable.set(true);
      },
    });
  }

  /**
   * 切換管理員專區分頁 (會員帳號 / 稽核日誌)；稽核日誌面板於切換時才建立並載入
   * @param view 要顯示的分頁
   */
  public setActiveView(view: AdminView): void {
    this.activeView.set(view);
  }

  /**
   * 檢視初始化後綁定 Material 分頁器與排序器至資料來源
   */
  public ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  /**
   * 把角色代碼轉為表格顯示的中文名稱 (清單中的 role 已正規化為 admin / user，也接受後端原始 ROLE_ 代碼)
   * @param role 角色代碼
   * @returns 系統管理員或一般會員
   */
  public getRoleLabel(role: string): string {
    return role === 'admin' || role === 'ROLE_ADMIN' ? '系統管理員' : '一般會員';
  }

  /**
   * 取得會員姓名縮寫字母
   */
  public getUserInitial(name: string): string {
    return name ? name.charAt(0) : 'U';
  }

  /**
   * 切換狀態篩選分頁 (全部、正常、待開通、已凍結)，清單與分頁會隨之重算
   * @param status 要顯示的狀態，'all' 為不篩選
   */
  public setStatusFilter(status: 'all' | ManagedUserStatus): void {
    this.statusFilter.set(status);
  }

  /**
   * 處理搜尋輸入
   */
  public onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.searchQuery.set(input.value);
  }

  /**
   * 清除搜尋關鍵字
   */
  public clearSearch(): void {
    this.searchQuery.set('');
  }

  /**
   * 移除會員帳號（僅保留刪除帳號操作）
   */
  public onDeleteUser(user: ManagedUser): void {
    if (user.role === 'admin') {
      this._dialogService
        .showDialog({
          title: '無法刪除系統管理員',
          message: '為維護系統運作安全，系統管理員帳號不支援線上刪除。',
          mode: 'errorMode',
          errorText: '確定',
        })
        .subscribe();
      return;
    }

    const dialogData: DialogData = {
      title: '確認刪除會員帳號',
      message: `警告：刪除會員「${user.name} (${user.email})」該帳號將無法再登入，此動作無法復原！確定要移除嗎？`,
      mode: 'checkMode',
      confirmText: '確定永久刪除',
      cancelText: '取消',
    };

    this._dialogService.showDialog(dialogData).subscribe((confirmed) => {
      if (confirmed) {
        this._apiClient.delete(`/api/admin/users/${user.id}`).subscribe({
          next: () => {
            this.usersList.update((list) =>
              list.filter((u) => u.id !== user.id),
            );

            this._dialogService
              .showDialog({
                title: '會員帳號已移除',
                message: `會員「${user.name}」已被系統永久移除。`,
                mode: 'noticeMode',
                confirmText: '確定',
                autoCloseDuration: 2500,
              })
              .subscribe();
          },
          error: (err) => {
            this._dialogService
              .showDialog({
                title: '移除會員失敗',
                message: this._apiClient.resolveErrorMessage(err, '伺服器執行刪除時發生錯誤，請稍後再試。'),
                mode: 'errorMode',
                errorText: '確定',
              })
              .subscribe();
          },
        });
      }
    });
  }

  /**
   * 凍結或恢復會員帳號 (PATCH /api/admin/users/{id}/status)：先用確認對話框說明影響，確認後才送出；
   * 成功時更新表格中該列狀態，失敗時顯示原因。管理員與待開通帳號不可切換
   * ([Why]: 待開通帳號由會員自行完成信箱驗證，管理員直接啟用會繞過信箱驗證)。
   * @param user 要切換狀態之會員
   */
  public toggleUserStatus(user: ManagedUser): void {
    if (!this.canToggleStatus(user)) {
      return;
    }
    const willSuspend = user.status === 'active';
    const targetStatus = willSuspend ? 'SUSPENDED' : 'ACTIVE';
    this._dialogService
      .showDialog({
        title: willSuspend ? '確認凍結會員帳號' : '確認恢復會員帳號',
        message: willSuspend
          ? `凍結後「${user.name}」將無法登入，已登入的裝置也會在下一次請求時被登出。確定要凍結嗎？`
          : `恢復後「${user.name}」即可重新登入。確定要恢復嗎？`,
        mode: 'checkMode',
        confirmText: willSuspend ? '確定凍結' : '確定恢復',
        cancelText: '取消',
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this._apiClient.patch(`/api/admin/users/${user.id}/status?status=${targetStatus}`, {}).subscribe({
          next: () => {
            this.usersList.update((list) =>
              list.map((u) => (u.id === user.id ? { ...u, status: willSuspend ? 'suspended' : 'active' } : u)),
            );
          },
          error: (err) => {
            this._dialogService
              .showDialog({
                title: '變更帳號狀態失敗',
                message: this._apiClient.resolveErrorMessage(err, '變更帳號狀態失敗，請稍後再試。'),
                mode: 'errorMode',
                errorText: '確定',
              })
              .subscribe();
          },
        });
      });
  }

  /**
   * 判斷該會員的狀態開關是否可操作：管理員帳號受保護、待開通帳號需由會員自行驗證，兩者皆不可切換
   * @param user 會員
   * @returns 可切換時為 true
   */
  public canToggleStatus(user: ManagedUser): boolean {
    return user.role !== 'admin' && user.status !== 'pending';
  }

  /**
   * 取得狀態開關旁的文字與提示說明，供模板顯示
   * @param user 會員
   * @returns label 為開關旁文字，hint 為滑鼠提示與螢幕閱讀器說明
   */
  public getStatusToggleText(user: ManagedUser): { label: string; hint: string } {
    if (user.role === 'admin') {
      return { label: '正常啟用', hint: '系統管理員帳號受保護，不可凍結' };
    }
    const textByStatus: Record<ManagedUserStatus, { label: string; hint: string }> = {
      active: { label: '正常啟用', hint: '點擊以凍結此帳號' },
      pending: { label: '待開通', hint: '會員尚未完成信箱驗證，開通後才可登入' },
      suspended: { label: '已凍結', hint: '點擊以恢復此帳號' },
    };
    return textByStatus[user.status];
  }
}
