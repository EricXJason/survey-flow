/**
 * ============================================================================
 * 檔案名稱: user-management.component.ts
 * 所屬模組: 會員管理與權限控管模組 (User Administration Module)
 * 責任描述: 供系統管理員（Admin）檢視全站會員清單、關鍵指標統計、多欄位模糊搜尋、狀態與角色過濾、以及安全移除帳號。
 * 架構分層: Presentation Layer / Page Component
 * 聯動關係:
 *   - 上游依賴: ../../../@services/user-state.service, ../../../@services/dialog.service
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
  status: 'active' | 'suspended';
  registeredAt: string;
  lastLoginAt: string;
  surveysCount: number;
}

/**
 * 會員帳號管理組件 (UserManagementComponent)
 * 供系統管理員（Admin）管理全站註冊會員，包含：
 * - 關鍵指標統計（會員總數、正常啟用、停用/凍結、管理員）
 * - 即時多條件關鍵字搜尋（姓名、帳號、手機、身分）
 * - 身分角色與帳號狀態篩選切換
 * - 快速啟用/停用帳號狀態切換
 * - 重設初始密碼與帳號移除操作對話框
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
  /** 狀態過濾信號 ('all' | 'active' | 'suspended') */
  public readonly statusFilter = signal<'all' | 'active' | 'suspended'>('all');

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
  /** 計算屬性：已停用/凍結數量 */
  public readonly suspendedUsers = computed(
    () => this.usersList().filter((u) => u.status === 'suspended').length,
  );
  /** 計算屬性：管理員帳號數量 */
  public readonly adminUsers = computed(
    () => this.usersList().filter((u) => u.role === 'admin').length,
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

  public ngOnInit(): void {
    // 驗證權限：若非管理員則導向問卷管理或首頁
    if (this._userState.access() !== 'admin') {
      this._router.navigate(['/survey-management']);
      return;
    }

    this.loadUsers();
  }

  /**
   * 向後端 API 載入全站真實會員清單 (嚴格排除 admin，僅列出一般會員)
   */
  public loadUsers(): void {
    this.isLoading.set(true);
    this.isServerUnavailable.set(false);
    this._apiClient.get<any>('/api/admin/users?page=0&size=100').subscribe({
      next: (res) => {
        if (res && res.data && res.data.content) {
          const mapped: ManagedUser[] = res.data.content
            .filter((item: any) => item.role !== 'ROLE_ADMIN' && item.username !== 'admin')
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
                status: item.status === 'ACTIVE' ? 'active' : 'suspended',
                registeredAt: item.createdAt ? item.createdAt.substring(0, 10) : '2026-10-13',
                lastLoginAt: item.createdAt ? item.createdAt.substring(0, 16).replace('T', ' ') : '-',
                surveysCount: item.surveyCount || 0,
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

  public ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  /**
   * 取得身分角色的中文名稱
   */
  public getRoleLabel(role: string): string {
    const roleMap: Record<string, string> = {
      admin: '系統管理員',
      ROLE_ADMIN: '系統管理員',
      user: '一般會員',
      ROLE_USER: '一般會員',
      guest: '訪客',
      ROLE_GUEST: '訪客',
      employee: '一般會員',
      student: '一般會員',
      other: '一般會員',
    };
    return roleMap[role] || '一般會員';
  }

  /**
   * 取得會員姓名縮寫字母
   */
  public getUserInitial(name: string): string {
    return name ? name.charAt(0) : 'U';
  }

  /**
   * 設定狀態過濾標籤
   */
  public setStatusFilter(status: 'all' | 'active' | 'suspended'): void {
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
      message: `警告：刪除會員「${user.name} (${user.email})」將一併封存該會員發起之 ${user.surveysCount} 份問卷資料，此動作無法復原！確定要永久移除嗎？`,
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
                message: err?.error?.message || '伺服器執行刪除時發生錯誤，請稍後再試。',
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
   * 切換會員帳號啟用/凍結狀態
   */
  public toggleUserStatus(user: ManagedUser): void {
    if (user.role === 'admin') {
      return;
    }
    const targetStatus = user.status === 'active' ? 'SUSPENDED' : 'ACTIVE';
    this._apiClient.patch(`/api/admin/users/${user.id}/status?status=${targetStatus}`, {}).subscribe({
      next: () => {
        this.usersList.update((list) =>
          list.map((u) => (u.id === user.id ? { ...u, status: targetStatus === 'ACTIVE' ? 'active' : 'suspended' } : u)),
        );
      },
    });
  }
}
