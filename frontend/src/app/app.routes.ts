/**
 * ============================================================================
 * 檔案名稱: app.routes.ts
 * 所屬模組: 應用程式路由導航模組 (Routing Navigation Module)
 * 責任描述: 定義全站客戶端頁面路由路徑對應表、未匹配回退規則，以及離開頁面防呆守衛 (canDeactivateGuard)。
 * 架構分層: Routing Layer
 * 聯動關係:
 *   - 上游依賴: 各功能頁面組件 (Home, Lobby, SurveyRunner, SurveyDesign, etc.)
 *   - 下游消費: app.config.ts (provideRouter 註冊), Angular Router 核心
 * 邊界處理: 通配符 '**' 自動重新導向回 '/home'，防止無效網址導致白屏
 * ============================================================================
 */

import { CanActivateFn, CanDeactivateFn, Routes } from '@angular/router';
import { Observable } from 'rxjs';
import { HomeComponent } from './components/main/home/home.component';
import { LobbyComponent } from './components/main/lobby/lobby.component';
import { SurveyRunnerComponent } from './components/main/survey-runner/survey-runner.component';
import { SurveyDesignComponent } from './components/main/survey-design/survey-design.component';
import { SurveyManagementComponent } from './components/main/survey-management/survey-management.component';
import { UserManagementComponent } from './components/main/user-management/user-management.component';
import { LogInComponent } from './components/main/log-in/log-in.component';
import { ForgetPasswordComponent } from './components/main/forget-password/forget-password.component';
import { RegisterComponent } from './components/main/register/register.component';
import { ResetPasswordComponent } from './components/main/reset-password/reset-password.component';
import { UserProfileComponent } from './components/main/user-profile/user-profile.component';

/**
 * 具備離開前確認能力之組件介面 (CanComponentDeactivate)
 */
export interface CanComponentDeactivate {
  canDeactivate: () => Observable<boolean> | Promise<boolean> | boolean;
}

import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { UserStateService } from './@services/user-state.service';
import { DialogService } from './@services/dialog.service';

/**
 * 路由離開守衛 (CanDeactivate Guard)
 * 攔截跳轉導航，於離開前呼叫組件之 canDeactivate 方法（如彈出確認對話框）。
 * 只有使用者明確確認離開時才放行導航；取消時由 Angular Router 取消跳轉並維持原地狀態，
 * 避免先跳轉導致表單填答進度遺失或頁面閃爍跳躍。
 */
export const canDeactivateGuard: CanDeactivateFn<CanComponentDeactivate> = (
  component: CanComponentDeactivate,
) => {
  return component?.canDeactivate ? component.canDeactivate() : true;
};

/**
 * 會員登入守衛 (Auth Guard)
 * 嚴格阻斷未登入之訪客進入問卷設計器與管理後台，未登入者僅允許填答與瀏覽大廳
 */
export const authGuard: CanActivateFn = () => {
  const userState = inject(UserStateService);
  const router = inject(Router);
  const dialogService = inject(DialogService);

  if (userState.isLoggedIn()) {
    return true;
  }

  dialogService.showDialog({
    title: '請先登入會員',
    message: '您目前為訪客身分，必須先登入會員帳號方可發起或設計問卷。',
    mode: 'errorMode',
  });
  return router.createUrlTree(['/log-in']);
};

export const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: 'home', component: HomeComponent },
  { path: 'lobby', component: LobbyComponent },
  {
    path: 'survey-runner',
    component: SurveyRunnerComponent,
    canDeactivate: [canDeactivateGuard],
  },
  { path: 'log-in', component: LogInComponent },
  { path: 'forget-password', component: ForgetPasswordComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  {
    path: 'survey-design',
    component: SurveyDesignComponent,
    canActivate: [authGuard],
    canDeactivate: [canDeactivateGuard],
  },
  {
    path: 'survey-management',
    component: SurveyManagementComponent,
    canActivate: [authGuard],
  },
  {
    path: 'user-management',
    component: UserManagementComponent,
    canActivate: [authGuard],
  },
  {
    path: 'user-profile',
    component: UserProfileComponent,
    canActivate: [authGuard],
    canDeactivate: [canDeactivateGuard],
  },
  { path: '**', redirectTo: '/home' },
];
