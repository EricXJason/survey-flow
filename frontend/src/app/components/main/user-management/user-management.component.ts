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

/**
 * 全站會員帳號資料模型
 */
export interface ManagedUser {
  id: string;
  name: string;
  email: string;
  phone: string;
  role: 'admin' | 'employee' | 'student' | 'teacher' | 'researcher' | 'retired' | 'other';
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
  private readonly _userState = inject(UserStateService);
  private readonly _dialogService = inject(DialogService);
  private readonly _router = inject(Router);

  /** 載入中狀態信號 */
  public readonly isLoading = signal<boolean>(true);
  /** 搜尋關鍵字信號 */
  public readonly searchQuery = signal<string>('');
  /** 狀態過濾信號 ('all' | 'active' | 'suspended') */
  public readonly statusFilter = signal<'all' | 'active' | 'suspended'>('all');
  /** 角色過濾信號 ('all' | 特定角色) */
  public readonly roleFilter = signal<string>('all');

  /** 模擬會員資料清單信號 */
  public readonly usersList = signal<ManagedUser[]>([
    {
      id: 'USR-001',
      name: '系統總管理員',
      email: 'admin@surveyflow.com',
      phone: '0912-345-678',
      role: 'admin',
      status: 'active',
      registeredAt: '2024-01-01',
      lastLoginAt: '2026-09-17 12:45',
      surveysCount: 18,
    },
    {
      id: 'USR-002',
      name: '陳雅婷',
      email: 'yating.chen@enterprise.tw',
      phone: '0923-456-789',
      role: 'employee',
      status: 'active',
      registeredAt: '2024-03-15',
      lastLoginAt: '2026-09-16 18:20',
      surveysCount: 8,
    },
    {
      id: 'USR-003',
      name: '林冠宇',
      email: 'guanyu.lin@ntu.edu.tw',
      phone: '0934-567-890',
      role: 'student',
      status: 'active',
      registeredAt: '2024-04-10',
      lastLoginAt: '2026-09-15 09:12',
      surveysCount: 4,
    },
    {
      id: 'USR-004',
      name: '張家銘 教授',
      email: 'jmzhang@academic.edu.tw',
      phone: '0955-678-901',
      role: 'teacher',
      status: 'active',
      registeredAt: '2024-05-02',
      lastLoginAt: '2026-09-14 20:30',
      surveysCount: 12,
    },
    {
      id: 'USR-005',
      name: '黃怡君 研究員',
      email: 'yjhuang@stat.sinica.edu.tw',
      phone: '0966-789-012',
      role: 'researcher',
      status: 'active',
      registeredAt: '2024-06-20',
      lastLoginAt: '2026-09-17 11:05',
      surveysCount: 15,
    },
    {
      id: 'USR-006',
      name: '李建宏',
      email: 'jh.lee@techcorp.io',
      phone: '0977-890-123',
      role: 'employee',
      status: 'suspended',
      registeredAt: '2024-07-08',
      lastLoginAt: '2026-08-30 14:18',
      surveysCount: 2,
    },
    {
      id: 'USR-007',
      name: '吳詩涵',
      email: 'shihan.wu@alumni.org',
      phone: '0988-901-234',
      role: 'student',
      status: 'active',
      registeredAt: '2024-08-19',
      lastLoginAt: '2026-09-16 22:40',
      surveysCount: 5,
    },
    {
      id: 'USR-008',
      name: '趙敏芬 主任',
      email: 'mfzhao@hospital.gov.tw',
      role: 'researcher',
      phone: '0911-234-567',
      status: 'active',
      registeredAt: '2024-09-03',
      lastLoginAt: '2026-09-13 16:55',
      surveysCount: 9,
    },
    {
      id: 'USR-009',
      name: '周維哲',
      email: 'wz.chou@freelance.cc',
      phone: '0922-345-678',
      role: 'employee',
      status: 'active',
      registeredAt: '2024-10-11',
      lastLoginAt: '2026-09-17 08:22',
      surveysCount: 6,
    },
    {
      id: 'USR-010',
      name: '謝志明',
      email: 'zm.hsieh@life.com',
      phone: '0933-456-789',
      role: 'retired',
      status: 'active',
      registeredAt: '2024-11-25',
      lastLoginAt: '2026-09-10 10:15',
      surveysCount: 1,
    },
    {
      id: 'USR-011',
      name: '楊舒涵',
      email: 'sh.yang@startup.ai',
      phone: '0944-567-890',
      role: 'employee',
      status: 'suspended',
      registeredAt: '2024-12-05',
      lastLoginAt: '2026-08-12 15:40',
      surveysCount: 0,
    },
    {
      id: 'USR-012',
      name: '郭俊逸',
      email: 'jy.kuo@designlab.org',
      phone: '0955-123-456',
      role: 'other',
      status: 'active',
      registeredAt: '2025-01-18',
      lastLoginAt: '2026-09-15 19:10',
      surveysCount: 3,
    },
  ]);

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

  @ViewChild(MatPaginator) public paginator!: MatPaginator;
  @ViewChild(MatSort) public sort!: MatSort;

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

    // 角色篩選
    const rf = this.roleFilter();
    if (rf !== 'all') {
      list = list.filter((u) => u.role === rf);
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

    setTimeout(() => {
      this.isLoading.set(false);
    }, 280);
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
      employee: '上班族',
      student: '學生',
      teacher: '教師',
      researcher: '研究人員',
      retired: '已退休',
      other: '其他',
    };
    return roleMap[role] || role;
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
   * 處理角色下拉選單切換
   */
  public onRoleSelectChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.roleFilter.set(select.value);
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
      }
    });
  }
}
